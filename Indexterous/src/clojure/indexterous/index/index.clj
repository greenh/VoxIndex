#_ ( Copyright (c) 2013 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Schema and base structure definitions for Indexterous.
      )
(ns indexterous.index.index
  (:import 
    [indexterous.index Oid]
    )
  (:use 
    [extensomatic.extensomatic]
    [indexterous.index.exin]
    [indexterous.util.string-utils]
    [indexterous.exintern.exintern-base]
    )
  )

(defn type-uri [cls] (.getName cls))

#_ (* Specifies that a class is capable of being persisted.
      @p In the current incarnation, we're using MongoDB as the 
      persistent store, so the implementation is oriented in that 
      direction.
      @field _id The @(il org.bson.types.ObjectId) object that identifes
      the object.
      )
(defextenso Persistable [] [_id]
  #_ (* Returns the object's @(il org.bson.types.ObjectId) identifier.)
  (id-of [this] _id)
  #_ (* Returns the string representation of the object's 
        @(il org.bson.types.ObjectId).)
  (id-string-of [this] (.toString _id)))

(defprotocol Identifiable 
  (relative-path-of [this])
  )

#_ (* Specified for classes of entities that have a name.
      @field name The entity's name, normally a string.
      )
(defextenso Named [] [name]
  #_ (* Returns the entity's name.)
  (name-of [this] name)
  )

#_ (* Specified for entity classes that have has a title, notionally a 
      human-readable textual identifier for an entity.
      @field title The entity's title, normally a string.)
(defextenso Titled [] [title]
  (title-of [this] title))

#_ (* Specifies that an entity class has a "super-title", textual identifier
      that provides additional indentifying information above and beyond the 
      normal title.
      @p This is used, for example, in Javadoc member descriptions, where 
      the "title" is the member's name, whereas the supertitle also includes
      the member's class's name.
      @field supertitle The entity's supertitle, normally a string.
      )
(defextenso SuperTitled [] [supertitle]
  (supertitle-of [this] supertitle)
)

(def timestamp-format (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm:ss.SSS"))

#_ (* Extendso describing entites that have versions.
      
      @field version A string representing the entity's version. 
      with the property that earlier versions
      compare before later versions when using the default string 
      comparison method.
      @field timestamp A timestamp that not only identifies creation time,
      but also serves to tiebreak identical version numbers.
      )
(defextenso Versioned [] [version (timestamp (.format timestamp-format (java.util.Date.)))]
  (version-of [this] version)
  (timestamp-of [this] timestamp))

#_ (* Specifies that an entity has a description, a human-readable textual
      description of the entity.)
(defextenso Described [] [description]
  (description-of [this] description)
  )

#_ (* Allows an entity to have an identifier that's subject to site-specific 
      interpretation.
      @p This gets used, e.g., by voxindex to identify sources that are served 
      "locally", the URLs of which are generated at the time of service.
      )
(defextenso ^:deprecated LocallyIdentified  [] [local-id]
  (local-identity-of [this] local-id)
  )

#_ (* Specifies that an entity is "consultable", meaning that it can be accessed 
      electronically at some specified URI.)
(defprotocol Consultable
  #_ (* Returns a string containing the URI at which the entitiy can notionally 
        be accessed.)
  (service-uri-of [this])
  )

#_ (* Specifies that an entity is "locatable", that it can notionally be 
      referenced at some generally non-electronic, physical place, such 
      as a page number in a book, or an aisle in a grocery store.)
(defprotocol Locatable 
  #_ (* Returns a string describing the location of the entity.)
  (location-of [this])
  )

#_ (* Base specification for a @(i source), a coherent body of information such as a 
      document or collection of documents, the content of which contains refrenceable
      points of interest described by @(link Indexable indexables). 
      )
(defextenso Source [(Persistable (_id (Oid/oid))) Named Described Versioned] []
  )

#_ (* Refined specification for @(link Source sources) that are "consultable" in the 
      sense that they are online-accessible at some URI.
      @field service-uri A URI for the source. By convention, this is a URL for
      globally accessible sources, or a URN for locally served sources.
      @filed local-id An id that can be mapped into a locally-served version of the 
      source.
      )
(defextenso ConsultableSource  
  [(Source name description version) (LocallyIdentified local-id)] 
  [service-uri]
  #_ (* Returns true if the source is locally served from an exogeneously specified
        location. Instead of a URL, the source is spec'ed as having a URN that's
        used as a locally mapped key by the server.
        )
  (locally-served? [this] (boolean (re-matches #"urn:.*" service-uri)))
  Consultable 
  (service-uri-of [this] service-uri)
  )

#_ (* Refined specification for @(link Source sources) that are "locatable" in the
      sense that they can be located by offline means. This provides the basis for 
      describing physical resources what contain indexables of interest, such 
      as books, grocery stores, storage facilities, and the like.
      )
(defextenso LocatableSource [(Source name description version)] [location]
  Locatable
  (location-of [this] location)  
  )

;(def source-map* (ref { }))

;(defn get-source-by-ref [source-ref] (get @source-map* source-ref))
;(defn add-source [source] (dosync (alter source-map* assoc (id-of source) source)))

#_ (* Specifies that an entity (such as an indexable) is contained within or is
      part of a source.
      @field source-ref A reference to the source.
      )
(defextenso Sourced [] [source-ref]
  (source-ref-of [this] source-ref)
  ; (source-of [this] (get-source-by-ref source-ref))
  )

#_ (* An extenso for specifying indexables that have a @(link ConsultableSource).
      @p @(b Note that, despite its name, the URI contained in @(field relative-uri) 
          :br @(u MAY) be relative to that of the source but is @(u NOT) required 
          to be so!) If the full URI (e.g., a URI starting with, e.g., "http://")  
      )
(defextenso ConsultablySourced [(Sourced source-ref)] [relative-uri]
  (relative-uri-of [this] relative-uri)
  (service-uri-from [this source] 
    (if (re-matches #"[\w+-.]+:.*" relative-uri)
      relative-uri   ; ... but it's not "relative" 
      (str (service-uri-of source) "/" relative-uri )))
  
  )

(defextenso LocatablySourced [(Sourced source-ref)] [relative-location]
  (relative-location-of [this] relative-location)
  Locatable 
  (location-of [this] [(location-of source) relative-location]) ;; cop-out :-)
  )

#_ (* Extenso for elements (e.g., @(link RootEntry) and @(link Index) objects) that 
      may make use of multiple sources. 
      @p This is intended as hint, rather than a committment. Primary use is identifying
      affected elements, as when a source's content is removed or replaced. 
      
      @field source-refs A single reference to a source, or a collection 
      (notionally a set) of references to source objects.
      )
(defextenso MultiSourced [] [source-refs]
  (source-refs-of [this] source-refs)
  (source-ref-set [this] (if (coll? source-refs) (set source-refs) #{ source-refs })))

#_ (* Extenso for consultable indexables that have "parent" indexes. 
      @p This is primarily in support of bottom-up context estabishment, 
      wherein given an indexable, a set of parent indexes that constitute a 
      plausible path to the indexable can be derived.
      @field parents A collection of index ID sequences. Each element 
      in the collection is either the OID of a single index, or a sequence of 
      OIDs of nested index contexts. (No, it isn't really a string.)
      )
(defextenso Parented [] [parents]
  (parent-id-string-of [this] 
    (apply str 
      (interpose "," 
        (map (fn [ids] 
               (if (coll? ids)
                 (apply str (interpose ":" ids))
                 ids))
             parents))))
  (parent-ids-of [this] 
    (let [pids (parent-id-string-of this)] 
      (if (empty? pids) nil (.split pids ","))))
  )

#_(defn string-parent-ids [parent-ids] 
  (apply str (interpose "," parent-ids)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Specifies that an entity is an indexable. Abstractly, indexables are 
      pretty much anything that can be referenced via an index, which is to
      say, just about anything that's identifable. As this obviously
      gives rise to a rather large diversity of possible kinds of indexable,
      the extenso here is, in effect, the base for indexables defined elsewhere.
      )
(defextenso Indexable [(Persistable (_id (Oid/oid)))] []
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (*
     )
(defextenso RootIndexable [Indexable] [index-ref index-name]
  (root-index-ref-of [this] index-ref)
  (root-index-ref-string-of [this] (.toString index-ref))
  (root-index-name-of [this] index-name))

#_ (* An entry in an incrementally constructed map of terms to @(i indexes). 
      
      @p The overall map is used by the VoxIndex control grammar,
      which creates the map from the collection of all known @name objects at startup,
      and uses the map to identify indexes as such, as in opening indexes for use.
      This is fundamentally different from the terms found in @(l Index) objects,
      which are complete maps and reference @(l Indexable) objects.
      
      @p A complete entry actually consists of two parts\: the @name object, and a 
      separate @(link RootIndexable) object. The control grammar's handler normally 
      uses the former of these to construct its map, and fetches the latter
      after a relevant recognition event occurs.
      )
(defexin RootEntry  type-uri
  [(Persistable  _id) (Named name) 
   (Described description) (MultiSourced source-refs)
   (Versioned version)]
  [indexable-ref terms]
  
  (root-indexable-ref-of [this] indexable-ref)
  (root-terms-of [this] terms)
  
  java.lang.Object
  (toString [this] (str "#<RootEntry " name " " _id  ">" ))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; (defexin Entry type-uri [] [source-term indexable-ref terms]
;   (source-term-of [this] source-term)
;   (indexable-ref-of [this] indexable-ref)
;   (terms-of [this] terms)
;   
;   java.lang.Object
;   (toString [this] 
;     (str "#<Entry " (enquote source-term) " " (.toString indexable-ref) ">"))
;   )

(defn new-entry [source-term indexable terms]
  [source-term (id-of indexable) (vec terms)])

(defn entry-source-term-of [[source-term indexable terms]] source-term)
(defn entry-indexable-of [[source-term indexable terms]] indexable)
(defn entry-terms-of [[source-term indexable terms]] terms)

#_ (* Preferred function for generating a spec for inclusion in an indexes. 
      Note that this generates and returns @(i one) spec\; see @(link new-specs) 
      to generate a vector of multiple specs.
      @p See @(link Index) for details on spec content.
      @arg category The spec's category string.
      @arg prefixes The spec's collection of prefix vocalization strings. May be nil.
      @arg entries The spec's collection of entries.
      )
(defn new-spec [category prefixes entries]
  [category prefixes (vec entries)])

(defn new-specs [& cats-prefs-ents]
  (loop [[category prefixes entries & more] cats-prefs-ents
         specs []]
    (if entries
      (recur more (conj specs [category prefixes (vec entries)]))
      specs)))

(defn spec-category-of [[category _ _]] category)
(defn spec-prefixes-of [[_ prefixes _]] prefixes)
(defn spec-entries-of [[_ _ entries]] entries)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Defines a base for the construction of various kinds of indexes. 
     
      @p An index, in local usage, is a collection of entries, where 
      each entry maps one or more terms onto some referent, which is described 
      by an @(link Indexable). The index as a whole, therefore, is a form of 
      map from textual or vocalizable terms into indexables.
     
     @field handler-model A string that denotes the the grammar handler type
     to be used for the grammar generated by the index.
      
     @field handler-model A designatator for the class of @(link GrammarEventHandler)
     to be used for the index.
      
     @(field specs A collection of tuples of the form 
             @(form [category prefixes entries]), where
             @arg category A string that briefly describes the spec's contents
             @arg prefixes A string or collection of strings representing prefixes 
             to be applied to the terms contained in the indexes denoted by
             @(arg entries). If the collection is empty, no prefix is applied. Note 
             the prefix sets used within any particular index should be disjoint!
             
             @(arg entries A collection of tuples representing entries, of the form
                   @(form [source-term indexable-ref term-or-terms]), where
                   
                   @arg source-term is a string representing the original 
                   unprocessed term from which vocalizations were derived, intended
                   to provide a user with a readable representation of the term.
                   
                   @arg indexable-ref The @(linki org.bson.types.ObjectId) that identifies the
                   @(link Indexable) that is the entry's referent.
                   
                   @arg term-or-terms A string, or collection of strings, that are 
                   vocalized forms of @(arg source-term). For example, a 
                   @(arg source-term) of "IEEE" might have vocalized terms
                   of "i e e e" or "i triple e".))
      )
(defextenso IndexBase 
  [(Persistable _id) Named Described
   (MultiSourced source-refs)] 
  [handler-model specs]
  
  (index-id-of [this] (.toString _id))
  (specs-of [this] specs)
  (handler-model-of [this] handler-model)
  
  ) ;; IndexBase

(defonce default-handler-model nil)

(defexin Index type-uri 
  [(IndexBase _id name description source-refs 
              (handler-model default-handler-model) specs)] 
  []

  java.lang.Object
  (toString [this] (str "#<Index " _id " " name ">" ))
  )




