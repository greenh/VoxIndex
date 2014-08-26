#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Defines and implements support for a top-level control grammar for 
      the recognizer environment.
      @p This grammar is mostly limited to operations that manage the set
      of indexes whose grammars are being recognized by the recognizer,
      including terms to open and close recognized grammars.
      )

(ns voxindex.vshell.control-grammar
  (:use
    [voxindex.server.service-environment]
    [voxindex.server.vox-log]
    [voxindex.util.grammar-utils]
    [voxindex.vshell.contextualized-ids]
    [voxindex.vshell.grammar]
    [voxindex.vshell.grammar-html]
    [voxindex.vshell.recognizer-base]
    [indexterous.config.assigned-ids]
    [extensomatic.extensomatic]
    [indexterous.util.misc-utils]
    [indexterous.index.db]
    [indexterous.index.index]
    )
  (:import
    [system.speech.recognition 
     Choices
     Grammar
     GrammarBuilder
     SemanticResultKey
     SemanticResultValue
     ]
    )  
  )

(def target-key ":target" )
(def command-key ":command")

(def cmd-grammar {
   :open ["open" "index open"]
   :indexes ["indexes" "index index" "index of indexes"]
   :use ["use" "index use"]
   :term-index ["index" "term index" "index of terms" "active indexes"]
   :testing ["testing" "testing 1 2 3"]
   })

(def ^{:private true} log-id "Control")

(defn html-grammar [this]
  (gi-block (gi-head "Index selection")
            (gi-voc-item (gi-term "open") " " (gi-non-term "index-term"))
            (gi-voc-item (gi-term "use") " " (gi-non-term "index"))
            (gi-voc-item (gi-term "disuse") " " (gi-non-term "used-index"))
            (gi-voc-item (gi-term "index list") " or " (gi-term "indexes"))))

(def targeted-commands #{ :open :use :terms-for })
(defn targeted-command? [cmd] (get targeted-commands cmd))
(def bare-commands #{ :terms-for :indexes })
(defn bare-command? [cmd] (get bare-commands cmd))

(def control-grammar-name "<Control>") ; instance ID for control grammar

#_ (* Constructs a grammar for top-level control operations. 
      @p @name works with two lists\: @(arg root-term-map) is nominally
      a map of root-entry terms into index IDs, and is generally derived
      from the database's root entry collection\; this gives rise to a list
      of "targets" for some of the commands.
      @p Commands are derived from the @(link cmd-grammar), and can 
      either accept a target as a parameter or not.
      
      @arg root-term-map A map of terms into root-indexable IDs.
      @(returns A grammar object incorporating the commands and targets.
                A recognized phrase from the grammar returns a map with two keys\: 
                @(ul 
                   @li @(link command-key) maps to a string containing the name 
                   of the command
                   @li @(link target-key) maps to a string containing the indexable ID of 
                   the target.))
      )
(defn make-control-grammar [root-term-map] 
  (let [target-choices 
        (make-sem-res-key target-key 
          (.ToGrammarBuilder 
            (Choices. 
              (into-array  GrammarBuilder
                (map 
                  (fn [[term index-id]] (srv-string term index-id)) 
                  root-term-map)))))
        
        cmd-choices
        (make-sem-res-key command-key
          (.ToGrammarBuilder 
            (Choices.
              (into-array GrammarBuilder 
                (map 
                  (fn [[cmd phrase]]
                    (if (targeted-command? cmd)
                      (make-cv phrase cmd target-choices)
                      (make-cv phrase cmd)))
                  (expand-seqmap cmd-grammar))))))
        grammar (Grammar. cmd-choices)]
    (.setName grammar control-index-id)
    grammar))

#_ (* Comparator for qualified terms.
      @(p A qualified term is a tuple of the form @(form [term version timestamp]),
          where
          @arg term A string containing the actual term.
          @arg version A string representing the term's version.
          @arg timestamp A string containing the term's timestamp.)
      @p @name compares two qualified terms. If they both have the same term,
      @name next compares the versions, and if they're the same, it compares
      the timestamps.
      @p In a real-world scenario, we'd probably make this a parameter to
      the control grammar or recognizer or something.
      @arg a One term to compare
      @arg b The other arg to compare
      @returns -1 , 0 , or 1 if @(arg a) compares before, same as, or after
      @(arg b).
      )
(def term-comparator 
  (proxy [java.util.Comparator] [] 
    (compare [a b] 
      (let [[a-term] a
            [b-term] b
            c1 (.compareToIgnoreCase a-term b-term)]
        (if (zero? c1)
          (let [[_ a-vers] a
                [_ b-vers] b
                c2 (.compareToIgnoreCase (str a-vers) (str b-vers))]
            (if (zero? c2)
              (let [[_ _ a-ts] a
                    [_ _ b-ts] b] 
                (.compareToIgnoreCase (str a-ts) (str b-ts)))
              c2))
          c1)))))

#_ (* Generates a map between terms contained in a database's root
        entries and the indexes to which the terms correspond.
        
        @p This version operates assuming that root entries have
        versions and timestamps. It sorts (term, version, timestamp)
        tuples, and inserts the sorted results into the map. Consequently,
        the "latest" term, by version or timestamp, wins, and the 
        others are effectively discarded.

        @returns A map where each key is a term, and the corresponding 
        value is the object ID of the root-indexable designated by the root entry.
     )
(defn root-term-map [db]
  (let [qterms 
        (sort 
          term-comparator
          (reduce 
            (fn [qterms+ root-entry]
              (let [indexable-ref (root-indexable-ref-of root-entry)
                    version (version-of root-entry)
                    timestamp (timestamp-of root-entry)]
                (reduce 
                  (fn [qterms++ term]
                    (conj qterms++ [term version timestamp indexable-ref]))
                  qterms+ (root-terms-of root-entry))))
            [] (fetch-all db (root-collection db))))]
    (reduce 
      (fn [term-map+ [term _ _ indexable-ref]]
        (assoc term-map+ term indexable-ref))
      { } qterms)))

#_ (* Defines the grammar interaction handler for the control grammar.)
(defconstructo ControlGrammar 
  [(GrammarHandlerBase 
     (grammar (make-control-grammar (root-term-map (db-of recognizer)))) 
     (grammar-id control-index-id) 
     (index-name control-grammar-name) 
     recognizer)] [] 
  { :new-sym new-ControlGrammar }

  GrammarEventHandler
  (on-load [this] )
  
  (on-recognition [this sem-map conid-map result-thingy]
    (let [{ cmdstr command-key target-id target-key } sem-map
          cmd (keyword (.substring cmdstr 1))]
      (if (targeted-command? cmd)
        (condp = cmd
          ;----------------------------------------            
          :open  
          (let [root-indexable 
                (fetch-one (db-of recognizer)  
                       indexable-collection-name {"_id" (oid target-id)})
                
                index-id 
                (if (satisfies? RootIndexable root-indexable) 
                  (root-index-ref-string-of root-indexable)
                  nil)]
            (println "target ID:" target-id  " index-ID:" index-id " root indexable: " root-indexable)
            (cond 
              (not index-id)
              (do 
                (log-info log-id "No index id for root indexable " target-id)
                result-thingy)
              
              :else
              (do
                (if (index-loaded? recognizer index-id)
                  (log-info log-id "Index " index-id " is already open")
                  (do 
                    (load-indexes recognizer [index-id])
                    (log-info log-id "Index " index-id " opened")))
                (-> result-thingy
                  (target!  root-indexable 
                            (fetch-source (db-of recognizer) (source-ref-of root-indexable))
                            (edit-conids { } nil [index-id]))
                  (index-name! (root-index-name-of root-indexable))))))
          

          ;----------------------------------------            
          :use
          (let [root-indexable 
                (fetch-one (db-of recognizer)  
                       indexable-collection-name {"_id" (oid target-id)})
                index-id 
                (if (satisfies? RootIndexable root-indexable) 
                  (root-index-ref-string-of root-indexable)
                  nil)]
            (if (not index-id)
              (do 
                (log-info log-id "No index id for root indexable " target-id)
                result-thingy)
              (command! result-thingy 
                        (str "urn:indexterous.audiology-command:use:" index-id)))
            )
          
          ;----------------------------------------            
          (do
            (log-info log-id "Unrecognized targeted command: " cmd)
            result-thingy))
        ; bare commands go here            
        (condp = cmd
          :indexes 
          (command! result-thingy 
                    (str "urn:indexterous.audiology-command:index:"
                         (service-index-service-uri (environment-of recognizer))))

          ;----------------------------------------            
          :term-index
          (let [index-ids 
                (filter 
                  (fn [id] (not (preassigned-index-id? id))) 
                  (keys conid-map))] 
            (command! result-thingy 
                    (str "urn:indexterous.audiology-command:index:"
                         (service-index-service-uri (environment-of recognizer))
                         (if-not (empty? index-ids)  
                           (apply str "?index=" (interpose "," index-ids) )))))
          
          ;----------------------------------------
          :quit
          (do
            (terminate recognizer)
            result-thingy)
          ;----------------------------------------            
          (do
            (log-info log-id "Unrecognized command: " cmd)
            result-thingy)))))
  
  (on-unload [this] )
  )


