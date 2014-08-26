#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* The external interface for the recognizer.
     )
(ns voxindex.vshell.recognizer-base
  (:use
    [extensomatic.extensomatic]
    [indexterous.util.string-utils]
    [voxindex.vshell.contextualized-ids]
    )
  )

#_ (* Describes a target turned up by a lookup operation---basically, an 
      indexable and some supporting information.
      @field indexable An indexable that was recognized. 
      @field source The source of the indexable 
      @field conid-map A map of consequent contextualized grammar IDs\: if a client 
      chooses to pursue this target, this is the set of grammar IDs that should 
      be used for derivative lookup requests.
      )
(defconstructo RecognizedTarget []
  [indexable 
   source 
   externalized-conids]    
  { :new-sym new-RecognizerTarget }
  (indexable-of [this] indexable)
  (indexable-source-of [this] source)
  (conids-of [this] externalized-conids)
  )

#_ (* This hideous construction is used to incrementally record information
      about the outcome of a recognition operation.
      @p In essence, it's a collection of named fields with corresponding
      "getters" (<field name>-of methods) and "setters" (<field-name>! methods).
      The setters all return an updated version of the structure-qua-map.
      
      @p In typical practice, the recognize-complete routine allocates an 
      instance of @name , fills out as much information as it knows about, 
      passes it on to other recognition layers, which add their own 
      information\; and then the resulting information-accretion is passed
      back to the recognition's invoker as a parting gift. 
      )
(defconstructo RecognizerResult [] 
  [(^{:unsynchronized-mutable true} recognized false) 
   (^{:unsynchronized-mutable true} sub-threshold false) 
   (^{:unsynchronized-mutable true} confidence-value 0) 
   (^{:unsynchronized-mutable true} phrase nil) 
   (^{:unsynchronized-mutable true} semantics-map nil) 
   (^{:unsynchronized-mutable true} targets []) 
   (^{:unsynchronized-mutable true} p-targets nil) 
   (^{:unsynchronized-mutable true} index-name nil)
   (^{:unsynchronized-mutable true} recognizing-index-id nil) 
   (^{:unsynchronized-mutable true} command nil) 
   ]
  { :new-sym new-RecognizerResult :def-sym deftype }

  
  (recognized? [this] recognized)
  (recognized! [this tf] (set! recognized tf) this #_(assoc this :recognized tf))
  (sub-threshold? [this] sub-threshold)
  (sub-threshold! [this tf] (set! sub-threshold tf) this #_(assoc this :sub-threshold tf))
  (confidence-of [this] confidence-value)
  (confidence! [this conf] (set! confidence-value conf) this #_(assoc this :confidence-value conf))
  (phrase-of [this] phrase)
  (phrase! [this rec-phrase] (set! phrase rec-phrase) this #_(assoc this :phrase rec-phrase))
  (targets-of [this] targets)
  (target! [this indexable source conid-map] 
    (set! targets 
           (conj targets (RecognizedTarget. 
                           indexable source(externalize-conids conid-map))))
    this
    #_(assoc this :targets 
           (conj targets (RecognizedTarget. indexable source (externalize-conids conid-map)))))
  (p-targets-of [this] p-targets)
  (p-targets! [this ptargets] (set! p-targets ptargets) this #_(assoc this :p-targets ptargets))
  (index-name-of [this] index-name)
  (index-name! [this name] (set! index-name name) this #_(assoc this :index-name name))
  (command-of [this] command)
  (command! [this cmd] (set! command cmd) this #_(assoc this :command cmd))
  (recognizing-index-id-of [this] recognizing-index-id)
  (recognizing-index-id! [this id] (set! recognizing-index-id id) this #_(assoc this :recognizing-index-id id))
;   (opened-index-ids-of [this] closed-index-ids)
;   (opened-index-id! [this id] 
;     (set! (.opened-index-ids this) (conj opened-index-ids id) #_(assoc this :opened-index-ids (conj opened-index-ids id)))
;   (closed-index-ids-of [this] opened-index-ids)
;   (closed-index-id! [this id] 
;     (set! (.closed-index-ids this) (conj closed-index-ids id) #_(assoc this :closed-index-ids (conj closed-index-ids id)))
  )


(defprotocol Recognizer
  (grammar-of [this grammar-id])
  (engine-of [this] )
  (db-of [this])
  (environment-of [this])
  
  #_ (* Returns the current map of index IDs to @(link GrammarHandlerBase) :nb -derived
        objects that represent the grammars in use by the recognizer. 
        )
  (grammar-map-of [this])
  
  (confidence-threshold-of [this] )
  (confidence-threshold-set [this threshold])
  
  #_ (* Checks to see if an index (identified by its ID string) is loaded by
        the recognizer as a grammar.
        @returns @true, if the index is loaded.
        )
  (index-loaded? [this index-id])
  
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
  (using-indexes [this index-ids])
  
  #_ (* Invoked by the client to indicate that it's fer sure no longer
        using some indexes (e.g., the user closed a context), and so the
        recognizer should feel free to immediately drop the associated 
        grammars.
        )
  (done-with-indexes [this index-ids])
  
  #_ (* Initiates the heavy lifting involved with retrieving an index and 
        generating creating its grammar. 
        @arg index-ids A collection of IDs of the indexes to load.
        @returns nil
        )
  (load-indexes [this index-ids])
  
  #_ (* Removes a set of indexes from the recognizer environment.
        @arg index-ids A collection of index IDs to be unloaded.
        )
  (unload-indexes [this index-ids])
  
  #_ (* Specifies the set of grammars that must be loaded and enabled, 
        generally as a preliminary to performing a recognition operation.
        Any loaded grammar not specified as part of the set is disabled.
        @arg index-ids The collection of index IDs that are to be 
        enabled. This is coerced into a set as a preliminary to processing.
        @returns nil
        )
  (enable-indexes [this index-ids])
  
  #_ (* Loads a grammar outside of the index-based loading mechanism provided
        by @(link load-indexes). Intended primarily to facilitate loading of
        fixed grammars during recognizer startup.
        @arg grammar The grammar to unload.
        )
  (load-grammar [this grammar])
  
  #_ (* Unloads a grammar from the recognizer. This is only defined to work 
        with grammars that were initially loaded with @(l load-grammar).
        @arg grammar The grammar to unload.
        )
  (unload-grammar [this grammar])
  
  #_ (* Unloads any currently loaded grammar with the same ID as @(arg grammar), 
        then loads @(arg grammar) using @(l load-grammar).
        @arg grammar The grammar to be refreshed.
        )
  (refresh-grammar [this grammar])

  #_ (* Initiates speech recognition on a chunk of Wave-format audio data
        over a set of specified indexes. Recognition takes place asynchronously, 
        with the outcome being reported back by the specified callback.
        )
  (wave-recognize [this ext-conids wave-bytes callback])
  
;   #_ (* Called in response to a recognition, @name determines the grammar
;         object that corresponds to the ID of the recognizing grammar, and
;         passes the results to the grammar's @(c on-recognition) method
;         for grammar-speciifc processing. The @(c on-recognition) method
;         is expected to return 
;         @arg grammar-id The name of the grammar that did the recognizing.
;         By current convention, this is the ID of the index from which the
;         grammar was built.
;         @(arg result-map A map @(map result-name result-value), where
;               @arg result-name 
;               @arg result-value)   
;         )
;   (dispatch-instance [this grammar-id result-map])

   #_ (* Called as the final step after completion of a recognition operation.
         @name takes the outcome and invokes the callback specified in 
         the @(link wave-recognize) method.
         )
  (deliver-result [this result])
  
  (start [this])
  
  (terminate [this])
  
  (speech-recognized [this args])
  
  )