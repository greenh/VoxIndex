#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Defines a weak-minded specialization of a MongoDB database for use by
      Indexterous.
      )
(ns indexterous.index.db
  (:use 
    [extensomatic.extensomatic]
    [indexterous.util.string-utils]
    [indexterous.exintern.exintern-base]
    [indexterous.exintern.mongo]
    [indexterous.index.index]
    )
  (:import 
    [com.mongodb WriteConcern DBCollection DBObject DB Mongo]
    [org.bson.types ObjectId]
    )
  )

(def source-collection-name "sources")
(def index-collection-name "indexes")
(def root-collection-name "root_entries")
(def indexable-collection-name "indexables")



#_ (* Based on a Mongo cursor, returns a lazy sequence that sequentially
      fetches objects from the cursor and internalizes them.)
(defn internalization-seq [cursor] 
  (if-let [obj (first cursor)]
    (lazy-seq
      (cons (internalize-mongo obj) 
            (internalization-seq (next cursor))))))

#_ (* Little thingy to generate an ObjectId from a string object ID. Here
      as a convenience, to eliminate the need to import ObjectId.
      @arg object-id An object ID string.
      @returns An ObjectId object with the specified ID.)
(defn oid [object-id] (ObjectId. object-id))

#_ (* Front end object for access to a MongoDB.
      @p This is a weak-minded and non-comprehensive set of stuff for 
      making access to an Indexterous database running on MongoDB
      slightly more convenient and robust.
      )
(defconstructo IndexterousDB [] [mongo db-name (db* (ref nil)) (sources* (ref {}))]
  { :new-sym new-IndexterousDB }

  (open-db [this] (dosync (ref-set db* (.getDB mongo db-name))))
  
  (get-collection [this col-name] (.getCollection @db* col-name))
  
  
  (source-collection [this] (get-collection this source-collection-name))
  (index-collection [this] (get-collection this index-collection-name))
  (root-collection [this] (get-collection this root-collection-name))
  (indexable-collection [this] (get-collection this indexable-collection-name))
  
  
;   #_ (* Generates a map between terms contained in a database's root
;         entries and the indexes to which the terms correspond.

;         @returns A map where each key is a term, and the corresponding 
;         value is the @(link org.bson.types.ObjectId ObjectId) of the 
;         root-indexable designated by the root entry.
;      )
;   (root-term-map [this]
;     (let [entries (fetch-all this (root-collection this))
;           
;           term-map 
;           (reduce 
;             (fn [term-map+ root-entry]
;               (let [indexable-ref (root-indexable-ref-of root-entry)
;                     terms (root-terms-of root-entry)]
;                 (reduce
;                   (fn [term-map++ term] (assoc term-map++ term indexable-ref))
;                   term-map+
;                   terms)))
;             { }
;             entries)]
;       term-map))
  
  #_ (* Back end for @(link do-insert). This should not be called directly. 
        )
  (do-insert* [this collection objs]
  (let [exts (vec (map (fn [obj] (externalize-mongo obj)) objs))]
    (.insert collection exts WriteConcern/SAFE)))
  
  
  (close-db [this] (.close mongo))
  
  
  
  #_ (* Returns a MongoDB cursor descibing the results of performing the specified
        query on the the indicated collection. 
        @p Note that objects returned by the cursor @(i are not internalized).
        
        @arg collection Either a @(il com.mongodb.DBCollection) 
        object (as from @(l get-collection)), or a string that names a 
        collection.
        @arg query A map describing the objects to be fetched, ala Mongo. 
        @name externalizes the map to Mongo-form as neeeded.
        @returns The MongoDB (@il com.mongodb.DBCursor) object.
        )
  (fetch-cursor [this collection query]
    (let [col (if (instance? DBCollection collection) 
                collection 
                (get-collection this collection))
          extq (externalize-mongo query)
          cursor (.find col extq)]
      cursor))
  
  #_ (* Returns a lazy seq of internalized objects fetched from the indicated 
        collection by the specified query.
        
        @arg collection Either a @(link com.mongodb.DBCollection DBCollection) 
        object (as from @(link get-collection)), or a string that names a 
        collection.
        @arg query A map describing the objects to be fetched, ala Mongo. 
        @name externalizes the map to Mongo-form as neeeded.
        @returns A lazy sequence of internalized retrieved objects.
        )
  (fetch [this collection query]
    (internalization-seq (fetch-cursor this collection query)))
  
  (fetch-all [this collection]
    (internalization-seq (.find collection)))
  
  (fetch-one [this collection query]
    (let [col (if (instance? DBCollection collection) 
                collection 
                (get-collection this collection))
          extq (externalize-mongo query)
          obj (.findOne col extq)]
      (internalize-mongo obj)))
  
  #_ (* Fetch a source object based on its ID. 
        @p Because sources should be small in number but frequently referenced,
        we maintain a teeny cache of them, and only do an actual fetch if 
        the cache doesn't have the target in stock.)
  (fetch-source [this src-ref] 
    (let [source-ref (if (string? src-ref) (oid src-ref) src-ref)]
      (if-let [src (get @sources* source-ref)]
      src
      (let [src* (fetch-one this source-collection-name { "_id" source-ref })]
        (if src* 
          (dosync (alter sources* assoc source-ref src*)))
        src*))))
  
  (fetch-index [this index-ref]
    (let [id (if (string? index-ref) (oid index-ref) index-ref)]
      (fetch-one this index-collection-name { "_id" id})))
  
  (fetch-indexable [this ix-ref]
    (let [id (if (string? ix-ref) (oid ix-ref) ix-ref)]
      (fetch-one this indexable-collection-name { "_id" id})))
  
  (fetch-root [this root-ref]
    (let [id (if (string? root-ref) (oid root-ref) root-ref)]
      (fetch-one this root-collection-name { "_id" id})))
  
  (count-of [this collection query] 
    (let [col (if (instance? DBCollection collection) 
                collection 
                (get-collection this collection))
          extq (externalize-mongo query)]
      (.count col extq)))
  
  (remove-from [this collection query] 
    (let [col (if (instance? DBCollection collection) 
                collection 
                (get-collection this collection))
          extq (externalize-mongo query)]
      (.remove col extq)))
  
  )   ;;;; IndexterousDB

(defn new-DB
  ([db-name]
    (let [mongo (Mongo.)]
      (new-DB mongo db-name)))
  ([mongo db-name] 
    (let [db (new-IndexterousDB mongo db-name)]
      (open-db db )
      db)))

#_ (* Inserts a collection of objects into one of the database's collections.
      @p @name performs externalization on the objects, which must implement 
      the @(link Externalizable) extenso. 
      @arg collection The database collection to insert into.
      @arg stuff Indivicual objects, or collections of objects, to be inserted. 
      @returns The @(link com.mongodb.WriteResult WriteResult) returned from the
      insert operation.
     )
(defn do-insert [this collection & stuff ]
  (let [objs (mapcat (fn [obj] (if (sequential? obj) obj [obj])) stuff )] 
    (do-insert* this collection objs)))

(defn to-oids [oids] (map #(ObjectId. %) oids))

(defn remove-source [db source-name]
  (if-let [src (fetch-one db (source-collection db) { "name" source-name})]
    (let [source-ref (id-of src)]
      (remove-from db (source-collection db) { "_id" source-ref})
      (remove-from db (indexable-collection db) { "source-ref" source-ref })
      (remove-from db (index-collection db) { "source-refs" source-ref })
      (remove-from db (root-collection db) { "source-refs" source-ref }))
    (println "No source: " source-name)))

(defn count-source [db name]
  (if-let [src (fetch-one db (source-collection db) { "name" name})]
    [(count-of db (source-collection db) { "name" name})
     (count-of db (root-collection db) { "source-refs" (id-of src) })
     (count-of db (index-collection db) { "source-refs" (id-of src) })
     (count-of db (indexable-collection db) { "source-ref" (id-of src) })]))

(defn show-roots [db]
  (let [roots (fetch-all db (root-collection db))]
    (doseq [root roots]
      (println (description-of root) (id-of root) (vec (map enquote (root-terms-of root))) 
               (str(source-refs-of root)) (str (root-indexable-ref-of root)))) ))

(defn show-sources [db]
  (let [sources (fetch-all db (source-collection db))]
    (doseq [source sources]
      (println (description-of source) (name-of source) (id-of source) 
               (cond 
                 (satisfies? ConsultableSource source) (str "URI: " (service-uri-of source))
                 (satisfies? LocatableSource source) (str "Loc: " (location-of source)))))))



