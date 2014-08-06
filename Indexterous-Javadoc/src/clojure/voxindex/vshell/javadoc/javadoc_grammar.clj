#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Provides support for some amount of dynamic behavior for the recognition
      process for Javadoc. 
      @p Specifically, the intent here is that whenever a client successfully 
      looks up a Java type in a Javadoc type index, we'd like, in effect, to 
      install an additional grammar in the recognizer to recognize members of
      the type. Correspondingly, should the client move on to another type, 
      the member grammar for the new type needs to be instantiated, and the 
      member grammar for the old type disabled. 
      @p Very little of this happens explicitly\; mostly it occurs as a byproduct
      of the set of index IDs returned to client after a recognition, and the
      expectation that a client will use these indexes in a subsequent lookup request.
      )
(ns voxindex.vshell.javadoc.javadoc-grammar
  (:import 
    [org.bson.types ObjectId]
    )
  (:use
    [indexterous.index.index]
    [indexterous.index.db]
    [indexterous.javadoc.javadoc]
    [extensomatic.extensomatic]
    [indexterous.util.string-utils]
    [voxindex.vshell.contextualized-ids]
    [voxindex.vshell.recognizer-base]
    [voxindex.vshell.grammar]
    )
  )

#_ (* Grammar object definition for Javadoc type indexes. 
      @p This pretty much parallels @(linki voxindex.vshell.grammar.DefaultGrammar). The only
      notable difference is that when a referent that's a @(link JavadocType)
      is encountered, the @(c on-recognition) method generates a result
      that includes the index IDs for not only the type index, but for the 
      referent's member index as well.
      )
(defconstructo JavadocTypeGrammar
  [(GrammarHandlerBase 
     grammar 
     grammar-id
     index-name
     recognizer)] []
  { :new-sym new-JavadocTypeGrammar }
  
  GrammarEventHandler
  (on-load [this]
    (println (str "JavadocTypeGrammar loaded " grammar-id " " index-name))       )
  (on-unload [this])
  
  (on-recognition [this sem-map conid-map result-thingy]
    (println (str "JavadocTypeGrammar recognized in " index-name))              
    (let [{ refs-list referents-key } sem-map ]
      (assert refs-list)
      (let [ref-ids (recover-refs refs-list)
            
            referents 
            (fetch (db-of recognizer)  
                   indexable-collection-name {"_id" {"$in" ref-ids }})
            
            result-thingy* 
            (reduce 
              (fn [result-thingy+ referent]
                (let [subids (if (and (satisfies? JavadocType referent)
                                      (members-index-ref-of referent))
                               [(members-index-ref-string referent)] 
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
  )  ;;; JavadocTypeGrammar

(defmethod make-grammar javadoc-type-handler-model [index recognizer] 
  (new-JavadocTypeGrammar (composite-grammar index) 
                          (index-id-of index) (name-of index) recognizer))


#_ (* Grammar object definition for Javadoc member indexes. 
      @p This essentially identical to @(linki voxindex.vshell.grammar.DefaultGrammar), but is slightly 
      modified to return both the type and member index IDs in the result.
      )
(defconstructo JavadocPackageGrammar
  [(GrammarHandlerBase 
     grammar 
     grammar-id
     index-name
     recognizer)] 
  []
  { :new-sym new-JavadocPackageGrammar }
  
  GrammarEventHandler
  (on-load [this])
  (on-unload [this])
  
  (on-recognition [this sem-map conid-map result-thingy]
    (let [{ refs-list referents-key } sem-map ]
      (assert refs-list)
      (let [ref-ids (recover-refs refs-list)
            
            referents 
            (fetch (db-of recognizer)  
                   indexable-collection-name {"_id" {"$in" ref-ids }})
            
            result-thingy* 
            (reduce 
              (fn [result-thingy+ referent]
                (let [subids (if (and (instance? indexterous.javadoc.javadoc.JavadocPackage referent)
                                      (types-index-ref-of referent))
                               [(types-index-ref-of referent)] 
                               nil)
                      conid-map* (edit-conids conid-map grammar-id subids)]
                  (if (satisfies? Sourced referent)
                    (let [source (fetch-source (db-of recognizer) (source-ref-of referent))]
                      (target! result-thingy+ referent source conid-map*))
                    (target! result-thingy+ referent nil conid-map*))))
              result-thingy referents)]
        (-> result-thingy* 
          (index-name! index-name)))))
  )


(defmethod make-grammar javadoc-package-handler-model [index recognizer] 
  (new-JavadocPackageGrammar (composite-grammar index) 
                            (index-id-of index) (name-of index) recognizer)
  )




