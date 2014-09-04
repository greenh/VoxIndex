#_ ( Copyright (C) 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Provides a set of functions for extracting index content from an Android
      documentation tree and depositing it in an Indexterous database. 
      @(link doc-extract) is the main front end function.
      )
(ns indexterous.android.user.scraper
  (:import
    [indexterous.util BBQueue]
    [java.io PrintStream File]
    [indexterous.index Oid]
    )
  (:use
    [extensomatic.extensomatic]
    [indexterous.android.android-doc]
    [indexterous.index.index]
    [indexterous.index.document]
    [indexterous.util.vocalizer]
    [indexterous.util.defnk]
    [indexterous.exintern.exintern-base]
    [indexterous.exintern.json]
    ))

(def ^:dynamic message-agent (agent nil))

(set-error-handler! message-agent (fn [_ e] (.printStackTrace e)))

(defn write-message [_ msg]
  (println msg) 
  (flush ))

(defn message [msg] (send-off message-agent write-message msg))
   
(defn message-flush [] 
     (let [p (promise)]
       (send-off message-agent (fn [_] (deliver p nil)))
       (deref p)))

; these are rebound as required by the driver functions
(def ^:dynamic  member-msg message)
(def ^:dynamic type-msg message)
(def ^:dynamic package-msg message)

#_ (* Agent used to process output. In this case, we send it objects to be 
     externalized, e.g. into JSON.)
(def output-agent (agent nil))


#_ (* Placeholder output function for use with @(l output-agent).
      @p This is bound dynamically, and has the form @(fun [agent stuff]),
      where 
      @arg agent The agent object.
      @arg stuff An object (notionally, a string) to output.
      @returns nil.) 
(def ^:dynamic output-fn)

#_(defn write-output [_ stuff]
    (.println out-writer stuff ) 
    )

#_ (* Sends a string to be dealt with by the output agent in a nice, orderly manner.
      ) 
(defn output [msg] 
  (send-off output-agent output-fn msg))

#_ (* Signals the end of output to the agent, and delivers the completion notice.) 
(defn output-flush [] 
  (let [p (promise)]
    (send-off output-agent (fn [_] (deliver p nil)))
    (deref p)))

#_ (* The externalizer. This is dynamically bound in @(l javadoc-extract).)
(def ^:dynamic externalizer)  ; (make-JSONExternalizer)

#_ (* Externalizes an object and sends its externalized form to the output agent.
      @arg obj The object to be externalized and output.
      @returns The object.
      )
(defn zap [obj]
  (let [stuff (externalize obj externalizer)]
    (output stuff)
    obj))
  
; Crude awful mechanism for locating starting offsets in an Android type document.
; This list is very carefully ordered to follow the order that stuff
; is encountered in an actual document. Note that these *must* be kept in
; sync with the stuff in "summary-matches" below.
(def summary-start 0)
(def nested-classes-summary 1)
(def attributes-summary 2)
(def inherited-attributes-summary 3)
(def constants-summary 4)
(def inherited-constants-summary 5)
(def fields-summary 6)
(def inherited-fields-summary 7)
(def enum-constants-summary 8)
(def public-ctors-summary 9)
(def protected-ctors-summary 10)
(def public-methods-summary 11)
(def protected-methods-summary 12)
(def inherited-methods-summary 13)
(def attributes-detail 14)
(def constants-detail 15)
(def fields-detail 16)
(def enum-constants-detail 17)
(def public-ctors-detail  18)
(def protected-ctors-detail  19)
(def public-methods-detail 20)
(def protected-methods-detail 21)


(def summary-start-re #"(?i)<h2>Summary</h2>" )
(def summary-end-re #"(?i)<h2>")

(def nested-classes-summary-re #"(?i)<table id=\"nestedclasses\"")
(def attributes-summary-re #"(?i)<table id=\"lattrs\"")
(def inherited-attributes-summary-re #"(?i)<table id=\"inhattrs\"")
(def constants-summary-re #"(?i)<table id=\"constants\"")
(def inherited-constants-summary-re #"(?i)<table id=\"inhconstants\"")
(def fields-summary-re #"(?i)<table id=\"fields\"")
(def inherited-fields-summary-re #"(?i)<table id=\"inhfields\"")
(def enum-constants-summary-re #"(?i)<table id=\"enumconstants\"")
(def public-ctors-summary-re #"(?i)<table id=\"pubctors\"")
(def protected-ctors-summary-re #"(?i)<table id=\"proctors\"")
(def public-methods-summary-re #"(?i)<table id=\"pubmethods\"")
(def protected-methods-summary-re #"(?i)<table id=\"promethods\"")
(def inherited-methods-summary-re #"(?i)<table id=\"inhmethods\"")

(def attributes-detail-re #"(?i)<h2>XML Attributes</h2>")
(def constants-detail-re #"(?i)<h2>Constants</h2>")
(def fields-detail-re #"(?i)<h2>Fields</h2>")
(def enum-constants-detail-re #"(?i)<h2>Enum Values</h2>")
(def public-ctors-detail-re #"(?i)<h2>Public Constructors</h2>")
(def protected-ctors-detail-re #"(?i)<h2>Protected Constructors</h2>")
(def public-methods-detail-re #"(?i)<h2>Public Methods</h2>")
(def protected-methods-detail-re #"(?i)<h2>Protected Methods</h2>")

(def attribute-re #"<A NAME=\"([^\"]+)\"></A>\s*<div [^>]+>\s*<h4 class=\"jd-details-title\">(\S+)\s*</h4>")
(def name-re #"<A NAME=\"([^\"]+)\"></A>")
(def method-re #"<A NAME=\"(([^\(]+)\(([^\)]*)\))\"></A>")

(defn off [pattern content] 
  (let [matcher (re-matcher pattern content)] 
    (if (.find matcher)
      (.start matcher)
      -1)))

#_ (* Generates a vector of character offsets to various points in the 
      "Summary" section of an Android type document, with non-present
      sections having an offset of nil. This servers as fodder for 
      @(link mvrange) and friends.
      
      @arg content A string containing the content of an Android-doc format
      html document.
      )
(defn summary-matches [content]
  (let [start-off (off summary-start-re content)]
    (if start-off
;      (let [end-matcher (.region (re-matcher summary-end-re content)
;                                 (+ start-off 16) (dec (count content)))
;            end-off (if (.find end-matcher) (.start end-matcher) (dec (count content)) )])
      ;;
      ;; WARNING!!! The list below has to be kept in strict synchrony with the 
      ;; "<whatever>-summary" values defined above!!
      ;;
      [start-off
       (off nested-classes-summary-re content)
       (off attributes-summary-re content)
       (off inherited-attributes-summary-re content)
       (off constants-summary-re content)
       (off inherited-constants-summary-re content)
       (off fields-summary-re content)
       (off inherited-fields-summary-re content)
       (off enum-constants-summary-re content)
       (off public-ctors-summary-re content)
       (off protected-ctors-summary-re content)
       (off public-methods-summary-re content)
       (off protected-methods-summary-re content)
       (off inherited-methods-summary-re content)
       (off attributes-detail-re content)
       (off constants-detail-re content)
       (off fields-detail-re content)
       (off enum-constants-detail-re content)
       (off public-ctors-detail-re content)
       (off protected-ctors-detail-re content)
       (off public-methods-detail-re content)
       (off protected-methods-detail-re content)
       (dec (count content))
       ])))

(defn mvdef? [match-vec index] (pos? (nth match-vec index)))
(defn mvstart [match-vec index] (nth match-vec index))

(defn mvend [match-vec index]
  (loop [[pos & more] (next (nthnext match-vec index))]
    (if pos
      (if (pos? pos) 
        pos
        (recur more))
      nil)))

#_ (* Returns the range of a section of an Android-doc type document.
      
      @arg match-vec A vector of offsets as generated by @(link summary-matches).
      @arg index The index of a section within @(arg match-vec).
      @(returns A tuple @(form [start end]), where
                @arg start The starting index of the section described by @(arg index).
                @arg end The ending index.)
      )
(defn mvrange [match-vec index]
  (if (mvdef? match-vec index) 
    [(mvstart match-vec index) (mvend match-vec index)]))

#_ (* Does a simple display of a location vector, for debug purposes.)
(defn printmv [mvec]
  (letfn [(prx [mvec item caption]
            (let [[start end] (mvrange mvec item)]
              (if start
                (printf "%-30s %7d %7d\n" caption start end)
                (printf "%-30s \n" caption))))]
    (prx mvec nested-classes-summary "nested-classes-summary") 
    (prx mvec attributes-summary "attributes-summary") 
    (prx mvec inherited-attributes-summary "inherited-attributes-summary") 
    (prx mvec constants-summary "constants-summary") 
    (prx mvec inherited-constants-summary "inherited-constants-summary") 
    (prx mvec fields-summary "fields-summary") 
    (prx mvec inherited-fields-summary "inherited-fields-summary") 
    (prx mvec enum-constants-summary "enum-constants-summary") 
    (prx mvec public-ctors-summary "public-ctors-summary") 
    (prx mvec protected-ctors-summary "protected-ctors-summary") 
    (prx mvec public-methods-summary "public-methods-summary") 
    (prx mvec protected-methods-summary "protected-methods-summary") 
    (prx mvec inherited-methods-summary "inherited-methods-summary") 
    (prx mvec attributes-detail "attributes-detail") 
    (prx mvec constants-detail "constants-detail") 
    (prx mvec fields-detail "fields-detail") 
    (prx mvec enum-constants-detail "enum-constants-detail") 
    (prx mvec public-ctors-detail  "public-ctors-detail") 
    (prx mvec protected-ctors-detail  "protected-ctors-detail") 
    (prx mvec public-methods-detail "public-methods-detail") 
    (prx mvec protected-methods-detail "protected-methods-detail")) )


#_ (* Generates a @(link java.util.regex.Matcher Matcher) object for the range 
      of a section of an Android-doc document.
      @arg match-vec A vector of offsets as generated by @(link summary-matches).
      @arg index The index of a section within @(arg match-vec).
      
      )
(defn mvmatcher [match-vec index content pattern]
  (let [[start end] (mvrange match-vec index)]
    (if start
      (.region (re-matcher pattern content) start end))))

;-------------------------------------------------------------------------------
#_ (* Given a @(linki java.util.regex.Matcher), returns a lazy sequence of all of the matches 
      found by the matcher.
      @arg matcher The matcher object.
      @returns The lazy sequence, as described above.
     )
(defn member-seqer [matcher]
  (lazy-seq
    (when-let [match (re-find matcher)] 
      (cons (drop 1 match) (member-seqer matcher)))))  

(defn attributes-seq [match-vec content]
  (if (mvdef? match-vec attributes-detail)
    (let [mm (mvmatcher match-vec attributes-detail content attribute-re)]
      (member-seqer mm))))

(defn constants-seq [match-vec content]
  (if (mvdef? match-vec constants-detail)
    (let [mm (mvmatcher match-vec constants-detail content name-re)]
      (member-seqer mm))))

(defn fields-seq [match-vec content]
  (if (mvdef? match-vec fields-detail)
    (let [mm (mvmatcher match-vec fields-detail content name-re)]
      (member-seqer mm))))

(defn enum-constants-seq [match-vec content]
  (if (mvdef? match-vec enum-constants-detail)
    (let [mm (mvmatcher match-vec enum-constants-detail content name-re)]
      (member-seqer mm))))

(defn public-ctors-seq [match-vec content]
  (if (mvdef? match-vec public-ctors-detail)
    (let [mm (mvmatcher match-vec public-ctors-detail  content method-re)]
      (member-seqer mm))))

(defn protected-ctors-seq [match-vec content]
  (if (mvdef? match-vec protected-ctors-detail)
    (let [mm (mvmatcher match-vec protected-ctors-detail  content method-re)]
      (member-seqer mm))))

(defn public-methods-seq [match-vec content]
  (if (mvdef? match-vec public-methods-detail)
    (let [mm (mvmatcher match-vec public-methods-detail content method-re)]
      (member-seqer mm))))

(defn protected-methods-seq [match-vec content]
  (if (mvdef? match-vec protected-methods-detail)
    (let [mm (mvmatcher match-vec protected-methods-detail content method-re)]
      (member-seqer mm))))

(def type-kind-pattern 
  #"<div id=\"jd-header\">[\s\w\n]*(interface|class|enum|annotation|@interface)"
  ;#"(?i)<div id=\"jd-header\">[\s\S\n]*(interface|class|enum|annotation)[\s\n]+<h1>"
  )


(def all-classes-file-name "reference/classes.html")

;(def type-pattern #"<td class=\"jd-linkcol\"><a href=\"([^\"]+)\">([^<]+)</a>")
;(def package-pattern #"\.\./reference/([^/]+(?:/[^/]+)*)/[^/]*")
(def type-pattern #"<td class=\"jd-linkcol\"><a href=\"\.\./([^\"]+)\">([^<]+)</a>")
(def package-pattern #"reference/([^/]+(?:/[^/]+)*)/[^/]*")


#_ (* Based on a documentation tree's @(link all-classes-file-name) file, extracts
      and returns a sequence of tuples, each of which describes the 
      documentation for a specific type. 
      
      @arg base-uri is the base URI of the root of the javadoc tree. 
      @(returns 
         A collection of tuples of the form @(form [name pkg-name target]) where
         @arg name is the simple type name, e.g. "ArrayList"
         @arg pkg-name is the full package name, e.g. "java.util" 
         @arg target is a source-relative URI of the document describing the type,
         e.g. "java/util/ArrayList.html" .)
     )
(defn type-seq [base-uri]
  (let [type-content (slurp (str base-uri "/" all-classes-file-name))]
    (map (fn [[_ target name]] 
           (let [[_ pkg-base] (re-matches package-pattern target)
                 pkg-name (.replaceAll pkg-base "/" ".")]
             [name pkg-name target]))
         (re-seq type-pattern type-content))))

#_ (* Generates a collection of collections, each of which contains tuples describing 
      the types of some package.
      @p @name uses @(link type-seq) to generate a collection of type-specific tuples,
      which it then sorts by package and partitions into per-package sub-collections.
      @arg base-uri is the base URI of the root of the javadoc tree. 
      @arg pkg-set is a set of package name strings which are to be included in 
      or excluded from the list
      @arg exclude? If true, @(arg pkg-set) denotes packages to be excluded\;
      if false, @(arg pkg-set) denotes packages to be included.
      @(returns 
         A collection of collections, where each of the subcollections contains
         tuples describing types of a common Java package. Each tuple has the form
         @(form [name pkg-name target]) where
         @arg target is a source-relative name of the document describing the type,
         e.g. "java/util/ArrayList.html" .
         @arg pkg-name is the full package name, e.g. "java.util" 
         @arg name is the simple type name, e.g. "ArrayList"
         )      
     )
(defn package-seq 
  ([base-uri]
    (partition-by second (sort-by second (type-seq base-uri))))
  ([base-uri pkg-set exclude?]
    (if exclude?
      (filter 
        (fn [[[_ pkg-name _]]] (not (get pkg-set pkg-name))) 
        (partition-by second (sort-by second (type-seq base-uri))))
      (filter 
        (fn [[[_ pkg-name _]]] (get pkg-set pkg-name)) 
        (partition-by second (sort-by second (type-seq base-uri))))))
  )

(defn unempty? [what] (if what true false))

#_ (* Macro for generating typical indexables.
      @p @name supplies all of the boilerplate for\: 
      @(ul
         @li Cretaing an object of the appropriate type
         @li Externalizing the object, and causing it to be output to external media.
         @li Generating an entry that references the indexable. )
      @p By default, @name uses the indexable's name (typically class, member name, etc\. ) 
      as the basis for entry term generation. However, if the first argument in @(arg args) 
      is :t, @name interprets the second argument as the terms to be used. If the first
      argument is :a, then @name assumes the entry is for an attribute, and applies
      some additional massaging.
      @p Note, that as a side effect, the indexable per se is shuffled off to 
      output nirvana via @(l zap), so we're left only with the entries.
      @arg type The (simple) class name for the indexable to be generated.
      @arg args The arguments to be passed to the type's constructor.
      @returns The entry constructed from the object.
      )
(defmacro do-ix [type & args]
  (let [[opt & post-opt-args] args
        [terms & port-terms-args] post-opt-args
        argseq (condp = opt 
                 :t port-terms-args 
                 :a post-opt-args
                 args)
        ixsym (gensym "indexable")
        term-src (condp = opt 
                   :t terms
                   :a `(id-vocalizer
                         (if (.startsWith (name-of ~ixsym) "android:") 
                           (.substring (name-of ~ixsym) 8) 
                           (name-of ~ixsym)))
                   `(id-vocalizer (name-of ~ixsym)))
        makefn (symbol (str "new-" type))]
    #_(prn '--- `(let [~ixsym (~makefn ~@argseq)]
       (zap ~ixsym)
       (new-entry (name-of ~ixsym) ~ixsym ~term-src)))
    `(let [~ixsym (~makefn ~@argseq)]
       (zap ~ixsym)
       (new-entry (if (satisfies? Named ~ixsym) (name-of ~ixsym) (title-of ~ixsym)) 
                  ~ixsym ~term-src))
    ))

#_ (* Generates member-related index artifacts for a Java type based on 
      the content of the type's Android doc page. 
      
      @arg type-index-id The @(linki org.bson.types.ObjectId) of the type's index
      @arg type-qname The fully qualified name of the parent type.
      @arg type-rel-uri The tree-relative URI of the parent type's document.
      @arg content A string containing the content of the parent type's 
      document.
      @arg source-ref The @(linki org.bson.types.ObjectId) of the Android doc tree's source object.
      
      @(returns @(form [member-index-id kind-set member-count]), where\:
         @arg member-index-id The OID of an index whose entries are vocalizations 
         of all of the parent type's member's names, referring to the member's indexables.
         @arg kind-set A set indicating the kinds of members the type contains. 
         Elements of the set are drawn from the universe 
         @(form [:f :m :c :x :a]), denoting that the type has fields, methods, constants,
         constructors, and/or annotation-elements respectively.
         @arg member-count The number of members for the type.)
      #_@(returns 
          @(form [index kind-set member-count]), where\:
          @arg indexables A collection of indexables, one for each member.
          @arg index An index whose entries are vocalizations of all of the 
          parent type's member's names, referring to the member's indexables.
          @arg kind-set A set indicating the kinds of members the type contains. 
          Elements of the set are drawn from the universe 
          @(form [:field :method :constant :constructor :annotation-element]).
          @arg member-count The number of members for the type.)
     )
(defn member-extractor [type-index-id type-qname type-rel-uri content source-ref ]
  (let [member-index-id (Oid/oid)
        parent-iids (str type-index-id "," member-index-id ":" type-index-id)
        mvec (summary-matches content)
        
        public-method-entries 
        (if (mvdef? mvec public-methods-detail)
          (map (fn [[target name parameters]]
                 (do-ix AndroidMethod 
                   name source-ref "0" (str type-rel-uri "#" target) 
                   parent-iids type-qname parameters))
            (public-methods-seq mvec content)))

        protected-method-entries 
        (if (mvdef? mvec protected-methods-detail) 
          (map (fn [[target name parameters]]
                 (do-ix AndroidMethod 
                   name source-ref "0" (str type-rel-uri "#" target) 
                   parent-iids type-qname parameters))
            (protected-methods-seq mvec content)))
        
        constructor-entries
        (if (or (mvdef? mvec public-ctors-detail) (mvdef? mvec protected-ctors-detail)) 
          (map (fn [[target name parameters]]
                 (do-ix AndroidConstructor :t ["constructor"] 
                   name source-ref "0" (str type-rel-uri "#" target)
                   parent-iids type-qname parameters))
            (concat 
              (if (mvdef? mvec public-ctors-detail)(public-ctors-seq mvec content) [])
              (if (mvdef? mvec protected-ctors-detail)(protected-ctors-seq mvec content) []))))
                
        constant-entries
        (if (mvdef? mvec constants-detail) 
          (map (fn [[target]]
                 (do-ix AndroidConstant 
                   target source-ref "0" 
                   (str type-rel-uri "#" target) parent-iids type-qname))
            (constants-seq mvec content)))
          
        field-entries
        (if (mvdef? mvec fields-detail) 
          (map (fn [[target]]
                 (do-ix AndroidField 
                   target source-ref "0" 
                   (str type-rel-uri "#" target) parent-iids type-qname))
            (fields-seq mvec content)))
          
        enum-constant-entries
        (if (mvdef? mvec enum-constants-detail) 
          (map (fn [[target]]
                 (do-ix AndroidEnumConstant 
                   target source-ref "0" 
                   (str type-rel-uri "#" target) parent-iids type-qname))
            (enum-constants-seq mvec content)))
          
         annotation-entries nil
;          (if (mvdef? mvec annotation-elements-detail) 
;            (map (fn [[target name parameters]]
;                   (new-AndroidAnnotationElement 
;                     name source-ref (str type-rel-uri "#" target) type-qname ))
;                 (annotations-seq mvec content)))
          
        attribute-entries
        (if (mvdef? mvec attributes-detail)
          (map (fn [[target t-name]]
                 (do-ix AndroidAttribute :a
                   t-name source-ref "0" 
                   (str type-rel-uri "#" target) parent-iids type-qname))
            (attributes-seq mvec content)))
          
        kind-set  ; not clear we need this any more
        (letfn [(nilc [col inds val] (if-not (empty? inds) (conj col val) col))]
          (-> #{}
            (nilc public-method-entries :public-method)
            (nilc protected-method-entries :protected-method)
            (nilc constructor-entries :constructor)
            (nilc constant-entries :constant)
            (nilc field-entries :field)
            (nilc enum-constant-entries :enum-constant)
            (nilc annotation-entries :annotation-element)
            (nilc attribute-entries :attribute)))
        
        ; [misc-indexables misc-entries]
        misc-entries
        (letfn [(ifn [entries+ what target title terms]
                       (if what
                         (let [entry (do-ix Bookmark :t terms
                                            source-ref "0" 
                                            (str type-rel-uri "#" target) 
                                            (str type-qname " | " title)
                                            parent-iids)]
                           (conj entries+ entry))
                         entries+))
                (ifr [entries+ what rel-uri title terms]
                       (if what
                         (let [entry (do-ix Bookmark :t terms
                                       source-ref "0" 
                                       rel-uri 
                                       (str type-qname " | " title)
                                       parent-iids)]
                           (conj entries+ entry))
                         entries+))]
          (-> []
            (ifn (or (mvdef? mvec public-methods-summary) 
                    (mvdef? mvec protected-methods-summary))
              (if (mvdef? mvec public-methods-summary) "pubmethods" "promethods") 
              "Method summary" ["methods" "method summary"])
	          (ifn (unempty? public-method-entries) 
	              "pubmethods" "Public method summary" 
	              ["public methods" "public method summary"])
	          (ifn (unempty? protected-method-entries) 
	              "promethods" "Protected method summary" 
	              ["protected methods" "protected method summary"])
	          (ifn (unempty? constructor-entries) 
	              "pubctors" "Constructor summary" 
	              ["constructors" "constructor summary"])
	          (ifn (or (unempty? constant-entries) (unempty? enum-constant-entries)) 
	              (if (unempty? constant-entries) "constants" "enumconstants") 
	              "Constant summary" ["constants" "constant summary"])
	          (ifn (unempty? field-entries) 
	              "fields" "Field summary" ["fields" "field summary"])
	          (ifn (mvdef? mvec inherited-methods-summary) 
	              "inhmethods" "Inherited methods" ["inherited methods"])
	          (ifn (mvdef? mvec inherited-constants-summary) 
	              "inhconstants" "Inherited constants" ["inherited constants"])
	          (ifn (mvdef? mvec inherited-fields-summary) 
	              "inhfields" "Inherited fields" ["inherited fields"])
	          (ifn (mvdef? mvec attributes-summary) 
	              "lattrs" "Attributes" ["x m l attributes" "attribute summary"])
	          (ifn (mvdef? mvec inherited-attributes-summary) 
	              "inhattrs" "Inherited attributes" ["inherited attributes"])
	               
;	          (ifr (or (unempty? public-method-entries) (unempty? protected-method-entries))
;	                (if (unempty? public-method-entries) 
;	                  (relative-uri-of (first public-method-entries))
;	                  (relative-uri-of (first protected-method-entries)))
;	                "Method detail" ["method detail"])
;	          (ifr (unempty? public-method-entries) 
;	                (relative-uri-of (first public-method-entries)) 
;	                "Public method detail" ["public method detail"])
;	          (ifr (unempty? protected-method-entries) 
;	                (relative-uri-of (first protected-method-entries)) 
;	                "Protected method detail" ["protected method detail"])
;	          (ifr (unempty? constructor-entries) 
;	                (relative-uri-of (first constructor-entries)) 
;	                "Constructor detail" ["constructor detail"])
;	          (ifr (unempty? constant-entries) 
;	                (relative-uri-of (first constant-entries)) 
;	                "Constant detail" ["constant detail"])
;	          (ifr (unempty? field-entries) 
;	                (relative-uri-of (first field-entries)) 
;	                "Field detail" ["field detail"])
;	          (ifr (unempty? attribute-entries) 
;	                (relative-uri-of (first attribute-entries)) 
;	                "Attribute detail" ["attribute detail" "x m l attribute detail"])
           ))
        
        member-count 
        (apply + (map count [public-method-entries protected-method-entries
                             constant-entries attribute-entries
                             constructor-entries field-entries 
                             enum-constant-entries annotation-entries]))
        
        
        ]

    ;; generate the index, if there's anything to generate the index for
    (if (empty? kind-set)
      nil
      (zap 
        (new-Index 
          member-index-id
          type-qname (str "Members of " type-qname) 
          source-ref
          (new-specs "Members" ["member" ""]
            (concat public-method-entries protected-method-entries
                constant-entries attribute-entries
                field-entries annotation-entries 
                constructor-entries enum-constant-entries misc-entries)))))
    [(if (empty? kind-set) nil member-index-id) kind-set member-count]
    ))   ;;; member-extractor

#_ (* Generates index-related artifacts from the Android docs for a collection 
      of Java types. 
      
      @arg base-uri The base uri of...
      @arg source-ref The @(linki org.bson.types.ObjectId) of the source object.
      @arg type-index-id ???? while processing members.
      @arg types A collection of tuples describing Java types, 
      of the form @(form [doc kind package name])
     
     @(returns @(form [type-entries members]) )
     )
(defn type-extractor [base-uri source-ref type-index-id types]
  (let [[type-entries  members]
        (reduce 
          (fn [[type-entries+ members+] [name package doc]]
            #_(type-msg (format "%s %s.%s" kind package name))
            (let [type-qname (str package "." name )
                  content (slurp (str base-uri "/" doc))
                  [_ k] (re-find type-kind-pattern content)
                  kind (if k 
                         (if (= k "@interface")
                           "annotation"
                           (clojure.string/lower-case k)) nil)
                  
                  _ (type-msg (format "%s.%s %s" package name (if kind kind "!! missing kind!!")))
                  
                  [member-index-id member-kind-set member-count]
                  (member-extractor type-index-id type-qname doc content source-ref )
                  
                  type-iid (if member-index-id
                             (str type-index-id "," 
                                  member-index-id ":" type-index-id)
                             (str type-index-id ))
                  
                  ; do a little song-and-dance here to get inner class names like
                  ; "whatsit.xxx" to show up as "xxx" as well as "whatsit dot xxx".
                  terms 
                  (let [[_ tail-name] (re-matches #".*\.([^\.]+)" name)]
                    (if tail-name
                      (concat (id-vocalizer name) (id-vocalizer tail-name))
                      (id-vocalizer name)))
                  
                  type-entry
                  (condp = kind
                    "class" 
                    (do-ix AndroidClass :t terms
                      name source-ref "0" doc type-iid member-index-id package member-kind-set)
                    "interface" 
                    (do-ix AndroidInterface  :t terms
                      name source-ref "0" doc type-iid member-index-id package member-kind-set)
                    "annotation" 
                    (do-ix AndroidAnnotation  :t terms
                      name source-ref "0" doc type-iid member-index-id package member-kind-set)
                    "enum"
                    (do-ix AndroidEnum  :t terms
                      name source-ref "0" doc type-iid member-index-id package member-kind-set)
                    
                    (message (str "Unrecognized type kind: " kind 
                                  " in " (str package "." name)) ))
                  
                  ]
              [(conj type-entries+ type-entry)
               (+ members+ member-count)]))
          [[] 0] 
          types)]
    [type-entries members]
    )
  )    ;;; type-extractor

;-------------------------------------------------------------------------------
#_ (* Object containing all the information needed to represent and coordinate
      index construction for a document tree.
      )
(defconstructo DocJob []
  [base-uri
   source 
   procs
   do-members
   message-functions
   incexc-list
   exclude?
   (type-index-id (Oid/oid))
   (package-index-id (Oid/oid))
   (start-time (System/nanoTime))
   (end-time* (ref nil))
   (pkg-seq* (ref (if incexc-list 
                    (package-seq base-uri incexc-list exclude?)
                    (package-seq base-uri))))
   (running* (ref procs))
   (package-results (BBQueue.))
   (result* (ref nil))
   (final-result (promise)) 
   (package-count* (ref 0))
   (type-count* (ref 0))
   (member-count* (ref 0))
   (completed-ok* (ref true))
   ]
  { :new-sym new-DocJob }
  (start-nsec [this] start-time)
  (end-nsec [this] @end-time*)
  (packages-in [this] @package-count*)
  (types-in [this] @type-count*)
  (members-in [this] @member-count*)
  (source-id-of [this] (id-of source)) 
  (source-name-of [this] (name-of source))
  (source-desc-of [this] (description-of source))
  (package-index-id-of [this] package-index-id)
  (type-index-id-of [this] type-index-id) 
  (base-uri-of [this] base-uri)
  (next-package [this] 
    (dosync 
      (if-let [next-pkg (first @pkg-seq*)]
        (do
          (alter pkg-seq* next)
          next-pkg)
        nil)))
  (final-result [this] final-result)
  (members? [this] do-members)
  (message-fns [this] message-functions)
  (completed-ok? [this] @completed-ok*)

  #_ (* Adds the results of extracting from a package to the job's queue of 
        such results. 
        
        @(arg package-result Results for the package, a sequence of the form
              @(form [type-entries package-entry indexables indexes]), where
              @arg type-entries A collection of entries for types belonging 
              to the processed package.
              @arg package-entry A single entry describing the package as a whole.
              @arg indexables A collection of all indexables generated 
              for the package.
              @arg indexes A collection of all indexes generated by the package.)
       )
  (package-complete [this proc-id package-result type-count member-count]
     ;;; NB: package-results is mutable state with its own synchronization...
     ;;; DO NOT STICK IT IN THE DOSYNC!!!
     (.add package-results package-result)
     (dosync 
       (alter type-count* + type-count)
       (alter member-count* + member-count)
       (alter package-count* inc)
       ))
  
  #_ (* Returns the next package result from the results queue.
        )
  (next-package-result [this] (.remove package-results))
  
  #_ (* Signals that one of the extraction threads has completed. 
        )
  (process-complete [this proc-id ok]
    (when 
      (dosync 
        (alter completed-ok* #(and ok %)) 
        (zero? (alter running* dec)))
      (.terminate package-results)))
  
  #_ (* Signals the completion of the extraction job and records the job's 
        results.
        
        @(arg result A tuple of the form @(form [root-entries indexables indexes]))
        )
  (job-complete [this result]
     (dosync 
       (ref-set result* result)
       (ref-set end-time* (System/nanoTime))
       (deliver final-result result)))
  
  #_ (* d
        @(returns @(form [type-index package-index indexables indexes])) )
  (results [this] @result*)
  (result-source [this] source)
  )   ;; DocJob


#_ (* Extracts index-related artifacts from document tree on per-package  
      basis, possibly as one of several threads of a complete documentation 
      processing job. 
      
      @p @name normally is instantiated in multiple threads by a job processing
      a complete Android docs tree. As such, @name works in a loop, wherein it retrieves 
      the next to-be-processed package name from the job, generates indexes and
      indexables for the package, each type in the package, and all of each
      type's members. 
      
      @p @name forwards the aggregate output :em collections of indexes and 
      indexables :em back to the job, where they're consolidated and ultimately
      returned as the overall job's output.
      
      @arg id The identity of the thread within the job\.\.\. just a number allowing
      one thread to be distinguished from its counterparts.
      @arg job The @(link DocJob) object for the job.
      )
(defn package-process [id job]
  (try
    (let [base-uri (base-uri-of job)
          source-ref (source-id-of job)
          type-index-id (type-index-id-of job)
          type-iid (str type-index-id)
          [p-msg t-msg m-msg] (message-fns job)] 
      (binding [package-msg p-msg
                type-msg t-msg
                member-msg m-msg]
        (message (format "%d: running" id))
        (loop [pkg-goop (next-package job)]
          (if pkg-goop
            (let [[_ pkg-name _] (first pkg-goop)
                 _ (package-msg (str (format "%d: package %s" id pkg-name)
                                     #_ [(count type-entries)
                                       (count indexables)
                                       (count indexes)]))
                  
                  [type-entries member-count]
                  (type-extractor base-uri source-ref type-index-id pkg-goop)
                   
                  package-index 
                  (new-Index (Oid/oid) pkg-name (str "Index of " pkg-name) source-ref 
                             (new-specs nil nil type-entries))

                  package-entry 
                  (do-ix AndroidPackage pkg-name source-ref "0" type-iid package-index)
                  ]
              (zap package-index)
              (package-msg (format "%d: package %s complete %d types %d members" 
                                   id pkg-name (count type-entries) member-count))
              
              (package-complete job id [type-entries package-entry]
                (count type-entries) member-count)
              (recur (next-package job)))
            ; else... all packages done, terminate process
            (do
              (message (format "%d: complete" id))
              (process-complete job id true))))))
    (catch Exception e 
      (printf "%d: Exception -- %s\n" id (.getMessage e))
      (.printStackTrace e))))


(def pfoo (ref nil))

#_ (* Consolidates the per-package results produced by @(link package-process),
      and initiates job completion proceedings when everything has completed. 
      @p This normally runs in its own thread concurrently with the 
      @(link package-process) threads, and processes their results as they
      become available.
      @arg job The @(link DocJob) object for the job.
      )
(defn reduction-process [job]
  (try
    (message "X: running")
    (letfn [(completed-pkg-seq []
              (if-let [pkg-stuff (next-package-result job)]
                (lazy-seq (cons pkg-stuff (completed-pkg-seq)))))]
       (let [[type-entries pkg-entries indexables indexes]
             (reduce 
               (fn [[type-entries+ pkg-entries+]
                    [pkg-type-entries pkg-entry pkg-indexables pkg-indexes]]
                 [(concat type-entries+ pkg-type-entries)
                  (conj pkg-entries+ pkg-entry)])
               [[] []] 
               (completed-pkg-seq))
             
		         class-index-entry   
		         (do-ix Bookmark :t  ["index of classes" "class index"]
                    (source-id-of job) "0" 
		                "reference/classes.html" "Class Index" nil)
           
		         package-index-entry
		         (do-ix Bookmark :t ["index of packages" "package index"]
                    (source-id-of job) "0" 
		                "reference/packages.html" "Package Index" nil)
           
           poo
         (new-ContextualIndex ; new-AndroidTypeIndex
                (type-index-id-of job)
                (str (source-name-of job) "-types") 
                (str (source-desc-of job) " Types")
                (source-id-of job)
                (new-specs
                  "Classes" ["" "class" "enum" "interface" "type" "annotation"] type-entries
                  "Packages" ["package"] pkg-entries
                  "Convenience" nil [class-index-entry package-index-entry]))
           
           ]
        
         (dosync (ref-set pfoo poo))
         (zap poo)
         
         #_(zap (new-AndroidTypeIndex
                (type-index-id-of job)
                (str (source-name-of job) "-types") 
                (str (source-desc-of job) " Types")
                (source-id-of job)
                (new-specs
                  "Classes" ["" "class" "enum" "interface" "type" "annotation"] type-entries
                  "Packages" ["package"] pkg-entries
                  "Convenience" nil [class-index-entry package-index-entry])))
         
         (zap (new-ContextualIndex ; new-AndroidPackageIndex
                (package-index-id-of job)
                (str (source-name-of job) "-packages")
                (str (source-desc-of job) " Packages")
                (source-id-of job)  
                (new-specs "Packages" nil pkg-entries)))           
         (job-complete job true)
         (message "X: complete")))
    (catch Exception e 
      (binding [*out* *err*] (printf "X: Exception -- %s\n" (.getMessage e)))
      (.printStackTrace e)
      (job-complete job false)))
  )   ;; reduction-process


#_ (* Processes an android tree, extracting index information as it goes. @name can
      operate in a single-threaded mode (useful for debugging), or multithreaded.
      @p Arguments are as in @(l javadoc-extract).
      @returns A @(link JavadocJob) object describing the extraction job, and containing
      its results.
     )
(defn doc-process [name description version base-uri remote-uri 
                        {:keys [threads members vm vt vp include exclude]}]
  (let [source (zap (new-AndroidSource name description version { "0" remote-uri }))
        job (new-DocJob 
              base-uri source 
              (if (= threads 0) 1 threads) members
              [(if (or vp vm vt) message (fn [_]))
               (if (or vt vm) message (fn [_]))
               (if (or vm) message (fn [_]))]
              (if include include exclude)
              (if include false true))]
    (message (str "Starting " name " at " base-uri))
    (if (zero? threads)
      (do
        (package-process 0 job )
        (reduction-process job))
      (do 
        (future (reduction-process job))
        (dotimes [id threads] 
          (future (package-process id job)))))
    (deref (final-result job))
    (if (completed-ok? job)
      (let [elapsed (/ (double (- (end-nsec job) (start-nsec job))) 1000000000)] 
        (message (format "complete: %.3f elapsed; %d packages, %d types, %d members"
                         elapsed (packages-in job) (types-in job) (members-in job))))
      (message "failed!"))
    (message-flush )
    job) ; must be last!!!
  ) ;; doc-process

#_ (* Processes a documentation tree, extracting index information as it goes. @name can
      operate in a single-threaded mode (useful for debugging), or multithreaded.
      @arg name A (short) name for the source tree\; used as the name for the 
      source object.
      
      @arg description A longer name or short description for the tree.
      @arg base-uri URI string that locates the Android doc tree from which @name will
      extract index information.
      @arg remote-uri URI string for the location of the tree to be used
      when servicing requests for the tree's document content. This may be nil if the
      tree content will always be served locally.
      @option :threads If zero, operates in single-threaded mode. If nonzero,
      determines the number of extraction threads to run concurrently. 
      Default value is 0.
      @option nodb false If true, @name just goes through the motions 
      (and returns the generated results) without updating the database. 
      @option :members true If true, extracts member information and constructs per-type 
      member indexes. (Not currently implemented!!!)
      @option :vm false If true, outputs a message describing each member encountered.
      @option :vt false If true, outputs a message describing each type 
      (class, interface, enum, annotation, trait, etc.) encountered. 
      @option :vp true If true, outputs a message describing each package 
      encountered.
      @option :include nil A set of package name strings to be included by 
      the extraction process. Note that at most one of @(option :include) 
      or @(option :exclude) can be specified.
      @option :exclude nil A set of package name strings to be excluded.
      @returns A @(link DocJob) object describing the extraction job, and containing
      its results.
     )
(defn scrape [dest name description version 
               pkg-terms type-terms base-uri remote-uri 
               {:keys [threads members vm vt vp include exclude] :as opts}]
  (if (and include exclude)
    (throw (Exception. "Cannot specify both :include and :exclude")))
  
  (let [out-stream (PrintStream. (File. dest))] 
    (binding [output-fn (fn [_ stuff] (.println out-stream stuff))
              externalizer (make-JSONExternalizer)]
      (let [job (doc-process name description version base-uri remote-uri opts)
            package-root 
            (new-AndroidRoot 
               (package-index-id-of job) (str name "-packages")
               (source-id-of job) "0" (str description " packages"))
            type-root 
            (new-AndroidRoot 
               (type-index-id-of job) name (source-id-of job) "0" description)]
        (zap package-root)
        (zap (new-RootEntry
               (Oid/oid) (str name "-packages") (str description " packages") 
               (source-id-of job) "0" version (id-of package-root) pkg-terms))
        (zap type-root)
        (zap (new-RootEntry
               (Oid/oid) name description (source-id-of job) "0" version 
               (id-of type-root) type-terms))
        (output-flush)
        (.close out-stream))))
  )  ;; scrape
  
(defnk ascr [dest name description version 
                        pkg-terms type-terms base-uri remote-uri   
                        :threads 0 :members true :vm false :vt false :vp true
                        :include nil :exclude nil
                        ]
  (scrape dest name description version pkg-terms type-terms base-uri remote-uri 
                   { :threads threads :members members :vm vm :vt vt :vp vp
                    :include include :exclude exclude }))


