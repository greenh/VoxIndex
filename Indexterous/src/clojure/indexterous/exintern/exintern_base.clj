#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Provides an infrastructure to support transformation of objects between 
      their native Clojure forms and external forms, possibly using such
      technologies as JSON, MongoDB/BSON, and XML. 
      @p A particular goal is to 
      allow each participating object type to describe what information it
      needs to externalize, and how to reconsitute the object during internalization,
      just once, in a representation-independent way. This allows a 
      variety of representations to be applied as needed, with no further 
      effort on the object definer's part.)
(ns indexterous.exintern.exintern-base
  (:import
    [indexterous.exintern ConversionException]
    )
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Protocol implemented by externalization technologies.
      )
(defprotocol Externalizer
  (externalize-object [this obj])
  (externalize-type [this type-uri field-map])
  )

#_ (* Protocol implemented by objects that intend to be externalizable.
      )
(defprotocol Externalizable 
  #_ (* Generates an externalized representation of the object
        using the supplied @(link Externalizer).  
        )
  (externalize [this ^Externalizer exter])
  
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Externalizes an externalizable Clojure object's content, recording 
      the object's type and the value of specified fields. Note that this is
      intended @(b only) for use within the implementation of the 
      @(link Externalizable/externalize externalize) method 
      of an object implementing the @(link Externalizable) protocol.
      
;      @(session
;         (defrecord Foo [aa bb cc]
;           Externalizable
;           (externalize [this externalizer] 
;             (externalize-fields externalizer "http://example.com/index#Foo" aa bb cc)))
;         ~"user.Pooo" 
;         (def foo (Foo. 1 "abc" [3 2 1 ]))
;         ~"#'user/foo"
;         foo
;         ~"#:user.Foo{:aa 1, :bb \"abc\", :cc true}"
;         (externalize-object foo (indexterous.exintern.mongodb.MongoExternalizer.))
;         ~"#<BasicDBObject { \"cc\" : [ 3 , 2 , 1] , \"bb\" : \"abc\" , \"aa\" : 1 , \"~type!\" : \"http://example.com/index#Foo\"}>")
      @p Note the addition of the "~type!" field as a way of recording the object's
      type.
      
      @arg externalizer The @(link Externalizable) object for the externalization technology
      being applied.
      @arg type-uri The class of object being externalized.
      @arg fields Names of the fields to be externalized.
      @returns An externalizer-specific object describing the externalized object.
      )
(defmacro externalize-fields [exter type-uri & fields]
  (let [dingbat-map 
        (reduce 
          (fn [m+ field] 
            (assoc m+ (str field) `(externalize-object ~exter ~field)))
          { }
          fields)]
    `(externalize-type ~exter ~type-uri ~dingbat-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Interface for a class of objects that provide internalization services.
      By design, @(name) objects interact with the @(link type-internalize) multimethod
      to allow internalization of arbitrary types.
      )
(defprotocol Internalizer
  #_ (* Returns the type URI of the indicated externalized object. 
        @arg ext-obj An externalized object.
        @returns The type URI of the externalized object if it has one, 
        or nil if there is none.
        )
  (get-type-uri [this ext-obj] )
  
  #_ (* Fetches the externalized form associated with a specific key from the   )
  (get-value [this ext-obj ^String key])
  
  #_ (* Internalizes an externalized object.)
  (internalize-object [this ext-obj])
  
  #_ (* A convenience method that returns 
        @(c (internalize-object this (get-value ext-obj key))).
        )
  (get-internalized [this ext-obj key])
  
  #_ (* Returns a string describing the encoding technology of the 
        externalized object, e.g. "JSON" or "XML".)
  (encoding-of [this])
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Multimethod for internalizing objects from their external representation
      using an internalizer. @(name) keys on the basis of a type URI 
      extracted from the object by the internalizer.
      
      @arg extobj An externalized representation of an object.
      @arg internalizer An @(link Internalizer) object appropriate to the 
      technology used to encode @(arg extobj). 
     )
(defmulti type-internalize (fn [extobj internalizer] 
                             (get-type-uri internalizer extobj)) :default nil)

#_ (defmethod type-internalize nil [extobj internalizer] 
  (throw (ConversionException. 
           (str "Unable to internalize from " (encoding-of internalizer) 
                ": unrecognized type uri -- "
                (get-type-uri internalizer extobj)))))

#_ (* Recursion-in-progress flag for type-internalize. )
(def ^:dynamic *type-internalizer-wrap* #{})

(defn- throw-conversion-exception [extobj internalizer]
  (throw (ConversionException. 
           (str "Unable to internalize from " (encoding-of internalizer) 
                ": unrecognized type uri -- "
                (get-type-uri internalizer extobj)))))

#_ (* The nil variant of @name is invoked as a default case, i.e., where 
      no internalization method was found for an externalized object's 
      type. The response here is to extract a package name from the type,
      try to load it, and then retry the conversion... taking care that we 
      don't recurse more than once.
      @p This versions should handle the case where there are objects
      of unknown type embedded within objects of unknown type. 
      )
(defmethod type-internalize nil [extobj internalizer] 
  (let [tname (get-type-uri internalizer extobj)
        lx (if tname (.lastIndexOf tname ".") -1)
        pname (if (>= lx 0)  (symbol (subs tname 0 lx)))]
    (if (= *type-internalizer-wrap* pname)
      (throw-conversion-exception extobj internalizer)
      (try 
        (if pname
          (do
            ; (println "Trying to load " pname)
            (require pname)
            (binding [*type-internalizer-wrap* pname]
              (type-internalize extobj internalizer))))
      (catch java.lang.ClassNotFoundException e
        (throw-conversion-exception extobj internalizer))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_ (* Internalizes a Clojure object from an externalized representation. This is 
      principally intended for use within object-specific @(link internalize)
      multimethod implementations, but may have application elsewhere.
      @p @(name) constructs a small environment containing variables with the names
      given in @(arg fields), and embeds @(arg proc-form) to execute within the 
      environment. @(arg proc-form) is nominally intended as an expression for 
      generating a new object.
;      @p Continuing the example started in @(link externalize-fields)\:
;      @(session
;         (defmethod type-internalize "http://example.com/index#Foo" [intobj inter]
;           (internalize-fields intobj (Foo. aa bb cc) aa bb cc))
;         ~"#<MultiFn clojure.lang.MultiFn@7c5e87>"
;         foo-data
;         ~"#<BasicDBObject { \"cc\" : [ 3 , 2 , 1] , \"bb\" : \"abc\" , \"aa\" : 1 , \"~type!\" : \"http://example.com/index#Foo\"}>"
;         (internalize-object (indexterous.exintern.mongodb.MongoInternalizer.) foo-data)
;         ~"#:user.Foo{:aa 1, :bb "abc", :cc [3 2 1]}"
;         
;         )
      @arg extobj An object containing data to be internalized.
      @arg proc-form A form to be evaluated in the environment established by the macro,
      as described above.
      @arg fields The names of fields to be extracted from the @(arg ext).
      @returns Whatever is returned from the evaluation of @(arg proc-form).
      )
(defmacro internalize-fields [inter extobj proc-form & fields]
  (let [;; construct a vector of assignments of forms 
        ;;      field-name (get-internalized ext "field-name")
        ;; for insertion into a 'let' form.
        fassigns 
        (reduce (fn [v+ field] 
                  (conj v+ field `(get-internalized ~inter ~extobj ~(str field)))) 
                [] fields)]
    (list 'let fassigns  proc-form))
  )
