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

#_ (* Specifies that a provider is "consultable", meaning that it can be accessed 
      electronically at some specified URI.)
(defprotocol Consultable 
  #_ (* Returns the provider's URI.
       @p If the provider happens to be mapped, @name calls the supplied mapping 
       function to get the actual URI.
       @(arg map-fn A function that does URI mapping, if needed. It has the form
          @(fun [provider-key]), where
          @arg provider-key A key identifying the provider.
          @returns A string containing the URI for the provider.)
       @returns The provider's uri.
       )
  (uri-of [this map-fn])
  #_ (* Returns a URI that's extended relative to the provider's URI.
       )
  (extended-uri-of [this map-fn relative-uri])
  )

#_ (* Specifies that an provider is "locatable", that it can notionally be 
      referenced at some generally non-electronic, physical place, such 
      as a page number in a book, or an aisle in a grocery store.)
(defprotocol Locatable 
  #_ (* Returns a string describing the location of the entity.)
  (location-of [this])
  )


#_ (* Base specification for a @(i source), a coherent body of information such as a 
      document or collection of documents, the content of which contains referenceable
      points of interest described by @(l Indexable indexables).
      @p A @name includes a map between keys and locators, which are strings that
      specify the locations of content, and generally take the form of URIs.
      In general, an indexable identifies its location by specifying its source,
      a key into the source's locator map, and some additional, locator-relative
      data (e.g., relative URI path or target, or page number).
      @p The approach taken here is to not define specific meanings to locator
      values, but rather to leave that up to site- or implementation-specific
      issue. To that end, several of the methods below envision the use of a 
      mapping function to handle, e.g., URI generation.
      
      @field source-map A map of keys to locator strings.
      )
(defextenso Source [(Persistable (_id (Oid/oid))) Named Described Versioned] 
  [locator-map]
  (locator-map-of [this] locator-map)
  (raw-locator-at [this key] (get locator-map key))
  (locator-at [this key] (get locator-map key))
  )

#_ (* Specifies that an entity (such as an indexable) is contained within or is
      part of a source.
      @field source-ref A reference to the source.
      @field locator-key A key to a locator as defined by the source's locator map.
      )
(defextenso Sourced [] [source-ref locator-key]
  (source-ref-of [this] source-ref)
  (locator-key-of [this] locator-key)
  )

#_ (* Extenso for describing artifacts that are part of a source, but (like indexes)
     are not specific to a particular locator.
     )
(defextenso SourcedIn [(Sourced source-ref (locator-key nil))] []) 

#_ (* An extenso for specifying indexables that are consultable, i.e.,
     are online-accessible.
      @p Note that there's an implicit (but unchecked) requirement that the
      locator denoted by the source and locator index is of appropriate form,
      and woe betide if it ain't.
     @(field relative-uri A string containing the source-locator-relative URI.  
       @p @(b Note that, despite its name, the URI contained in @(field relative-uri) 
             :br @(u MAY) be relative to that of the source but is @(u NOT) required 
             to be so!)
       If the value is an absolute URI 
       (e.g., a URI starting with, e.g., "http://"), that URI is presumed to be 
       the complete location of the indexable, and source locator information
       is ignored.) 
      )
(defextenso ConsultablySourced [(Sourced source-ref locator-key)] [relative-uri]
  (relative-uri-of [this] relative-uri)
;  #_ (* Returns the URI for the indexable, applying an externally-specified mapping,
;       if appropriate.
;       @arg source The @name object's @(l Source) object.
;       @arg source-map )
;  (service-uri-from [this source source-map] 
;    (if (re-matches #"[\w+-.]+:.*" relative-uri)   ;; URI scheme, per RFC 3986!
;      relative-uri   ; ... it's not "relative" 
;      (extend-map-uri (locator-at source locator-key) source-map relative-uri)))
  )

#_ (* Extenso specifying indexables that are "locatable", having some physical
     manifestation in the real world. 
     @p Such an indexable might be a point on a page in a dead-tree book, for example.
     )
(defextenso LocatablySourced [(Sourced source-ref locator-key)] [relative-location]
  (relative-location-of [this] relative-location)
  Locatable 
  (location-of [this] [(location-of source) relative-location]) ;; cop-out :-)
  )

#_ (* Extenso for indexables that have "parent" indexables. 
     @p This semi-hack is currently used to identify containing relationships between
     indexables, such as a Java method is has a "parent" ("is contained in")
     a java class.
     )
(defextenso Parented [] [parents]
    (parent-ids-of [this] parents)
  )

#_ (* Extenso for indexables that denote a subindex.
     @field subindex-id The OID of the subindex.
     )
(defextenso HasSubindex [] [subindex-ref]
  (subindex-ref-of [this] subindex-ref)
  (subindex-ref-string [this] (str subindex-ref))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Base extenso for an entity that is an indexable. 
     @p Abstractly, indexables are 
      pretty much anything that can be referenced via an index, which is to
      say, just about anything that's identifable. As this obviously
      gives rise to a rather large diversity of possible kinds of indexable,
      the extenso here is, in effect, the base for indexables defined elsewhere.
      )
(defextenso Indexable [(Persistable (_id (Oid/oid)))] []
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* An extenso for a specialized form indexable that represents a top-level
     index. 
     )
(defextenso RootIndexable [Indexable] [index-ref index-name]
  (root-index-ref-of [this] index-ref)
  (root-index-ref-string-of [this] (.toString index-ref))
  (root-index-name-of [this] index-name))

#_ (* An entry in an incrementally constructed map of terms to top-level @(i indexes). 
      
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
   (Described description) (Sourced source-ref locator-key)
   (Versioned version)]
  [indexable-ref terms]
  
  (root-indexable-ref-of [this] indexable-ref)
  (root-terms-of [this] terms)
  
  java.lang.Object
  (toString [this] (str "#<RootEntry " name " " _id  ">" ))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Preferred function for identifiably creating entries.
     @p See @(l IndexBase) for details.
     @arg source-term A short readable string describing the entry.
     @arg indexable The @(l Indexable) object that the entry identifies.
     @arg terms A sequence of strings that are vocalizable terms.
     @(returns An entry, a tuple of the form @form [source-term indexable-id terms]).     
     )
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


#_ (* Preferred function for generating a collection of specs.
     @p See @(l IndexBase) for details.
     )
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

#_ ( Identifier for the default grammar handler. 
     @p See @(il voxindex.vshell.grammar.DefaultGrammar) for details.)
(def default-handler-model nil)

#_ ( Identifier for the contextual grammar handler. 
     @p See @(il voxindex.vshell.grammar.ContextualGrammar) for details.)
(def contextual-handler-model "contextual")


#_ (* Defines a base for the construction of various kinds of indexes. 
     
      @p An index, in local usage, is a collection of entries, where 
      each entry maps one or more terms onto some referent, which is described 
      by an @(link Indexable). The index as a whole, therefore, is a form of 
      map from textual or vocalizable terms into indexables.
     
     @field handler-model A string that denotes the the grammar handler type
     to be used for the grammar generated by the index.
      
     @field handler-model A string used as a designatator for a class of 
     @(il voxindex.vshell.grammar.GrammarEventHandler)
     to be used for the index. See @(il voxindex.vshell.grammar.make-grammar)
     for details.
      
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
(defextenso IndexBase [(Persistable _id) Named Described (SourcedIn source-ref)] 
  [handler-model specs]
  
  (index-id-of [this] (.toString _id))
  (specs-of [this] specs)
  (handler-model-of [this] handler-model)
  
  ) ;; IndexBase

#_ (* Extenso for indexes that support )
(defextenso ContextualIndexBase 
  [(IndexBase _id name description source-ref 
              (handler-model contextual-handler-model) specs)] 
  [] 
  ) ;; ContextualIndexBase

#_ (* Defines a minimal index, using the default grammar handler model.
     )
(defexin Index type-uri 
  [(IndexBase _id name description source-ref 
              (handler-model default-handler-model) specs)] 
  []

  java.lang.Object
  (toString [this] (str "#<Index " _id " " name ">" ))
  )

#_ (* Defines an index that uses a grammar handler that adds the entries of  
     subindexes to the recognition context.
     )
(defexin ContextualIndex type-uri 
  [(ContextualIndexBase _id name description source-ref  specs)] []

  java.lang.Object
  (toString [this] (str "#<ContextualIndex " _id " " name ">" ))
  )




