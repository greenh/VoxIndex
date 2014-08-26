#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Provides the main speech rcognizer implementation.
      @p This is in essence a value-added wrapper for the ~".Net" speech recognition
      engine. Its essential function is to accept blobs of Wave-formatted voice input
      received from a client, run it through the recognizer engine, and report
      the results back to the client. 
      )
(ns voxindex.vshell.recognizer
  (:use
    [extensomatic.extensomatic]
    [indexterous.index.index]
    [indexterous.index.db]
    [voxindex.server.vox-log]
    [voxindex.vshell.recognizer-base]
    [voxindex.vshell.grammar]
    [voxindex.vshell.control-grammar]
    [voxindex.vshell.audiology-grammar]
    [voxindex.vshell.contextualized-ids]
    )
  (:import
    [voxindex.vshell EventAdapter EventHandler_ ]
    [goop Speechify SEAdapter RecognizerResultInterpreter]
    [system EventArgs EventHandler]
    [system.io MemoryStream]
    [system.speech.recognition Grammar SpeechRecognitionEngine RecognitionResult ]
    [java.io FileInputStream]
    )
  (:require 
    [voxindex.vshell.bridge-start :as bs]
    )
  )

(bs/start-bridge)

(def ^{:private true} log-id "Recognizer")

(declare make-RecognizeCompleted)
(declare make-SpeechDetected)
(declare make-SpeechRejected)
(declare make-StateChanged)
(declare make-SpeechRecognized)


#_ (* The central index-based speech recognizer thingy. 
      @p This is in essence a value-added wrapper for the ~".Net" speech recognition
      engine. Its essential function is to accept blobs of Wave-formatted voice input
      received from a client, run it through the recognizer engine, and report
      the results back to the client. However, there's a bit more to it than that.
      @p @name performs lookups over the terms derived from a set of indexes, 
      which are manifested internally in the form of grammars. 
      
      The specific set of grammars
      applicable to a specific lookup request varies from request to request.
      Consequently, @name provides services to ensure that the desired set
      of indexes is loaded and appropriately enabled for each request.
      
      
      @p In general, the assumption is that there's one @name object per
      user session, and that it
      
      @field confidence-threshold* A float, with value between 0 and 1 , that 
      determines the minimum confidence level. Not actually used any longer,
      as the confidence discrimination function has been devolved to clients.  
      
      @field loaded* A reference to a map of index ID --> grammar instances. This map
      is updated as indexes are added or removed from the recognition context.
      
      @field db An @(linki indexterous.index.db.IndexterousDB) 
      object that provides access to the underlying database.
      
      @field engine A ~".Net" @(linki system.speech.recognition.SpeechRecognitionEngine)
      object that performs the actual recognition function.
     
     )
(defconstructo RecognizerImpl [] 
  [(confidence-threshold* (ref 0.6666))
   (loaded* (ref { }))      ; map : ^String grammar-id --> grammar instance
   (result-callback* (ref nil)) ; callback
   (input-conids* (ref nil))
   environment              ; service environment
   db                       ; the database 
   engine                   ; the recognizer engine
   (adapter (SEAdapter. engine))
   ]
  { :new-sym new-RecognizerImpl }
  Recognizer
  (grammar-of [this grammar-id] (get @loaded* grammar-id))
  (engine-of [this] engine)
  (db-of [this] db)
  (environment-of [this] environment)
  
  (confidence-threshold-of [this] @confidence-threshold*)
  (confidence-threshold-set [this threshold]
    (if-not (and (float? threshold) (> threshold 0) (< threshold 1))
      (throw (IllegalArgumentException. "Threshold must be a float between 0.0 and 1.0")))
    (ref-set confidence-threshold* threshold))
  
  #_ (* Returns the current map of index IDs to @(link GrammarHandlerBase) :nb -derived
        objects that represent the grammars in use by the recognizer. 
        
        )
  (grammar-map-of [this] @loaded*)
  
  #_ (* Checks to see if an index (identified by its ID string) is loaded by
        the recognizer as a grammar.
        @returns true, if the index is loaded.
        )
  (index-loaded? [this index-id] (contains? @loaded* index-id))
  
  #_ (* Invoked by the client to indicate the set of indexes that it
        has in use. This allows the recognizer to compile and load the
        associated grammars in advance of need, or to dump grammars that
        are no longer in use.
        @p This is envisioned as being useful in recovery situations\:
        if the server is recovering, it lets the server know what the
        client is likely to want down the road 
        (although it's not strictly necessary, as enable-indexes 
        ultimately accomplishes the same function). In the case of client 
        crashes where no state is saved, it lets the server know that
        the client is restarting with a blank slate, and that any 
        historical grammars can be ditched.
        @arg index-ids The list of IDs of indexes the client expects to use.
        )
  (using-indexes [this index-ids]
    (let [[to-load to-unload]
          (reduce 
            (fn [[to-load+ to-unload+] index-id]
              (if (contains? @loaded* index-id)
                [to-load+ (disj to-unload+ index-id)]
                [(conj to-load+ index-id) to-unload+])
              [[] (keys @loaded*)]))]
      (unload-indexes this to-unload)
      (load-indexes this to-load)))
  
  #_ (* Invoked by the client to indicate that it's fer sure no longer
        using some indexes (e.g., the user closed a context), and so the
        recognizer should feel free to immediately drop the associated 
        grammars.
        )
  (done-with-indexes [this index-ids]
    (unload-indexes this index-ids))
  
  #_ (* Initiates the heavy lifting involved with retrieving an index and 
        generating creating its grammar. 
        @arg index-ids A collection of IDs of the indexes to load.
        @returns nil
        )
  (load-indexes [this index-ids]
    (let [indexes (fetch db index-collection-name 
                         { "_id" { "$in" (vec (to-oids index-ids))}})
          loaded$
          (reduce 
            (fn [loaded+ index]
              (let [grammar (make-grammar index this)
                    index-id (index-id-of index)]
                (log-info log-id "Loading " (grammar-index-name-of grammar) 
                  " = " index-id 
                  " [" (.getName (type grammar)) "]")
                (.LoadGrammar engine (grammar-grammar-of grammar))
                (let [loaded+$ (assoc loaded+ index-id grammar)]
                  (on-load grammar)
                  loaded+$)))
            @loaded* indexes)]
      (dosync 
        (ref-set loaded* loaded$)))
    nil)
  
  #_ (* Removes a set of indexes from the recognizer environment.
        @arg index-ids A collection of index IDs to be unloaded.
        )
  (unload-indexes [this index-ids]
    (let [loaded$
          (reduce 
            (fn [loaded+ index-id]
              (if-let [grammar (get loaded+ index-id)]
                (do
                  (log-info log-id "Unloading " (grammar-index-name-of grammar) " = " index-id)
                  (on-unload grammar)
                  (.UnloadGrammar engine (grammar-grammar-of grammar))
                  (dissoc loaded+ index-id))
                loaded+))
            @loaded* 
            (set index-ids))]
      (dosync 
        (ref-set loaded* loaded$)))
    nil) ; disable-indexes
  
  #_ (* Specifies the set of grammars that must be loaded and enabled, 
        generally as a preliminary to performing a recognition operation.
        Any loaded grammar not specified as part of the set is disabled.
        @arg index-ids The collection of index IDs that are to be 
        enabled. This is coerced into a set as a preliminary to processing.
        @returns nil
        )
  (enable-indexes [this index-ids]
    ; start by generating lists of grammars to disable, and indexes to load.
    ; any grammar that's on the index-ids list we enable on the spot.
    (let [[to-disable missing]  
          (reduce 
            (fn [[to-disable+ missing+] index-id]
              (if-let [grammar (get @loaded* index-id)]
                (do
                  (when-not (grammar-enabled? grammar)
                    (log-debug log-id "Enabling " (grammar-index-name-of grammar) " = " index-id)
                    (enable-grammar grammar))
                  [(disj to-disable+ index-id) missing+])
                [to-disable+ (conj missing+ index-id)])
              )
            [(set (keys @loaded*)) #{}]
            (set index-ids))
          ]
      ; now, disable everything that wasn't removed from the enabled list
      (doseq [to-dis to-disable]
        (let [grammar (get @loaded* to-dis)]
          (assert grammar)
          (log-debug log-id "Disabling " (grammar-index-name-of grammar) " = " to-dis)
          (disable-grammar grammar)))
      ; next, retrieve missing indexes and generate/load grammars
      (if-not (empty? missing)
        (load-indexes this missing)))
    nil
    ) ; enable-indexes
  
  
  (load-grammar [this grammar]
    (let [grammar-id (grammar-id-of grammar)]
      (.LoadGrammar engine (grammar-grammar-of grammar))
      (dosync (alter loaded* assoc grammar-id grammar))
      (log-info log-id "Loaded " (grammar-index-name-of grammar) " = " grammar-id )
      (on-load grammar)))
  
  (unload-grammar [this grammar]
    (let [grammar-id (grammar-id-of grammar)]
      (if (grammar-of this grammar-id) 
        (do 
          (on-unload grammar)
          (dosync (alter loaded* dissoc grammar-id grammar))
          (.UnloadGrammar engine (grammar-grammar-of grammar))
          (log-info log-id "Unloaded " (grammar-index-name-of grammar) 
                    " = " grammar-id ))
        (log-warn log-id "Not unloaded: " (grammar-index-name-of grammar) 
                    " = " grammar-id " -- not found" ))))
  
  (refresh-grammar [this grammar]
    (let [grammar-id (grammar-id-of grammar)]
      (let [old-grammar (grammar-of this grammar-id)] 
        (if old-grammar
          (unload-grammar this old-grammar))
        (load-grammar this grammar))))

  #_ (* Initiates speech recognition on a chunk of Wave-format audio data
        over a set of specified indexes. Recognition takes place asynchronously, 
        with the outcome being reported back by the specified callback.
        )
  (wave-recognize [this ext-conids wave-bytes callback]
    (log-info log-id "vshell/wave-recognize " ext-conids)
    (let [conids (internalize-conids ext-conids)]
      (enable-indexes this (keys conids))
      (let [mem-stream (MemoryStream. wave-bytes)]
        (dosync 
          (ref-set input-conids* conids)
          (if @result-callback*
            (println "!! vshell callback is not nil !!"))
          (ref-set result-callback* callback))
        (.SetInputToWaveStream engine mem-stream)
        (.RecognizeAsync engine))))
  
;   #_ (* Called in response to a recognition, @name determines the grammar
;         object that corresponds to the ID of the recognizing grammar, and
;         passes the results to the grammar's @(c on-recognition) method
;         for grammar-speciifc processing. The @(c on-recognition) method
;         is expected to return 
;         @arg grammar-id The name of the grammar that did the recognizing.
;         By current convention, this is the ID of the index from which the
;         grammar was built.
;         @(arg result-map A map @(form { result-name result-value }), where
;               @arg result-name 
;               @arg result-value)   
;         )
;   (dispatch-instance [this grammar-id result-map]
;     (let [grammar-instance (get @loaded* this grammar-id)]
;       (if grammar-instance 
;         (on-recognition grammar-instance this @input-conids* result-map)
;         (println "No grammar instance found for " grammar-id))))

  #_ (* Called as the final step after completion of a recognition operation.
        @name takes the outcome and )
  
  (deliver-result [this result]
    ; (println ":::vshell.deliver-result delivering result ")
    (let [result-callback 
          (dosync 
            (let [rc @result-callback*]
              (ref-set result-callback* nil)
              rc))]
      (if result-callback
        (result-callback result)
        ; (println ":::vshell.deliver-result skipping null callback")
        )))
  
  (start [this]
    (try 
      (load-grammar this (new-ControlGrammar this))
      (load-grammar this (new-AudiologyGrammar this))
      (.setSpeechDetected adapter (make-SpeechDetected this))
      (.setSpeechRecognized adapter (make-SpeechRecognized this))
      (.setSpeechRejected adapter (make-SpeechRejected this))
      (.setRecognizeCompleted adapter (make-RecognizeCompleted this))
      (catch Throwable t (.printStackTrace t))
      ))
  
  (terminate [this] 
    )
  
  (speech-recognized [this args]
    (try
      (let [_ (log-debug log-id "Speech recognized")
          ^RecognitionResult result (.getResult args)
          ;_ (log-info log-id (RecognizerResultInterpreter/DisplayBasicPhraseInfo "Result: "  result nil))
          _ (log-info log-id (RecognizerResultInterpreter/condensedPhraseInfo "Result: "  result nil))
          semval (.getSemantics result)
          confidence (.getConfidence result) #_ (.getConfidence semval)
          grammar-id (.getName (.getGrammar result))
          text (.getText result)
          
          ; we use Speechify/recover to acquire a sequence of interleaved keys 
          ; and values from the semantics results of the recognition process.
          ; Perfect fodder for (apply assoc ... ).
          sem-map (apply assoc (sorted-map) (Speechify/recover semval))
          _ (log-info log-id "Speech recognized: " sem-map " in " grammar-id " confidence " confidence)
          ]
        (if-let [grammar-instance (grammar-of this grammar-id)]
            (let [result-thingy 
                  (on-recognition grammar-instance sem-map @input-conids*
                                  (.. (new-RecognizerResult) 
                                      (recognized! true )
                                      (confidence! confidence)
                                      (recognizing-index-id! grammar-id)
                                      (phrase! text)))]
              (deliver-result this result-thingy)))
;         (if (>= confidence (confidence-threshold-of this))
;           ... it went here
;           (do 
;             (log-info log-id "Rejected: confidence below threshold")
;             (deliver-result this 
;                             (.. (new-RecognizerResult)
;                                 (sub-threshold! true)
;                                 (confidence! confidence)
;                                 (phrase! text)))))
        )
      (catch Throwable t
        (log-info log-id "Exception occured in recognition handler: " (.getMessage t))
        (.printStackTrace t)))                     
    )
  )  ;;; RecognizerImpl

(defn new-Recognizer [db env]
  (new-RecognizerImpl env db (SpeechRecognitionEngine.)))

(deftype RecognizeCompleted [recognizer]
  EventHandler_
  (onEvent [this arg] 
    ; (println "Recognize completed")
    (deliver-result recognizer (new-RecognizerResult))))

(defn make-RecognizeCompleted [recognizer]
  (EventAdapter. (RecognizeCompleted. recognizer))
  )

(defrecord SpeechDetected [recognizer]
  EventHandler_
  (onEvent [this arg] (log-debug log-id "Speech detected")))

(defn make-SpeechDetected [recognizer]
  (EventAdapter. (SpeechDetected. recognizer))
  )

(defrecord SpeechRejected [recognizer]
  EventHandler_
  (onEvent [this arg] 
    (log-info log-id "Speech rejected")
    (deliver-result recognizer (new-RecognizerResult))))

(defn make-SpeechRejected [recognizer]
  (EventAdapter. (SpeechRejected. recognizer))
  )

(defrecord StateChanged [recognizer]
  EventHandler_
  (onEvent [this args] 
    (let [state (.getRecognizerState args)] 
      (log-info log-id "State changed -- is now " (.toString state)))))

(defn make-StateChanged [recognizer]
  (EventAdapter. (StateChanged. recognizer))
  )

(defrecord SpeechRecognized [recognizer]
  EventHandler_
  (onEvent [this args] (speech-recognized recognizer args)))

(defn make-SpeechRecognized [recognizer]
  (EventAdapter. (SpeechRecognized. recognizer))
  )

(defn grammar-status [recognizer]
  (letfn [(msz [current content]
               (max current (count (str content))))]
    (let [gmap (grammar-map-of recognizer)
          [idsz namesz enablesz] 
          (reduce 
            (fn [[idx namex enablex] [id grammar]]
              [(msz idx id) 
               (msz namex (grammar-index-name-of grammar))
               (msz enablex (grammar-enabled? grammar))])
            [0 0 0] gmap)
          fmt (str "%-" idsz "s  %-" namesz "s  %-" enablesz "s\n")]
      (doseq [[id grammar] gmap]
        (printf fmt id (grammar-index-name-of grammar) (grammar-enabled? grammar)))
    )))


; wave-recognize [this index-ids wave-bytes callback]

(def test-dir "./test")
(defn wfn [wav] (java.io.File. test-dir (str wav ".wav")))

(defn from-file-bytes [file]
  (with-open [input (FileInputStream. file)]
    (let [bbytes (byte-array (.available input))]
      (.read input bbytes)
      (.close input)
      bbytes)))

#_ (* Simple-minded front end for testing recognizer lookup behavior
      using stored .wav file content.
      @arg recognizer The recognizer.
      @arg index-ids The set of index IDs to enable.
      @arg wave-file A @(link java.io.File File) object describing the .wav file 
      to use.
      @returns Whatever the recognizer's lookup process generates.
      )

(defn lookup [recognizer index-ids wave-file]
  (let [wave-data (from-file-bytes wave-file)
        result-promise (promise)
        ]
    (wave-recognize recognizer index-ids wave-data 
                    (fn [result] (deliver result-promise result)))
    @result-promise))

(defn lp [recognizer index-ids wave-file]
  (let [result (lookup recognizer index-ids wave-file)]
    (clojure.pprint/pprint result)
    result))







