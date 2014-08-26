#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Defines and provides some infrastructure surrounding grammars as used by the 
      voxindex recognizer.
      )
(ns voxindex.vshell.grammar
  (:use
    [indexterous.index.index]
    [indexterous.index.db]
    [indexterous.util.string-utils]
    [extensomatic.extensomatic]
    [voxindex.util.grammar-utils]
    [voxindex.vshell.contextualized-ids]
    [voxindex.vshell.recognizer-base]
    )
  (:import
    [goop Speechify ]
    [system.speech.recognition 
     Choices
     Grammar
     GrammarBuilder
     SemanticResultKey
     SemanticResultValue
     ]
    [org.bson.types ObjectId]
    )
  )

#_ (* Semantic key value used in grammars to identify values as referent ID lists.
      )
(def referents-key "referents")

#_ (* Separator between indexable (referent) IDs in the semantic value associated
      with a term in a grammer.
      )
(def term-ref-sep ";")

#_ (* Returns a collection of @(link ObjectId) objects generated from a string 
      of @(link term-ref-sep)-separated IDs. 
      @p This normally is used after recognition has occurred and the recognizer
      has returned a semantic-key tagged with @(link referents-key).
      )
(defn recover-refs [refs-list] 
  (vec (map #(ObjectId. %) (re-split term-ref-sep refs-list))))

#_ (* Constructs a map between the vocalizable phrases of a set of terms
      and their referent indexables, resulting in a map where each phrase can
      in general denote multiple referents. 

      @(arg entries A collection of tuples representing entries to be included in
            the map, of the form 
            @(form [source-term indexable-ref term-or-terms]) 
            (as defined by @(link IndexBase)), where
            
            @arg source-term is a string representing the original 
            unprocessed term from which vocalizations were derived, intended
            to provide a user with a readable representation of the term.
            
            @arg indexable-ref The @(link ObjectId) that identifies the
            @(link Indexable) that is the entry's referent.
            
            @arg term-or-terms A string, or collection of strings, that are 
            vocalized forms of @(arg source-term). For example, a 
            @(arg source-term) of "IEEE" might have vocalized terms
            of "i e e e" or "i triple e".)
      @returns A map of terms to collections of indexable IDs.
      )
(defn make-term-map [entries]
  (reduce 
    (fn [term-map+ [source-term indexable-ref term-or-terms]]
      (let [ref-id (.toString indexable-ref)
            terms (if (coll? term-or-terms) term-or-terms [term-or-terms])] 
        (reduce 
          (fn [term-map++ term]
            (if-let [refs (get term-map++ term)]
              (assoc term-map++ term (conj refs ref-id))
              (assoc term-map++ term [ref-id])))
          term-map+
          terms)))
    (sorted-map)
    entries)
  )

;   (let [term-map
;         (reduce
;           (fn [term-map+ index] 
;             (reduce 
;               (fn [term-map++ entry]
;                 (let [ref-id (id-of (content-of entry))] 
;                   (reduce 
;                     (fn [term-map+++ term]
;                       (if-let [refs (get term-map+++ term)]
;                         (assoc term-map+++ term (conj refs ref-id))
;                         (assoc term-map+++ term [ref-id])))
;                     term-map++
;                     (terms-of entry))))
;               term-map+
;               (entries-of index)))
;           (sorted-map)
;           indexes)]
;     term-map)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* Generates a @(link Grammar) object for an index.
     )
(defn grammar-for [index]
  (let [grammar-base 
        (.ToGrammarBuilder
          (Choices.
            (into-array GrammarBuilder
              (map (fn [[term refs]]
                     (srv-string term (apply str (interpose term-ref-sep refs))))
                (make-term-map [index])))))
        
        grammar (Grammar. (make-sem-res-key referents-key grammar-base))]
      (.setName grammar (id-string-of index))
      grammar))

#_ (* Generates a @(link Grammar) object that's a composite of multiple indexes,
      where the terms of each index have a "prefix" phrase prepended to them.
      
      @arg index The index to generate the grammar for.
      @returns A @(link Grammar) object.
      )
(defn composite-grammar [index]
  (let [spec-gbs
        (map   ; create a collection of GBs, one per spec
          (fn [[category prefixes entries]] 
            (let [term-map (make-term-map entries)
                                    
                  term-gb  ;; GB of term-map contents 
                  (.ToGrammarBuilder
                    (Choices.
                      (into-array GrammarBuilder
                        (map (fn [[term refs]]
                               (srv-string term (apply str (interpose term-ref-sep refs))))
                             term-map))))]
              (cond 
                (empty? prefixes) term-gb
                
                (or (not (coll? prefixes)) (= 1 (count prefixes)))
                (gb-append (GrammarBuilder.) 
                           (if (coll? prefixes) (first prefixes) prefixes)
                           term-gb)
                
                :else   ; prefixes is a collection of multiple prefixes
                (gb-append (GrammarBuilder.)
                           (Choices. 
                             (into-array 
                               GrammarBuilder 
                               (map (fn [pre] 
                                      (if (empty? pre) (GrammarBuilder.) (GrammarBuilder. pre))) 
                                    prefixes)))
                           term-gb))))
          (specs-of index))
        
        composite-gb
        (if (= 1 (count spec-gbs))
          (first spec-gbs)
          (.ToGrammarBuilder (Choices. (into-array GrammarBuilder spec-gbs))))
        
        grammar (Grammar. (make-sem-res-key referents-key composite-gb))]
    (gb-set-name grammar (id-string-of index))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* Protocol that describes the interface to entities that serve as
      event handlers for a grammar. This interface is normally implemented
      as part of a grammar-specific constructo along with the 
      @(link GrammarHandlerBase) extenso.
      )
(defprotocol GrammarEventHandler
  #_ (* Invoked by the recognizer when some target belonging to the grammar 
        has been recognized. @name has the general responsibility for taking
        a map of "semantic" results from the recognizer and doing whatever 
        processing is needed to get them into a form that allows them to be
        packaged up and returned to the client.
        
        @arg sem-map A map of semantic results extracted from the 
        recognition engine. In general, the content is dependent on what 
        the grammar specified.
        
        @arg input-conids The collection of contextualized grammar-qua-index IDs
        received with the lookup request. This collection can be returned 
        (e.g., in a @(linki voxindex.vshell.recognizer_base.RecognizedTarget)) verbatim to maintain the same context, 
        or edited as needed to modify the context.  
        
        @arg result-thingy A  @(linki voxindex.vshell.recognizer_base.RecognizerResult) object that contains
        an initial set of result information relating to the recognition process.
         
        @returns The @(linki voxindex.vshell.recognizer_base.RecognizerResult) 
        object given by @(arg result-thingy)
        updated with additional result information as appropriate to whatever 
        was reecognized. )
  (on-recognition [this sem-map input-conids result-thingy])
  
  #_ (* Invoked by the recognizer after the grammar has been loaded into the 
        recognition engine. This provides a hook for such purposes as loading
        dependent grammars.)
  (on-load [this])
  
  #_ (* Invoked by the recognizer immediately before unloading the grammar from
        the recognition engire.)
  (on-unload [this])
  
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* Extenso that defines the basic stuff a grammar needs to be useable by the 
      recognizer. Note that an object that incorporates @name must 
      also implement the @(link GrammarEventHandler) protocol!
      
      @p A @(name)-derived object is a wrapper of sorts for a grammar per se, as 
      used by the speech recognition engine.
      
      @field grammar A @(linki system.speech.recognition.Grammar) object for 
      use by the speech recognition engine.
      @field grammar-id The grammar's identifier.
      @field index-name A short string identifying the index from which the
      grammar was created.
      @field recognizer The @(link Recognizer) object that uses this grammar
      instance.
      
      )
(defextenso GrammarHandlerBase [] [grammar grammar-id index-name recognizer]
  #_ (* Returns the  )
  (grammar-grammar-of [this] grammar)
  (grammar-id-of [this] grammar-id)
  (grammar-index-name-of [this] index-name)
  (grammar-recognizer-of [this] recognizer)
  #_ (* Returns the current enabled state of @(arg grammar).)
  (grammar-enabled? [this] (.getEnabled grammar))
  (enable-grammar [this] (.setEnabled grammar true))
  (disable-grammar [this] (.setEnabled grammar false))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* Default grammar interaction handler that provides basic services in 
      support of the recognition process.
      @p The services provided by @name should be sufficient for most kinds
      of indexes, where  
      )
(defconstructo DefaultGrammar 
  [(GrammarHandlerBase grammar grammar-id index-name recognizer)] []
  { :new-sym new-DefaultGrammar }
  
  GrammarEventHandler 
  #_ (* Invoked when the recognizer has recognized a term belonging to the grammar.
        The essential function here is to 
        expand the terse semantic information returned by the recognizer into
        a full result that can be returned to the client.
        @p In the default grammar model, the recognizer returns a list of 
        IDs of @(link Indexable) objects that are referents of the recognized term.
        @name retrieves the objects corresponding to the IDs, and packages it all 
        the @(arg result-thingy) structure, which it returns.
        )
  (on-recognition [this sem-map input-conids result-thingy]
    (let [{ refs-list referents-key } sem-map ]
      (assert refs-list)
      (let [ref-ids (recover-refs refs-list)
            
            referents 
            (fetch (db-of recognizer)  
                   indexable-collection-name {"_id" {"$in" ref-ids }})
            
            result-thingy* 
            (reduce 
              (fn [result-thingy+ referent]
                (if (satisfies? Sourced referent)
                  (let [source (fetch-source (db-of recognizer) (source-ref-of referent))]
                    (target! result-thingy+ referent source input-conids))
                  (target! result-thingy+ referent nil input-conids)))
              result-thingy referents)]
        (-> result-thingy* 
          (index-name! index-name)))))
  
  (on-load [this])
  (on-unload [this])
  )

#_ (* Defines a multimethod for generating a grammar from an root entry, based
      on the grammar model requested by the root entry.
      @p This defaults to generating a grammar of the the @(link DefaultGrammar)
      class, which provides basic lookup services, and probably suffices for 
      the large majority of content types.
      @p Naturally, this leaves it incumbent on types with more exotic interaction
      behaviors (e.g., Javadoc-type grammars) to supply their own grammar 
      implementation.
      
      @arg index The @(linki indexterous.index.index.Index) object for which a grammar is to be constructed.
      @arg recognizer The @(link Recognizer) object that will be using the @name object.
      )
(defmulti make-grammar (fn [index recognizer] (handler-model-of index)) :default nil)

; (defmethod make-grammar nil [index recognizer] 
;   (new-DefaultGrammar (grammar-for index) (index-id-of index) (name-of index) recognizer))

(defmethod make-grammar nil [index recognizer] 
  (new-DefaultGrammar (composite-grammar index) 
                      (index-id-of index) (name-of index) recognizer))


#_ (* Grammar object definition for context-accreting indexes. 
      @p This pretty much parallels @(link DefaultGrammar). The only
      notable difference is that when a referent that's a @(l HasSubindex)
      derivative is encountered, the @(c on-recognition) method generates a result
      that includes the index IDs for not only the parent index, but for the 
      referent's subindex as well.
      @p Consequently, if a client does a lookup against this result, 
      the recognizer's scoope includes not only the parent but also the subindex.
      )
(defconstructo ContextualGrammar
  [(GrammarHandlerBase 
     grammar 
     grammar-id
     index-name
     recognizer)] []
  { :new-sym new-ContextualGrammar }
  
  GrammarEventHandler
  (on-load [this]
    #_(println (str "ContextualGrammar loaded " grammar-id " " index-name))       )
  (on-unload [this])
  
  (on-recognition [this sem-map conid-map result-thingy]
    #_(println (str "ContextualGrammar recognized in " index-name))              
    (let [{ refs-list referents-key } sem-map ]
      (assert refs-list)
      (let [ref-ids (recover-refs refs-list)
            
            referents 
            (fetch (db-of recognizer)  
                   indexable-collection-name {"_id" {"$in" ref-ids }})
            
            #_ (Build the result object. Note that we don't ) 
            result-thingy* 
            (reduce 
              (fn [result-thingy+ referent]
                (let [subids (if (and (satisfies? HasSubindex referent)
                                      (subindex-ref-of referent))
                               [(subindex-ref-string referent)] 
                               nil)
                      ;_ (println "conids:" conid-map )
                      ;_ (println "subids:" subids)
                      conid-map* (edit-conids conid-map grammar-id subids)]
                  (if (satisfies? Sourced referent)
                    (let [source (fetch-source (db-of recognizer) (source-ref-of referent))]
                      (target! result-thingy+ referent source conid-map*))
                    (target! result-thingy+ referent nil conid-map*))))
              result-thingy referents)]
        (-> result-thingy* 
          (index-name! index-name)))))
  )  ;;; ContextualGrammar

(defmethod make-grammar contextual-handler-model [index recognizer] 
  (new-ContextualGrammar (composite-grammar index) 
                          (index-id-of index) (name-of index) recognizer))




