#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Provides support for some amount of dynamic behavior for the recognition
      process for Android documentation. 
      @p Specifically, the intent here is that whenever a client successfully 
      looks up a Java type in a Android type index, we'd like, in effect, to 
      install an additional grammar in the recognizer to recognize members of
      the type. Correspondingly, should the client move on to another type, 
      the member grammar for the new type needs to be instantiated, and the 
      member grammar for the old type disabled. 
      @p Very little of this happens explicitly\; mostly it occurs as a byproduct
      of the set of index IDs returned to client after a recognition, and the
      expectation that a client will use these indexes in a subsequent lookup request.
      )
(ns voxindex.vshell.android.android-grammar
  (:import 
    [org.bson.types ObjectId]
    )
  (:use
    [indexterous.index.index]
    [indexterous.index.db]
    [indexterous.android.android-doc]
    [extensomatic.extensomatic]
    [indexterous.util.string-utils]
    [voxindex.vshell.contextualized-ids]
    [voxindex.vshell.recognizer-base]
    [voxindex.vshell.grammar]
    )
  )

#_ (* Grammar object definition for Android type indexes. 
      @p This pretty much parallels @(il voxindex.vshell.grammar.DefaultGrammar). The only
      notable difference is that when a referent that's a @(link AndroidType)
      is encountered, the @(c on-recognition) method generates a result
      that includes the index IDs for not only the type index, but for the 
      referent's member index as well.
      )
(defconstructo AndroidTypeGrammar
  [(GrammarHandlerBase 
     grammar 
     grammar-id
     index-name
     recognizer)] []
  { :new-sym new-AndroidTypeGrammar }
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
                (let [subids (if (and (satisfies? AndroidType referent)
                                      (members-index-ref-of referent))
                               [(members-index-ref-string referent)] 
                               nil)
                      #_ (println "conids:" conid-map )
                      #_ (println "subids:" subids)
                      conid-map* (edit-conids conid-map grammar-id subids)]
                  (if (satisfies? Sourced referent)
                    (let [source (fetch-source (db-of recognizer) (source-ref-of referent))]
                      (target! result-thingy+ referent source conid-map*))
                    (target! result-thingy+ referent nil conid-map*))))
              result-thingy referents)]
        (-> result-thingy* 
          (index-name! index-name)))))
  )  ;;; AndroidTypeGrammar

(defmethod make-grammar android-type-handler-model [index recognizer] 
  (new-AndroidTypeGrammar (composite-grammar index) 
                          (index-id-of index) (name-of index) recognizer))


#_ (* Grammar object definition for Android package indexes. 
      @p This essentially identical to @(link DefaultGrammar), but is slightly 
      modified to return both the type and member index IDs in the result.
      )
(defconstructo AndroidPackageGrammar
  [(GrammarHandlerBase 
     grammar 
     grammar-id
     index-name
     recognizer)] 
  []
  { :new-sym new-AndroidPackageGrammar }
  
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
                (let [subids (if (and (instance? indexterous.android.android_doc.AndroidPackage referent)
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

(defmethod make-grammar android-package-handler-model [index recognizer] 
  (new-AndroidPackageGrammar (composite-grammar index) 
                            (index-id-of index) (name-of index) recognizer))

#_ (* Boilerplate entries for member indexes. This doesn't include anything
      for the "detail" counterparts, as there are no targets for such, and
      they have to be dynamically computed on a per-type basis.
      )
#_(def member-targets { 
  	 "nestedclasses" ["nested classes"]
  	 "lattrs" ["attributes" "attribute summary"]
		 "inhattrs" ["inherited attributes"]
		 "constants" ["constants" "constant summary"]
		 "inhconstants" ["inherited constants"]
		 "fields" ["fields" "field summary"]
		 "inhfields" ["inherited fields"]
		 "enumconstants" ["constants"] ; yes, same term as :constants
		 "pubctors" ["public constructors" "constructors" "constructor summary"]
		 "proctors" ["protected constructors"]
		 "pubmethods" ["public methods" "methods" "method summary"]
		 "promethods" ["protected methods"]
		 "inhmethods" ["inherited methods"]
 })

#_ (defmethod make-grammar android-member-handler-model [index recognizer] 
  (let [tset (set (target-set-of index))
        
        ]
    (new-DefaultGrammar (composite-grammar index ) 
                          (index-id-of index) (name-of index) recognizer)))

