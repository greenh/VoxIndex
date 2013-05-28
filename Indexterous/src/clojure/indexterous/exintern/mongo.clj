#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Provides support for externalizing and internalizing between Clojure
      structures and the BSON-based form used by MongoDB.
      )

(ns indexterous.exintern.mongo
  (:import
    [com.mongodb Mongo DB DBCollection DBObject BasicDBList BasicDBObject]
    [org.bson.types ObjectId]
    [indexterous.exintern ConversionException]
    )
  (:use
    [indexterous.exintern.exintern-base]
    [indexterous.util.string-utils]
    [indexterous.util.misc-utils]
    [clojure.pprint]
    )
  )

(def type-key "~type!")

(def value-key "~val")
(def set-type "~set")
(def list-type "~list")
(def symbol-type "~sym")
(def keyword-type "~kwd")
(def map-type "~map")

(defn typed-object [t v] 
  (BasicDBObject. { type-key t value-key v }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn externalize-collection [exter coll]
  (let [ml (BasicDBList.)]
    (.addAll ml (map (fn [elt] (externalize-object exter elt)) coll))
    ml))


(defrecord MongoExternalizer []
  
  Externalizer
  (externalize-object [this obj]
    (cond 
      (satisfies? Externalizable obj) (externalize obj this)
      (instance? DBObject obj) obj
      (instance? ObjectId obj) obj
      (map? obj) (if (every? string? (keys obj))
                   (BasicDBObject. (remap (fn [k v] [k (externalize-object this v)]) obj))
                   (typed-object map-type (externalize-collection this obj )))
      
      (set? obj) (typed-object set-type (externalize-collection this obj))
      (sequential? obj) (externalize-collection this obj)
      
      (symbol? obj) (typed-object symbol-type (name obj))
      (keyword? obj) (typed-object keyword-type (name obj))
      
      (or (string? obj) (number? obj) (nil? obj) (false? obj) (true? obj)) obj
      
      :else
      (throw (ConversionException. 
               (str "Unable to externalize to mongo: unsupported type -- "
                                   (.getName (type obj)))))
      ))
  
  (externalize-type [this type-uri field-map]
    (BasicDBObject. (assoc field-map type-key type-uri) ))
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Internalizer for converting objects represented using BSON 
      (MongoDB's preferred external form) into Clojure/Java objects. 
      @name in effect performs a transformation that's the inverse of that
      effected by @(link MongoExternalizer).
      @p Note that there is no internal state associated with @(name), and 
      consequently the instance @(link mongo-internalizer) can be used
      wherever internalization services are needed.
      )
(defrecord MongoInternalizer []
  Internalizer
  (get-type-uri [this ext-obj] (get-value this ext-obj type-key))
  
  (get-value [this ext-obj key] (get ext-obj key))
  
  (internalize-object [this obj]
    (cond 
      (instance? com.mongodb.BasicDBObject obj)
      (let [{ type-of type-key value-of value-key } obj]
        (if type-of
          (condp = type-of
            nil (map obj)
            map-type (reduce (fn [m+ [k v]] (assoc m+ k v)) {} (internalize-object this value-of) )
            set-type (set (internalize-object this value-of))
            list-type (apply list (internalize-object this value-of))
            symbol-type (symbol (internalize-object this value-of))
            keyword-type (keyword (internalize-object this value-of))
            
            ; else...
            (type-internalize obj this)
            )
          ; no type indication, so assume it's just a map---but it will arrive
          ; as a Java map; ; so we convert to a normal Cloj map, i.e., one where 
          ; assoc will work.
          (remap (fn [k v] [k (internalize-object this v)]) obj)
          ))
      
      (instance? com.mongodb.BasicDBList obj)
      (vec (map (fn [x] (internalize-object this x)) obj))
      
      (instance? ObjectId obj) obj
      
      (or (string? obj) (number? obj) (nil? obj) (false? obj) (true? obj)) obj
      
      :else (throw (new ConversionException 
                        "Unable to internalize from mongo: unrecognized type -- "
                        (get obj type)))
      ))
  
  (get-internalized [this ext-obj key] (internalize-object this (get-value this ext-obj key)))
  
  (encoding-of [this] "MongoDB")
  
  )

(def mongo-externalizer (indexterous.exintern.mongo.MongoExternalizer.))
(def mongo-internalizer (indexterous.exintern.mongo.MongoInternalizer.))

(defn externalize-mongo [obj] (externalize-object mongo-externalizer obj))
(defn internalize-mongo [obj] (internalize-object mongo-internalizer obj))

