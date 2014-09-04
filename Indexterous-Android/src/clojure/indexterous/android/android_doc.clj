#_ ( Copyright (c) 2011, 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Object model for Android-doc documentation. This extends the base
     index model described in @(il indexterous.index.index).
     )
(ns indexterous.android.android-doc
  (:import 
    [indexterous.index Oid]
    )
  (:use 
    [indexterous.index.index]
    [indexterous.index.exin]
    [extensomatic.extensomatic]
    [indexterous.util.string-utils]
    [indexterous.exintern.exintern-base]
    ; [indexterous.android.android-defs]
    )
  )

(defn qname-to-path [qname] (.replaceAll qname "\\." "/"))

(defonce android-type-handler-model "Android-Type")
(defonce android-package-handler-model "Android-Package")
(defonce android-member-handler-model "Android-Member")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* Represents an Android documentation tree. )
(defexin AndroidSource type-uri
  [(Source name description version locator-map)] [] 
  
  java.lang.Object 
  (toString [this] (str "#<AndroidSource " name " " (id-string-of this) ">" ))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* A form of @(link RootIndexable) representing a Android tree as a whole. 
      It contains references to an index for the tree (which might be by type
      or by package, as circumstances dictate), and the URI of tree's 
      top-level "overview-summary.html" page.
     )
(defexin AndroidRoot type-uri
  [(RootIndexable index-ref index-name) 
   (ConsultablySourced source-ref locator-key (relative-uri "reference/classes.html"))
   (Titled title)] []
  
  java.lang.Object 
  (toString [this] (str "#<AndroidRoot " title " #" (id-string-of this) ">" ))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* An indexable that describes a package in a Android tree. )
(defexin AndroidPackage type-uri
  [Indexable 
   (Named name)
   (ConsultablySourced source-ref locator-key
                       (relative-uri (str "reference/" (qname-to-path name) 
                                          "/package-summary.html"))) 
   (Parented parents)]
  [types-index-ref]
  
  (types-index-ref-of [this] types-index-ref)
  
  java.lang.Object
  (toString [this] (str "#<AndroidPackage " name " " (id-string-of this) " >"))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ (* An extenso that's the base for an @(il Indexable) that describes an Android 
     type, such as a class or interface.
     )
(defextenso AndroidType 
  [Indexable 
   (Named name)
   (ConsultablySourced source-ref locator-key relative-uri)
   (Parented parents)
   (HasSubindex subindex-ref)] 
  [package-name kind-set]
  (qname-of [this] (str package-name "." name))
;  (members-index-ref-of [this] subindex-ref)
;  (members-index-ref-string [this] (str subindex-ref))
  
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
  (toString [this] (str "#<" (.getSimpleName (type this)) " " (qname-of this)
                        " " (id-string-of this) ">"))
  )

(defexin AndroidClass type-uri 
  [(AndroidType name source-ref locator-key relative-uri parents subindex-ref package-name kind-set)] [] )

(defexin AndroidInterface type-uri 
  [(AndroidType name source-ref locator-key relative-uri parents subindex-ref package-name kind-set)] [] )

(defexin AndroidEnum type-uri 
  [(AndroidType name source-ref locator-key relative-uri parents subindex-ref package-name kind-set)] [] )

(defexin AndroidAnnotation type-uri 
  [(AndroidType name source-ref locator-key relative-uri parents subindex-ref package-name kind-set)] [] )

(defn class?? [jdtype] (= (class jdtype) AndroidClass))
(defn interface?? [jdtype] (= (class jdtype) AndroidInterface))
(defn annotation?? [jdtype] (= (class jdtype) AndroidAnnotation))
(defn enum?? [jdtype] (= (class jdtype) AndroidEnum))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn simple-parameters [parameters]
  (if parameters (map #(last (.split % "\\.")) (.split parameters ","))))

(defn simple-heading [name params] 
  (str name "(" (apply str (interpose ", " (simple-parameters params))) ")"))

#_ (* An extenso used to describe objects that have parameters. )
(defextenso Parameterized [] [parameters]
  (parameters-of [this] parameters)
  (simple-parameters-of [this] (simple-parameters parameters))
  
  )
#_ (* An extenso used to describe objects that are members of Android types.)
(defextenso AndroidMember 
  [Indexable 
   (Named name)
   (ConsultablySourced source-ref locator-key relative-uri)
   (Parented parents)] 
  [type-qname kind]
  
  (member-kind-of [this] kind)
  (type-qname-of [this] type-qname)

  SuperTitled
  (supertitle-of [this] (str type-qname " | " (title-of this)))

  java.lang.Object 
  (toString [this] (str "#<" (.getSimpleName (type this))   
                        " " (type-qname-of this) "." (title-of this)
                        " " (id-string-of this) ">"))
  )

#_ (* An @(il indexterous.index.Indexable) describing the documentation for a method.)
(defexin AndroidMethod type-uri 
  [(AndroidMember name source-ref locator-key relative-uri parents type-qname (kind "method")) 
   (Parameterized parameters)] []
  Titled
  (title-of [this] (simple-heading name parameters))
  )
 
#_ (* An @(il indexterous.index.Indexable) describing the documentation for a method.)
(defexin AndroidConstructor type-uri 
  [(AndroidMember name source-ref locator-key relative-uri parents type-qname (kind "constructor")) 
   (Parameterized parameters)] []
  Titled
  (title-of [this] (simple-heading name parameters))
  )

#_ (* An @(il indexterous.index.Indexable) describing the documentation for a field.)
(defexin AndroidField type-uri 
  [(AndroidMember name source-ref locator-key relative-uri parents type-qname (kind "field"))] []
  Titled 
  (title-of [this] name)
  )

#_ (* An @(il indexterous.index.Indexable) describing the documentation for a constant.)
(defexin AndroidConstant type-uri
  [(AndroidMember name source-ref locator-key relative-uri parents type-qname (kind "constant"))] []
  Titled 
  (title-of [this] name)
 )

#_ (* An @(il indexterous.index.Indexable) describing the documentation for an enum.)
(defexin AndroidEnumConstant type-uri
  [(AndroidMember name source-ref locator-key relative-uri parents type-qname (kind "enum-constant"))] []
  Titled 
  (title-of [this] name)
  )
#_ (* An @(il indexterous.index.Indexable) describing the documentation for an annotation element.)
(defexin AndroidAnnotationElement type-uri
  [(AndroidMember name source-ref locator-key relative-uri parents type-qname (kind "annotation-element"))] []
  Titled 
  (title-of [this] name)
  )

#_ (* An @(l indexterous.index.Indexable) describing the documentation for an attribute.)
(defexin AndroidAttribute type-uri
  [(AndroidMember name source-ref locator-key relative-uri parents type-qname (kind "attribute"))] []
  Titled 
  (title-of [this] name)
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;#_ (* An @(il indexterous.index.Index) that maps the names of types 
;     (classes, enums, interfaces, ...) to their corresponding indexables. )
;(defexin AndroidTypeIndex type-uri 
;  [(IndexBase _id name description source-refs (handler-model android-type-handler-model) specs)] 
;  []
;
;  java.lang.Object
;  (toString [this] (str "#<AndroidTypeIndex " name " " (id-string-of this)  ">" ))
;  )
;
;#_ (* An @(il indexterous.index.Index) )
;(defexin AndroidPackageIndex type-uri 
;  [(IndexBase _id name description source-refs 
;              (handler-model android-package-handler-model) specs)] []
;
;  java.lang.Object
;  (toString [this] (str "#<AndroidPackageIndex " name " " (id-string-of this) ">" ))
;  )
;
;#_ (* Defines an index of members for an Android type. 
;      @field target-set A collection of strings representing the fixed 
;      boilerplate targets (such as "public methods") found in the type. Since
;      these are well-known, there isn't any point to making indexables for them.)
;(defexin AndroidMemberIndex type-uri 
;  [(IndexBase _id name description source-refs (handler-model android-member-handler-model) specs)] 
;  [target-set]
;  
;  (target-set-of [this] target-set)
;
;  java.lang.Object
;  (toString [this] (str "#<AndroidMemberIndex " name " " (id-string-of this)  ">" ))
;  )


