#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Provides support for full-object externalization and internalization between 
      Clojure structures and JSON.
      )

(ns indexterous.exintern.json
  (:import
    [org.bson.types ObjectId]
    [indexterous.index Oid]
    [indexterous.exintern ConversionException]
    [java.io Reader File StringReader]
    [com.mongodb CommandResult WriteConcern Mongo]
    )
  (:use
    [extensomatic.extensomatic]
    [indexterous.exintern.exintern-base]
    [indexterous.index.index]
    [indexterous.index.db]
    [indexterous.util.string-utils]
    [indexterous.util.misc-utils]
    [clojure.pprint]
    )
  (:require 
    [cheshire.core :as cheshire])
  )

(def type-key "~type!")

(def value-key "~val")
(def set-type "~set")
(def list-type "~list")
(def symbol-type "~sym")
(def keyword-type "~kwd")
(def map-type "~map")
(def object-id-type "~oid")

(defn typed-object [t v] { type-key t value-key v })

#_ (* Externalizer for externalizing a body of 
      @(li indexterous.exintern.exintern_base.Externalizable)-conformant objects into 
      JSON.
      )
(defconstructo JSONExternalizer [] [(xoid-map* (ref {})) (next-xoid* (ref 0))]
  (ext-oid [this oid]
    (dosync
      (if-let [xoid (get @xoid-map* oid)]
        xoid
        (let [noid (alter next-xoid* inc)]
          (alter xoid-map* assoc oid noid)
          noid))))
  
  (externalize-collection [this coll]
    (map (fn [elt] (externalize-object this elt)) coll))
  
  Externalizer
  (externalize-object [this obj]
    (cond 
      (satisfies? Externalizable obj) (externalize obj this)
      (instance? ObjectId obj) (typed-object object-id-type (ext-oid this obj))
      (map? obj) (if (every? string? (keys obj))
                   (remap (fn [k v] [k (externalize-object this v)]) obj)
                   (typed-object map-type (externalize-collection this obj )))
      
      (set? obj) (typed-object set-type (externalize-collection this obj))
      (sequential? obj) (externalize-collection this obj)
      
      (symbol? obj) (typed-object symbol-type (name obj))
      (keyword? obj) (typed-object keyword-type (name obj))
      
      (or (string? obj) (number? obj) (nil? obj) (false? obj) (true? obj)) obj
      
      :else
      (throw (ConversionException. 
               (str "Unable to externalize to json: unsupported type -- "
                                   (.getName (type obj)))))
      ))
  
  (externalize-type [this type-uri field-map]
    (cheshire/generate-string (assoc field-map type-key type-uri) ))
  )

#_ (* Internalizer for converting objects represented using JSON 
      into Clojure/Java objects. 
      @name in effect performs a transformation that's the inverse of that
      effected by @(link JSONExternalizer).
      )
(defconstructo JSONInternalizer [] [(oid-map* (ref {}))]
  (int-oid [this xoid]
    (dosync 
      (if-let [oid (get @oid-map* xoid)]
        oid
        (let [noid (Oid/oid)]
          (alter oid-map* assoc xoid noid)
          noid))))
  
  Internalizer
  (get-type-uri [this ext-obj] (get-value this ext-obj type-key))
  
  (get-value [this ext-obj key] (get ext-obj key))
  
  (internalize-object [this obj]
    (cond 
      (map? obj)
      (let [{ type-of type-key value-of value-key } obj]
        (if type-of
          (condp = type-of
            nil (map obj)
            object-id-type (int-oid this value-of)
            map-type (reduce (fn [m+ [k v]] (assoc m+ k v)) {} (internalize-object this value-of) )
            set-type (set (internalize-object this value-of))
            list-type (apply list (internalize-object this value-of))
            symbol-type (symbol (internalize-object this value-of))
            keyword-type (keyword (internalize-object this value-of))
            
            ; else...
            (type-internalize obj this)
            )
          ; no type indication, so assume it's just a map.
          (remap (fn [k v] [k (internalize-object this v)]) obj)
          ))
      
      (coll? obj) (vec (map (fn [x] (internalize-object this x)) obj))
      
      (or (string? obj) (number? obj) (nil? obj) (false? obj) (true? obj)) obj
      
      :else (throw (new ConversionException 
                        "Unable to internalize from json: unrecognized type -- "
                        (get obj type)))
      ))
  
  (get-internalized [this ext-obj key] (internalize-object this (get-value this ext-obj key)))
  
  (encoding-of [this] "json")
  )

#_ (* Internalizes the content of a (putatively) JSON document expressed as a reader.
      @p Note that @name returns a lazy sequence, and that the reader must therefore
      not be closed until the entire content of the sequence has been consumed.
      @arg rdr A reader for the document. 
      @returns The (lazy)  sequence of objects generated by the internalization process.
      )
(defn internalize-document [doc]
  (let [inter (make-JSONInternalizer)
        rdr (clojure.java.io/reader doc)]
    (map
        (fn [ext-goop]
          (internalize-object inter ext-goop))
        (cheshire/parsed-seq rdr))))

#_(defonce mongo (Mongo.))

(def v-threshold 100) 

#_ (* Internalizes a JSON-formatted document and uses it to update a Mongo database.
      @arg mongo The @(il com.mongodb.Mongo) object for the Mongo server.
      @arg dbname The database name within the server.
      @arg content A representation of the content, a Reader or File or URL or 
      file name or whatever, as allowed by @(l clojure.java.io/reader).
      )
(defn dbload [mongo dbname source & options]
  (let [db (new-DB mongo dbname)
        opts (set options)
        v (:v opts)]
    (with-open [rdr (clojure.java.io/reader source)]
      (loop [sources []
             indexes []
             indexables []
             roots []
             [obj & objs :as o] (internalize-document rdr)]
        #_(prn '--> obj) 
        (if obj
          (cond 
            (instance? indexterous.index.index.Indexable obj)
            (if (> (count indexables) v-threshold)
              (do 
                (if v (do (print "x") (flush)))
                (do-insert db (indexable-collection db) indexables)
                (recur sources indexes [obj] roots objs))
              (recur sources indexes (conj indexables obj) roots objs))
            
            (instance? indexterous.index.index.IndexBase obj)
            (if (> (count indexables) v-threshold)
              (do 
                (do-insert db (index-collection db) indexes)
                (if v (print "i")) 
                (recur sources [obj] indexables roots objs))
              (recur sources (conj indexes obj) indexables roots objs))
            
            (instance? indexterous.index.index.Source obj)
            (recur (conj sources obj) indexes indexables roots objs)
            
            (instance? indexterous.index.index.RootEntry obj)
            (recur sources indexes indexables (conj roots obj) objs)
            
            :else 
            (throw (Exception. (str "-- Unrecognized imported object type: " (class obj))))
            )
          (do
            (if (> (count sources) 0)
              (do-insert db (source-collection db) sources))
            (if (> (count roots) 0)
              (do-insert db (root-collection db) roots))
            (if (> (count indexes) 0)
              (do-insert db (index-collection db) indexes))
            (if (> (count indexables) 0)
              (do-insert db (indexable-collection db) indexables)))
          )))))

