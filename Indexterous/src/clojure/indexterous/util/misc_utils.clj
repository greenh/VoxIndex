#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* A few misc. utility functions.
      )
(ns indexterous.util.misc-utils)

(defn identity-map [k v] [k v])

#_ (* Performs what is in effect a map-style operation on the entries of a map,
      resulting in a new map with entries transformed per a mapping function.
      @(arg map-fn A mapping function of the form @(fun [key val]), where\:
               @arg key A map entry's key
               @arg val A map entry's value
               @(returns @(form [k* v*]), where 
                         @arg k* the mapped key
                         @arg v* the mapped value.))
      @arg init-map An initial map to which remapped entries are @(c assoc) :nb -ed. 
      Optional\; the default value is @(form {}).
      @returns The remapped map.
     )
(defn remap 
  ([map-fn map] (remap map-fn map {}))
  
  ([map-fn map init-map]
  (reduce 
    (fn [m+ [k v]]
      (let [[k* v*] (map-fn k v)]
        (assoc m+ k* v*)))
    init-map map)))

(defn instance-of? [cls-or-proto obj]
  (cond
    (class? cls-or-proto) (instance? cls-or-proto obj)
    (map? cls-or-proto) (satisfies? cls-or-proto obj)
    :else
    (throw (IllegalArgumentException. "instance-of?: class argument is not a class or protocol"))))



#_ (* Accepts a map the values of which are collections, and returns
      a collection of @(form [key, value]) pairs.)
(defn expand-seqmap [m]
  (reduce 
    (fn [kvs+ [k vs]]
      (reduce
        (fn [kvs++ v] (list* [k v] kvs++))
        kvs+ vs))
    '() (seq m)))

