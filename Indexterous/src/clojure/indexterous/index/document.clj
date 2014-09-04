#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Defines a small object model for representing the indexable content of 
      generic online documents. 
     )
(ns indexterous.index.document
  (:import 
    [indexterous.index Oid]
    )
  (:use 
    [indexterous.index.index]
    [indexterous.index.exin]
    [extensomatic.extensomatic]
    [indexterous.util.string-utils]
    [indexterous.exintern.exintern-base]
    )
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* A @(link Source) for generic documents. 
      )
(defexin DocumentSource type-uri
  [(Source name description version locator-map)] [] 

  java.lang.Object 
  (toString [this] (str "#<DocumentSource " name ">" ))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* A form of @(link Indexable) representing a document as a whole. It contains
      references to both the index for the document, but the URI of "the document",
      notionally a first or front page or abstract or some such.
     )
(defexin DocumentRoot type-uri
  [(RootIndexable index-ref index-name ) 
   (ConsultablySourced source-ref locator-key relative-uri)
   (Titled title)] []
  
  java.lang.Object 
  (toString [this] (str "#<DocumentRoot " title " #" (id-string-of this) ">" ))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* A form of @(link Indexable) that represents a point in a document that has 
      a name or title and is 
      locatable via a URI---essentially the same concept as a bookmark
      in a browser.
      )
(defexin Bookmark type-uri 
  [Indexable (ConsultablySourced source-ref locator-key relative-uri) 
   (Titled title)
   (Parented parents)]  []
  java.lang.Object 
  (toString [this] (str "#<Bookmark " title " #" (id-string-of this) ">" ))
)

(defn bookmark-entry [source-ref locator-key parent-id entry]
  (if-not (and (vector? entry) (= 3 (count entry)))
    (throw (Exception. (str "Deformed entry: " (enquote entry)) )))
  (let [[terms source-term rel-uri] entry
        indexable (new-Bookmark source-ref locator-key rel-uri source-term parent-id)
        index-entry (new-entry source-term indexable terms)]
    [index-entry indexable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* A form of @(link Indexable) that represents a bookmark that's part of an 
      ordered sequence of bookmarks, such as one might find in a table of contents.
      @p The intent of the ordering information is to provide the wherewithal by
      which a collection of @name objects can be displayed in a sensible way.
      @field level Notionally a value indicating the "level" of the bookmark,
      e.g. chapter 1 would have level 1 , section ~"1.2.3" a level of 3 , and so on.
      @field seq-key A value the's notionally sortable to yield a useful ordering
      of collection of related @name objects.
     )
(defexin SequencedBookmark type-uri
  [Indexable 
   (ConsultablySourced source-ref locator-key relative-uri) 
   (Titled title)
   (Parented parents)] 
  [level seq-key]
  
  (level-of [this] level)
  (sequence-of [this] seq-key)
  
  java.lang.Object 
  (toString [this] (str "#<SequencedBookmark " (title-of this) 
                        " #" (id-string-of this) ">" )))

(def seq-bookmark-serial* (ref 1))

(defn seq-bookmark-entry [source-ref parent-id entry]
  (if-not (and (vector? entry) (= 5 (count entry)))
    (throw (Exception. (str "Deformed entry: " (enquote entry)) )))
  (let [[terms title rel-uri level seq-key] entry
        serial (dosync (alter seq-bookmark-serial* inc))
        indexable (new-SequencedBookmark source-ref rel-uri title parent-id level serial)
        index-entry (new-entry title indexable terms)]
    [index-entry indexable]))
