#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
(ns indexterous.javadoc.javadoc
  (:import 
    [indexterous.index Oid]
    )
  (:use 
    [indexterous.index.index]
    [indexterous.index.exin]
    [extensomatic.extensomatic]
    [indexterous.util.string-utils]
    [indexterous.exintern.exintern-base]
    [indexterous.javadoc.javadoc-defs]
    )
  )

(defn qname-to-path [qname] (.replaceAll qname "\\." "/"))


(defonce javadoc-type-handler-model "JD-Type")
(defonce javadoc-member-handler-model "JD-Member")
(defonce javadoc-package-handler-model "JD-Package")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defexin JavadocSource type-uri
  [(ConsultableSource name description version service-uri)] [] 
  
  java.lang.Object 
  (toString [this] (str "#<JavadocSource " name ">" ))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* A form of @(link RootIndexable) representing a Javadoc tree as a whole. 
      It contains references to an index for the tree (which might be by type
      or by package, as circumstances dictate), and the URI of tree's 
      top-level "overview-summary.html" page.
     )
(defexin JavadocRoot type-uri
  [(RootIndexable index-ref index-name) 
   (ConsultablySourced source-ref (relative-uri overview-summary))
   (Titled title)] []
  
  java.lang.Object 
  (toString [this] (str "#<JavadocTree " title " #" (id-string-of this) ">" ))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* An indexable that describes a package in a Javadoc tree. )
(defexin JavadocPackage type-uri
  [Indexable 
   (Named name)
   (ConsultablySourced source-ref  
                       (relative-uri (str (qname-to-path name) 
                                          "/package-summary.html"))) 
   (Parented parents)]
  [types-index-ref]
  
  (types-index-ref-of [this] types-index-ref)
  
  java.lang.Object
  (toString [this] (str "#<JavadocPackage " name " >"))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defextenso JavadocType 
  [Indexable 
   (Named name)
   (ConsultablySourced source-ref relative-uri)
   (Parented parents)] 
  [package-name kind-set members-index-ref]
  (qname-of [this] (str package-name "." name))
  (members-index-ref-of [this] members-index-ref)
  (members-index-ref-string [this] (str members-index-ref))
  
  (member-kind-set [inx] kind-set)
  (has-members? [this] (not (empty? kind-set)))
  (has-fields? [this] (contains? kind-set :field))
  (has-methods? [this] (contains? kind-set :method))
  (has-constants? [this] (contains? kind-set :constant))
  (has-constructors? [this] (contains? kind-set :constructor))
  (has-elements? [this] (contains? kind-set :annotation-element))
  
  Titled
  (title-of [this] (qname-of this))
  
  java.lang.Object
  (toString [this] (str "#<" (.getSimpleName (class this)) " " (qname-of this) ">"))
  )

(defexin JavadocClass type-uri 
  [(JavadocType name source-ref relative-uri parents package-name
                kind-set members-index-ref)] [] )


(defexin JavadocInterface type-uri 
  [(JavadocType name source-ref relative-uri parents package-name
                kind-set members-index-ref)] [] )


(defexin JavadocEnum type-uri 
  [(JavadocType name source-ref relative-uri parents package-name
                kind-set members-index-ref)] [] )


(defexin JavadocAnnotation type-uri 
  [(JavadocType name source-ref relative-uri parents package-name
                kind-set members-index-ref)] [] )

(defn class?? [jdtype] (= (class jdtype) JavadocClass))
(defn interface?? [jdtype] (= (class jdtype) JavadocInterface))
(defn annotation?? [jdtype] (= (class jdtype) JavadocAnnotation))
(defn enum?? [jdtype] (= (class jdtype) JavadocEnum))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn simple-parameters [parameters]
  (if parameters (map #(last (.split % "\\.")) (.split parameters ","))))

(defn simple-heading [name params] 
  (str name "(" (apply str (interpose ", " (simple-parameters params))) ")"))

(defextenso Parameterized [] [parameters]
  (parameters-of [this] parameters)
  (simple-parameters-of [this] (simple-parameters parameters))
  
  )

(defextenso JavadocMember 
  [Indexable 
   (Named name)
   (ConsultablySourced source-ref relative-uri)
   (Parented parents)] 
  [type-qname kind]
  
  (member-kind-of [this] kind)
  (type-qname-of [this] type-qname)
  
  java.lang.Object 
  (toString [this] (str "#<" (.getSimpleName (class this))   
                        " " (type-qname-of this) "." (title-of this) ">"))
  )

(defexin JavadocMethod type-uri 
  [(JavadocMember name source-ref relative-uri parents type-qname (kind "method")) 
   (Parameterized parameters)] []
  Titled
  (title-of [this] (simple-heading name parameters))
  SuperTitled
  (supertitle-of [this] (str type-qname " | " (title-of this)))
  )
  
(defexin JavadocConstructor type-uri 
  [(JavadocMember name source-ref relative-uri parents type-qname (kind "constructor")) 
   (Parameterized parameters)] []
  Titled 
  (title-of [this] (simple-heading name parameters))
  SuperTitled
  (supertitle-of [this] (str type-qname " | " (title-of this)))
  )

(defexin JavadocField type-uri 
  [(JavadocMember name source-ref relative-uri parents type-qname (kind "field")) ] []
  Titled 
  (title-of [this] name)
  SuperTitled
  (supertitle-of [this] (str type-qname " | " (title-of this)))
  )
  

(defexin JavadocConstant type-uri
  [(JavadocMember name source-ref relative-uri parents type-qname (kind "constant"))] []
  Titled 
  (title-of [this] name)
  SuperTitled
  (supertitle-of [this] (str type-qname " | " (title-of this)))
  )

(defexin JavadocAnnotationElement type-uri
  [(JavadocMember name source-ref relative-uri parents type-qname (kind "annotation-element"))] []
  Titled 
  (title-of [this] name)
  SuperTitled
  (supertitle-of [this] (str type-qname " | " (title-of this)))
  )

(defn constructor?? [member] (= (class member) JavadocConstructor))
(defn constant?? [member] (= (class member) JavadocConstant))
(defn field?? [member] (= (class member) JavadocField))
(defn method?? [member] (= (class member) JavadocMethod))
(defn annotation-element?? [member] (= (class member) JavadocAnnotationElement))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defexin JavadocTypeIndex type-uri 
  [(IndexBase _id name description source-refs (handler-model javadoc-type-handler-model) specs)] 
  []

  java.lang.Object
  (toString [this] (str "#<JavadocTypeIndex " _id " " name ">" ))
  )

; (defexin JavadocMemberIndex type-uri 
;   [(IndexBase (_id (Oid/oid)) name description source-refs 
;               (handler-model javadoc-member-handler-model) specs)] []

;   java.lang.Object
;   (toString [this] (str "#<Index " _id " " name ">" ))
;   )


(defexin JavadocPackageIndex type-uri 
  [(IndexBase _id name description source-refs 
              (handler-model javadoc-package-handler-model) specs)] []

  java.lang.Object
  (toString [this] (str "#<JavadocPackageIndex " _id " " name ">" ))
  )

