#_ ( Copyright (c) 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Import mechanism for a "simple" form of externalized index, wherein the index
      is represented as a hierarchy of Clojure maps.
      @p A simple-index is an externalized Clojure map, using standard Clojure syntax,
      and therefore suitable to be internalized by the Clojure reader. The map
      has the following contents\:
      @(nopt :source-name name A string that serves as the name of the source. 
         This is a short identifier that should contain no spaces. )
      @(nopt :source-title title A string that's a more descriptive, human-readable 
         title for the index.)
      @nopt :source-map source-map A map of locator keys to locator strings. Keys 
      are strictly local identifiers, and can be small integers or short strings. 
      Locator strings are generally URIs representing the base locations of accessible
      documents. Note that these may have site-specific interpretation!
      @(nopt :indexes indexes A collection of indexes, where each index is a map of the form
         @(nopt :name name A short string that identifies the index, e.g. 
            "jdk-1.7u67".)
         @(nopt :description desc A string that describes the index, e.g. 
            "Java 1.7 Update 67")
         @(nopt :root-terms terms A collection of vocalizable terms that will be used 
            vocally identify the index.)
         @(nopt :root-doc-uri root-uri The URI of a top-level document for the index. This is 
            what's normally shown to the user when the index is opened. )
         @(nopt :root-key A key for the @(arg source-map) that identifies the
            locator that sources the document. May be omitted if @(arg root-uri) is
            an absolute URI.)
         @(nopt specs A list of specs. Each spec is a named collection of entries. 
            Each 
            defines one or more categories, )
         )
      
      )
(ns indexterous.user.simple-import
  (:use
    [indexterous.index.index]
    [indexterous.index.document]
    [indexterous.index.db]
    [indexterous.util.defnk]
    [indexterous.util.string-utils]
    )
  (:import 
    [com.mongodb Mongo]
    [indexterous.index Oid]
    )
  )

(defn resolve-fn [fn-string]
  (let [[_ ns-name fn-name] (re-matches #"([^/]+)/(.*)" fn-string)]
    (if-not (and ns-name fn-name)
      (throw (Exception. (str "Can't parse function name: " (enquote fn-string)))))
    (require (symbol ns-name))
    (let [ns-obj (find-ns (symbol ns-name))
          fn-obj (ns-resolve ns-obj (symbol fn-name))]
      (if fn-obj
        fn-obj
        (throw (Exception. "Unable to find " (enquote fn-string)))))))

#_ (* Processes a list of entries from the input.
      @arg source-ref The ID of the source for the entries.
      @arg parent-id The parent index's ID string.
      @(arg entry-fn A function of the form @(fun [src-ref p-id entry]), where 
            @arg src-ref is set to @(arg source-ref)
            @arg p-id is set to @(arg parent-id)
            @arg entry is a type-dependent sequence of elements that describe
            an entry within a spec.
            @(returns 
               A tuple of the form @(form [index-entry indexable]), where
               @arg index-entry An entry to be incorporated into the nascent index
               @arg indexable An Indexable referenced by the entry)) 
      @arg indexables The list of generated indexables to date. New 
      indexables are added to this.
      @arg entry-list The list of entries to process.
      @(returns A tuple of the form @(form [indexables* entries]), where
                @arg indexables* The updated list of indexables
                @arg entries The list of generated entry tuples ))
(defn do-entries [source-ref parent-id entry-fn indexables entry-list] 
  (reduce 
    (fn [[indexables+ entries+] entry]
      (let [[index-entry indexable] (entry-fn source-ref (str parent-id) entry)]
        [(conj indexables+ indexable) (conj entries+ index-entry)]))
    [indexables '()] 
    entry-list))

#_ (* Processes a list of specs from the input.
      @arg source-ref The ID of the source for the entries.
      @arg indexables The list of generated indexables to date. New 
      indexables are added to this.
      @arg spec-list A list of specs from the input.
      @(returns A tuple of the form @(form [indexables* specs]), where
                @arg indexables* The updated list of indexables
                @arg specs The list of generated spec tuples ))
(defn do-specs [source-ref parent-id indexables spec-list]
  (reduce 
    (fn [[indexables+ specs+] spec]
      (let [{ category :category prefix-terms :prefix-terms entry-fn-name :entry-type
             entry-list :entries } spec
            entry-fn (if entry-fn-name (resolve-fn entry-fn-name) bookmark-entry)]
        (if-not category 
          (throw (Exception. "Missing \":category\" attribute in spec")))
        (if-not entry-list 
          (throw (Exception. "Missing \":entries\" attribute in spec")))
        (let [[indexables+* entries] (do-entries source-ref parent-id entry-fn indexables+ entry-list)]
          [indexables+* (conj specs+ (new-spec category prefix-terms entries))])))
    [indexables '()] 
    spec-list))

#_ (* Processes a list of indexes from the input.
      @arg version A version indicator (string or vector of integers).
      @arg source-ref The ID of the source for the indexes.
      @arg indexables The list of generated indexables to date. New 
      indexables are added to this.
      @(returns 
         A tuple of the form @(form [indexables indexes root-entries]), where
         @arg indexables The updated list of indexables
         @arg indexes The list of generated index objects
         @arg root-entries A list of generated root entry objects))
(defn do-indexes [version source-ref index-list]
  (reduce 
    (fn [[indexables+ indexes+ root-entries+] index-item] 
      (let [{root-terms :root-terms root-doc :root-doc
             locator :locator
             index-name :name index-desc :description
             spec-list :specs} index-item
            ]
        (if-not (and root-terms root-doc index-name index-desc spec-list locator) 
          (throw (Exception. "Missing field: index must have :name :desc :specs :root-terms :root-doc :locator")))
        (let [iid (Oid/oid)
              [indexables+* specs] (do-specs source-ref iid indexables+ spec-list)
            index (new-Index iid index-name index-desc source-ref specs)
            root-indexable (new-DocumentRoot (id-of index) index-name source-ref (str locator)
                                            root-doc index-desc)
            root-entry (new-RootEntry (Oid/oid) index-name index-desc
                                    source-ref nil version
                                    (id-of root-indexable) root-terms )]
          [(conj indexables+* root-indexable) 
           (conj indexes+ index)
           (conj root-entries+ root-entry)])))
    ['() '() '()]
    index-list))

#_ (* Imports a "simple" index.
      @arg file-name A string identifying the source, either a local file name or
      a URL.
      @arg dbname The name of the database to use.
      @(arg opts A map of options and values.
         @opt :nodb torf If true, suppresses actual database update, i.e. just does 
         parses the input.
         )
      )
(defn simport [file-name dbname opts]
  (let [{ :keys [nodb] } opts
        contents (slurp file-name)
        innards (read-string contents)
        { source-name :source-name source-title :source-title 
         source-type :source-type index-list :indexes version :source-version
         locators :locators
         } innards
        ]
    (if-not source-name 
      (throw (Exception. "Missing \":source-name\" attribute")))
    (if-not source-title 
      (throw (Exception. "Missing \":source-title\" attribute")))
    (if-not index-list
      (throw (Exception. "Missing \":indexes\" attribute")))
    (if-not (or locators (vector? locators) (empty? locators))
      (throw (Exception. ":locators missing, or not a non-empty vector")))
    (let [source (new-DocumentSource source-name source-title version 
                   (zipmap (map str (range 0 (count locators))) locators))
          source-ref (id-of source)
          [indexables indexes root-entries] (do-indexes version source-ref index-list) 
          ]
      (if-not nodb
        (with-open [mongo (Mongo.)] 
          (try 
            (let [db (new-DB mongo dbname)] 
              (print "Updating database...")
              (do-insert db (source-collection db) source)
              (do-insert db (index-collection db) indexes)
              (do-insert db (indexable-collection db) indexables)
              (do-insert db (root-collection db) root-entries)
              (println "Done."))
            (catch Exception e 
              (printf "Exception during DB update -- %s\n" (.getMessage e))
              (.printStackTrace e)))))))
  )  ;; simport

(defnk simple-import [file-name :nodb false :dbname "Myself" ]
  (simport file-name { :nodb nodb :dbname dbname } ))

;(defopts simple-opts 
;  (boolean-opt nodb
;    "Suppresses database update; only reads and parses the source. [false]")
;  (string-opt dbname 
;    "Specifies the internal database name [Myself]")
;  )
;
;(defn simple-import-cli [args]
;  (let [[{:keys [nodb dbname local-id] :as opts} source]])
;  
;  
;  
;  )
