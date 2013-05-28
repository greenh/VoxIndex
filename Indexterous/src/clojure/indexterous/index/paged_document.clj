#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Provides the schema for representing indexable information from paged documents,
      such as physical books and PDF files.
      @p Be aware that there's no back-end support for this yet! 
      )
(ns indexterous.index.paged-document
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

#_ (* Base for sources representing paged documents, such as physical books and 
      PDF files. 
      @p The general presumption for such entities is that references 
      within the document are in terms of page numbers. This fundamentally sets
      paged documents apart from hypertext documents, where references are 
      presumed to be inline targets.
      )
(defextenso PagedDocument [(Source name description version) (Titled title)] [])

#_ (* Protocol intended to be implemented by PagedDocument sources, to return
      URIs for source-related artifacts. Note that such URIs might refrence
      locations in consultable documents, or might merely allow information 
      @(i about) the document to be retrieved.
      )
(defprotocol PagedDocumentURI
  #_ (* Generates a URI for a paged document 
        based on the source and an indexable belonging to the source.)  
  (indexable-service-uri-of [source indexable])
  )

#_ (* Base extenso for a paged document source, such as a physical book, that is
      not accessible online. In such a case, the document contant can't 
      be displayed, but it is possible to look up and display page numbers 
      corresponding to entries. 
      )
(defextenso NonconsultablePagedDocument
  [(PagedDocument name description version title)] [])

#_ (* A NonconsultablePagedDocument that occurs in a single volume or file. 
      )
(defexin SimpleNonconsultablePagedDocument type-uri
  [(NonconsultablePagedDocument name description version title)] 
  [service-uri]
  
  PagedDocumentURI
  (indexable-service-uri-of [this indexable] 
     (str service-uri "?id=" (id-string-of indexable)))

  java.lang.Object 
  (toString [this] (str "#<SimplePagedDocument \"" name "\" #" (id-string-of this) ">" ))
  )

#_ (* Placeholder for a compound (e.g., multivolume, multifile) nonconsultable 
      document.
      )
(defexin CompoundNonconsultablePagedDocument type-uri
  [(NonconsultablePagedDocument name description version title)] [])


#_ (* Represents a paged document that is online-accessible and processable by 
      the client, such as a PDF file.)
(defexin ConsultablePagedDocument  type-uri
  [(PagedDocument name description version title)]
  [page-offset service-uri]
  
  Consultable
  (service-uri-of [this] service-uri)
  
  PagedDocumentURI
  (indexable-service-uri-of [this indexable])
  
  java.lang.Object 
  (toString [this] (str "#<ConsultablePagedDocument \"" name "\" #" (id-string-of this) ">" ))
  )



#_ (* An indexable that notionally represents an entry in a paged book's index. 
      @p As with real-world index entries, a single referent can appear on a
      multiple pages, or page ranges. Moreover, the role in which a referent 
      appears on specific pages can vary\: a referent might, for example, might  
      appear on a number of pages, but be defined on one specific distinguished 
      page.
      
;      @(field page-map A map containing information about different classes of
;              pages. Standard keys include\: 
;              @key :refs A list strings containing the numbers of pages that 
;              reference the term.
;              @key :defined A single string containing the page where the term
;              is defined.) 
      )
(defexin PagedIndexable type-uri 
  [Indexable (Sourced source-ref) (Titled title)]  
  [page-map]
  
  (page-map-of [this] page-map)
  
  (referencing-pages-of [this] (:refs page-map))
  (definintion-page-of [this] (:defined page-map))
  (discussion-pages-of [this] (:discuss page-map))
  
  ConsultablySourced
  (relative-uri-of [this])
  (service-uri-from [this source] (indexable-service-uri-of source this))
  
  java.lang.Object 
  (toString [this] (str "#<PageIndexable \"" title "\" #" (id-string-of this) ">" ))
)

#_(defexin ReferenceAuthor)