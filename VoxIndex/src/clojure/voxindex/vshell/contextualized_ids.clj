#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Defines a set of functions for manipulating contextualized IDs singly 
      and collectively.
      
      @p The problem dealt with here is... strange. In the current scheme
      of things, a VoxIndex client receives a collection of index-qua-grammar
      IDs each time the client does a successful lookup. These IDs specify
      a set of grammars (corresponding to indexes) that a define a context
      for subsequent lookup operations. 
      
      @p Each subsequent lookup will in general change the context, or, more 
      likely, parts of the context. To know which parts to change and which 
      to keep requires ancillary information --- notably, which grammars added 
      specific parts of the context. This gives rise to the notion of a 
      contextualized ID, or con-id for short.
      
      @p A con-id has is a tuple @(form [grammar-id owner-ids]), where
      the @(arg grammar-id) is the ID of a grammar that's part of the 
      context, and @(arg owner-ids) is a collection --- often empty ---
      of IDs of grammars that "own", or were responsible for installing 
      that grammar into the context, and are thus allowed to remove it
      from the context.
      
      @p The situation is complicated a little by the need to maintain both
      an internal form (a tuple, consisting of a string and a set, described 
      above) and an external form, which represents each con-id as a single 
      string.
      )
(ns voxindex.vshell.contextualized-ids
  (:use 
    [indexterous.util.string-utils]
    )
  )

#_ (* Separator between elements in the string representation of a 
      contextualized ID, which has the form "grammar-id { \":\" owner-id }*".  
      )
(def conid-sep ":")

#_ (* Takes a string representing the external form of a contextualized 
      index-qua-grammar ID and turns it into its internal form.
      @(arg id-string A string containing the external form of the ID.)
      @(returns A contextualized ID in the form of a tuple 
                @(form [grammar-id owner-ids]))
      )
(defn internalize-conid [id-string]
  (let [ids (re-split ":" id-string)]
    [(first ids) (if-let [owners (next ids)] (set owners) nil)]))

#_ (* Takes a collection of externalized contextualized IDs and returns a map 
      with the grammar IDs as keys, and sets of context IDs as values.)
(defn internalize-conids [conids]
  (reduce 
    (fn [conid-map+ id-string]
      (let [[grammar-id owner-ids] (internalize-conid id-string)] 
        (assoc conid-map+ grammar-id owner-ids)))
    { } conids))

#_ (* Takes an internal representation of a contextualized index-qua-grammar ID and
      generates an externalized string representation suitable for transmission to a
      client, and subsequent recovery by @(link internalize-conids).
      
      @(arg id-blob A tuple of the form @(form [grammar-id owner-ids]), where
            @arg grammar-id The ID of a index-qua-grammar 
            @arg owner-ids A collection of grammar-qua-index IDs that are in 
            the context of the grammar represented by  @(arg grammar-id).
            )
      @returns A string containing the externalized form of @(arg contextualized-id).
      )
(defn externalize-conid [contextualized-id]
  (let [[grammar-id owner-ids] contextualized-id]
    (apply str grammar-id (interleave (repeat conid-sep) owner-ids))))

#_ (* Takes a collection of internalized con-ids and generates an array of 
      strings as an externalized form.)
(defn externalize-conids [conid-map]
  (let [ext-ids
        (map
          (fn [[grammar-id owner-id-set]]
            (apply str grammar-id (interleave (repeat conid-sep) owner-id-set)))
          conid-map)]
    (into-array String ext-ids)))

(defn make-conid [grammar-id owner-ids] 
  [grammar-id (if owner-ids (set owner-ids) nil)])


(defn con-id-grammar [con-id] (first con-id))
(defn con-id-contexts [con-id] (second con-id))

#_ (* Performs the basic context-editing function on a con-id map, wherein
      each con-id with a owner of @(arg owner-id) is removed from the map, 
      and each id in @(arg new-ids) is added with @(arg owner-id) as its 
      owner.
      )
(defn edit-conids [con-id-map owner-id new-ids]
  (let [cleaned-id-map 
        (reduce 
          (fn [cleaned-id-map+ [grammar-id owner-ids]]
            (if (contains? owner-ids owner-id)
              cleaned-id-map+
              (assoc cleaned-id-map+ grammar-id owner-ids)))
          { }
          con-id-map)
        
        #_ (println "clean: " cleaned-id-map owner-id new-ids)
        
        own-owner-ids 
        (if owner-id
          (let [own-con (get cleaned-id-map owner-id)]
            (if (empty? own-con) (set [owner-id]) (conj own-con owner-id)))
          nil)
        
        new-id-map 
        (reduce 
          (fn [new-id-map+ new-id]
            (assoc new-id-map+ new-id own-owner-ids))
          cleaned-id-map 
          new-ids)
        ]
    new-id-map))



