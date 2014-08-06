#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
(ns indexterous.javadoc.client.javadoc-extract
  (:import
    [indexterous.util BBQueue]
    [java.io PrintStream File]
    [indexterous.index Oid]
    )
  (:use 
    [extensomatic.extensomatic]
    [indexterous.util.vocalizer]
    [indexterous.util.defnk]
    [indexterous.javadoc.javadoc-defs]
    [indexterous.javadoc.javadoc]
    [indexterous.index.index]
    [indexterous.index.document]
    [indexterous.exintern.exintern-base]
    [indexterous.exintern.json]
    )
  )

(def ^:dynamic message-agent (agent nil))

(set-error-handler! message-agent (fn [_ e] (.printStackTrace e)))

(defn write-message [_ msg]
  (println msg) 
  (flush ))

(defn message [msg] (send-off message-agent write-message msg))
   
(defn write-error [_ msg]
  (binding [*out* *err*] 
    (println msg)
    (flush)))

(defn error-message [msg] (send-off message-agent write-error msg))
   
(defn message-flush [] 
     (let [p (promise)]
       (send-off message-agent (fn [_] (deliver p nil)))
       (deref p)))

; these are be rebound as required by the driver functions
(def ^:dynamic member-msg message)
(def ^:dynamic type-msg message)
(def ^:dynamic package-msg message)
   
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
   
(defn output-flush [] 
  (let [p (promise)]
    (send-off output-agent (fn [_] (deliver p nil)))
    (deref p)))

(defn nilc [col inds val] (if-not (empty? inds) (conj col val) col))

(def package-pattern 
  (re-pattern "(?i)<A HREF=\"([^\"]*)\" title=\"([^ ]*) in ([^\"]*)\">(?:<I>)?([^>]*)(?:</I>)?</A>"))

(def inherited-methods #"<a name=\"(methods_inherited_from_[^\"]+)\">")
(def inherited-methods-end #"<a name=\"methods_inherited_from_class_java.lang.Object\">")
(def inherited-method #"<a href=\"([^\"]+)\">([^<]+)</a>")

(def field-detail #"(?i)<A NAME=\"field_detail\">")
(def constructor-detail #"(?i)<A NAME=\"constructor_detail\">")
(def method-detail #"(?i)<A NAME=\"method_detail\">")
(def enum-constant-detail #"(?i)<A NAME=\"enum_constant_detail\">")
(def annotation-detail #"(?i)<A NAME=\"annotation_type_element_detail\">")

(def bottom-mark #"(?i)<A NAME=\"navbar_bottom\">")

(def method-pattern #"(?i)<A NAME=\"(([a-zA-Z_0-9]+)\(([^)]*)\))\">")
  ; \1 = full target string, \2 = method name, \3 = parameter list
(def field-pattern #"(?i)<A NAME=\"([a-zA-Z_0-9]+)\">")

#_ (* Based on a javadoc tree's @(c allclasses-noframe.html) file, extracts
      and returns a sequence of vectors, each of which describes the 
      javadoc document for a specific java type. 
      
      @arg base-uri is the base URI of the root of the javadoc tree. 
      @(returns 
         A collection of tuples of the form @(form [doc type package name]) where
         @arg doc is a source-relative name of the document describing the type,
         e.g. "java/util/ArrayList.html" .
         @arg type is one of  "class", "interface", "annotation", or "enum"
         @arg package is the full package name, e.g. "java.util" 
         @arg name is the simple type name, e.g. "ArrayList")
     )
(defn type-seq [base-uri]
  (map #(drop 1 %) (re-seq package-pattern (slurp (str base-uri "/" all-classes-name)))))

#_ (* Generates a collection of collections, each of which contains tuples describing 
      the types of some Java package.
      @p @name uses @(link type-seq) to generate a collection of type-specific tuples,
      which it then sorts by package and partitions into per-package sub-collections.
      @arg base-uri is the base URI of the root of the javadoc tree. 
      @(returns 
         A collection of collections, where each of the subcollections contains
         tuples describing types of a common Java package. Each tuple has the form
         @(form [doc type package name]) where
         @arg doc is a source-relative name of the document describing the type,
         e.g. "java/util/ArrayList.html" .
         @arg type is one of  "class", "interface", "annotation", or "enum"
         @arg package is the full package name, e.g. "java.util" 
         @arg name is the simple type name, e.g. "ArrayList"
         )      
     )
(defn package-seq [base-uri] 
  (partition-by #(nth % 2) (sort-by #(nth % 2)(type-seq base-uri))))

;-------------------------------------------------------------------------------
#_ (* Given a @(linki java.util.regex.Matcher), 
      returns a lazy sequence of all of the matches 
      found by the matcher, with the first element of each match 
      (the whole-matched-string) deleted.
      @arg matcher The matcher object.
      @returns The lazy sequence, as described above.
     )
(defn member-seqer [matcher]
  (lazy-seq
    (when-let [match (re-find matcher)] 
      (cons (drop 1 match) (member-seqer matcher)))))  

#_ (* Given the content of a Javadoc type document, searches for method targets 
      in the method detail section. 
      @(returns 
         A sequence of the form @(form [name params]), where
         @arg name The method's simple name.
         @arg params A string containing a list of comma-separated parameter types.)
      )
(defn method-seq [content]
  (let [begin (re-matcher method-detail content),
        end (re-matcher bottom-mark content)]
    (if (and (.find begin) (.find end (.start begin)))
      (let [mm (re-matcher method-pattern content),
            methods (.region mm (.start begin) (.start end))]
        (member-seqer methods ))
      nil)))

(defn inherited-method-seq [content]
  (let [begin (re-matcher inherited-methods content),
        end (re-matcher inherited-methods-end content)]
    (if (and (.find begin) (.find end (.start begin)))
      (let [mm (re-matcher method-pattern content),
            methods (.region mm (.start begin) (.start end))]
        (member-seqer methods ))
      nil)))

(defn constructor-seq 
  ([content]
    (let [begin (re-matcher constructor-detail content)]
      (if (.find begin)
        (let [md (re-matcher method-detail content),
              has-md (.find md (.start begin)),
              bottom (re-matcher bottom-mark content),
              has-bottom (.find bottom (.start begin))]
          (if (or has-md has-bottom)
            (let [mm (re-matcher method-pattern content),
                  constrs (.region mm (.start begin) 
                            (if has-md (.start md) (.start bottom)))]
              (member-seqer constrs ))
            nil))
        nil)))
  ([base-uri doc]
    (constructor-seq (slurp (str base-uri "/" doc)))))

(defn field-seq [content]
  (let [begin (re-matcher field-detail content)]
    (if (.find begin)
      (let [cd (re-matcher constructor-detail content),
            has-cd (.find cd (.start begin)),
            md (re-matcher method-detail content),
            has-md (.find md (.start begin)),
            bottom (re-matcher bottom-mark content),
            has-bottom (.find bottom (.start begin))]
        (if (or has-cd has-md has-bottom)
          (let [mm (re-matcher field-pattern content),
                fields (.region mm (+ (.start begin) (.length (.group begin) )) 
                                (cond 
                                  has-cd (.start cd)
                                  has-md (.start md)
                                  true (.start bottom)))]
            (member-seqer fields ))
          nil)))))

(defn constant-seq [content]
  (let [begin (re-matcher enum-constant-detail content)]
    (if (.find begin)
      (let [md (re-matcher method-detail content),
            has-md (.find md (.start begin)),
            bottom (re-matcher bottom-mark content),
            has-bottom (.find bottom (.start begin))]
        (if (or has-md has-bottom)
          (let [mm (re-matcher field-pattern content),
                constants (.region mm 
                                   (+ (.start begin) (.length (.group begin) )) 
                                   (cond 
                                     has-md (.start md)
                                     true (.start bottom)))]
            (member-seqer constants ))
          nil))
      nil)))

(defn annotation-seq [content]
  (let [begin (re-matcher annotation-detail content),
        end (re-matcher bottom-mark content)]
    (if (and (.find begin) (.find end (.start begin)))
      (let [mm (re-matcher method-pattern content),
            methods (.region mm (.start begin) (.start end))]
        (member-seqer methods ))
      nil)))

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

#_ (* Macro for generating typical indexables.
      @p @name supplies all of the boilerplate for\: 
      @(ul
         @li Cretaing an object of the appropriate type
         @li Externalizing the object, and causing it to be output to external media.
         @li Generating an entry that references the indexable. )
      @p By default, @name uses the 
         indexable's name (typically class, member name, etc\. ) as the basis
         for entry term generation. However, if the first argument in @(arg args) 
         is :t, @name interprets the second argument as the terms to be used.
      @arg type The (simple) class name for the indexable to be generated.
      @arg args The arguments to be passed to the type's constructor.
      @returns The entry constructed from the object.
      )
(defmacro do-ix [type & args]
  (let [[term-opt terms & other-args] args
        argseq (if (= term-opt :t) other-args args)
        ixsym (gensym "indexable")
        term-src (if (= term-opt :t) terms `(id-vocalizer (name-of ~ixsym)))
        makefn (symbol (str "new-" type))]
    #_(prn '--- `(let [~ixsym (~makefn ~@argseq)]
       (zap ~ixsym)
       (new-entry (name-of ~ixsym) ~ixsym ~term-src)))
    `(let [~ixsym (~makefn ~@argseq)]
       (zap ~ixsym)
       (new-entry (if (satisfies? Named ~ixsym) (name-of ~ixsym) (title-of ~ixsym)) 
                  ~ixsym ~term-src))))

#_ (* Generates member-related index artifacts for a Java type based on 
      the content of the type's Javadoc page. 
      @arg type-index-id The OID of the type's index.
      @arg type-qname The fully qualified name of the parent type.
      @arg type-rel-uri The tree-relative URI of the parent type's Javadoc document.
      @arg content A string containing the content of the parent type's 
      Javadoc document.
      @arg source-ref The @(linki org.bson.types.ObjectId) of the Javadoc tree's source object.
      
      @(returns 
         [member-index-id kind-set member-count], where\:
         @arg member-index-id The OID of an index whose entries are vocalizations 
         of all of the parent type's member's names, referring to the member's indexables.
         @arg kind-set A set indicating the kinds of members the type contains. 
         Elements of the set are drawn from the universe 
         @(form [:f :m :c :x :a]), denoting that the type has fields, methods, constants,
         constructors, and/or annotation-elements respectively.
         @arg member-count The number of members for the type.)
     )
(defn member-extractor [type-index-id type-qname type-rel-uri content source-ref ]
  (let [member-index-id (Oid/oid)
        parent-iids [type-index-id [member-index-id type-index-id]]
        
        method-entries 
        (map
          (fn [[target name parameters]]
            (do-ix JavadocMethod 
                   name source-ref (str type-rel-uri "#" target) 
                   parent-iids type-qname parameters))
          (method-seq content))
        
        constructor-entries
        (map
          (fn [[target name parameters]]
            (do-ix JavadocConstructor :t ["constructor"]
                   name source-ref (str type-rel-uri "#" target) 
                   parent-iids type-qname parameters))
          (constructor-seq content))
        
        constant-entries
        (map
          (fn [[target]]
            (do-ix JavadocConstant
                   target source-ref (str type-rel-uri "#" target) parent-iids type-qname))
          (constant-seq content))
          
        field-entries
        (map
          (fn [[target]]
            (do-ix JavadocField
                   target source-ref (str type-rel-uri "#" target) parent-iids type-qname))
          (field-seq content))
          
        annotation-entries
        (map
          (fn [[target name parameters]]
            (do-ix JavadocAnnotationElement
                   name source-ref (str type-rel-uri "#" target) parent-iids type-qname ))
          (annotation-seq content))
          
        kind-set (-> #{}
                   (nilc method-entries :m)
                   (nilc constructor-entries :x)
                   (nilc constant-entries :c)
                   (nilc field-entries :f)
                   (nilc annotation-entries :a))
        
        misc-entries
        (let [[_ inherited] (re-find inherited-methods content)]
          (letfn [(ifn [entries+ what target title terms]
                       (if-not (empty? what)
                         (let [entry (do-ix Bookmark :t terms
                                            source-ref 
                                            (str type-rel-uri "#" target) 
                                            (str type-qname " | " title)
                                            parent-iids)]
                        (conj entries+ entry))
                      entries+))]
             (-> []
               (ifn (not-empty method-entries) "method_summary" "Method summary" 
                    ["methods" "method summary"])
               (ifn (not-empty constructor-entries) "constructor_summary" "Constructor summary" 
                    ["constructors" "constructor summary"])
               (ifn (not-empty constant-entries) "constant_summary" "Constant summary"
                    ["constants" "constant summary"])
               (ifn (not-empty field-entries) "field_summary" "Field summary"
                    ["fields" "field summary"])
               (ifn (not-empty method-entries) "method_detail" "Method detail" 
                    ["method detail"])
               (ifn (not-empty constructor-entries) "constructor_detail" "Constructor detail"
                    ["constructor detail"])
               (ifn (not-empty constant-entries) "constant_detail" "Constant detail"
                    ["constant detail"])
               (ifn (not-empty field-entries) "field_detail" "Field detail"
                    ["field detail"])
               (ifn (not-empty inherited) inherited "Inherited methods" 
                    ["inherited methods"])
            )))
        
        member-count (apply + (map count [method-entries constant-entries
                                          constructor-entries
                                          field-entries annotation-entries]))]
    (if (empty? kind-set)
          nil
          (zap (new-Index 
                 member-index-id
                 type-qname (str "Members of " type-qname) 
                 source-ref
                 (new-specs "Members" ["member" "" ]
                            (concat misc-entries method-entries constant-entries
                                    constructor-entries field-entries annotation-entries)))))
    [(if (empty? kind-set) nil member-index-id) kind-set member-count]))

;-------------------------------------------------------------------------------
#_ (* Generates index-related artifacts from the Javadoc for a collection 
      of Java types. 
      
      @arg base-uri
      @arg source-reg The @(link org.bson.types.ObjectId) of the source object.
      while processing members.
      @arg types A collection of tuples describing Java types, 
      of the form @(form [doc kind package name])
     
     @(returns @(form [type-entries members]) )
     )
(defn type-extractor [base-uri source-ref type-index-id types]
  (let [[type-entries members]
        (reduce 
          (fn [[type-entries+ members+] [doc kind package name]]
            (type-msg (format "%s %s.%s" kind package name))
            (let [type-qname (str package "." name )
                  content (slurp (str base-uri "/" doc))
                  
                  [member-index-id member-kind-set member-count]
                  (member-extractor type-index-id type-qname doc content source-ref )
                  
                  members-ref member-index-id
                  type-iid (if member-index-id 
                             [type-index-id [member-index-id type-index-id]]
                             type-index-id)
                  
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
                    (do-ix JavadocClass :t terms
                           name source-ref doc type-iid package 
                           member-kind-set members-ref)
                    "interface" 
                    (do-ix JavadocInterface :t terms
                           name source-ref doc type-iid package 
                           member-kind-set members-ref)
                    "annotation" 
                    (do-ix JavadocAnnotation :t terms
                           name source-ref doc type-iid package 
                           member-kind-set members-ref)
                    "enum"
                    (do-ix JavadocEnum :t terms
                           name source-ref doc type-iid package 
                           member-kind-set members-ref)
                    
                    (message (str "Unrecognized type kind: " kind 
                                  " in " (str package "." name)) ))
                  ]
              [(conj type-entries+ type-entry)
               (+ members+ member-count)]))
          [[] 0] 
          types)]
    [type-entries members]
    )
  )

#_ (* Object containing all the information needed to represent and coordinate
      index construction for a Javadoc tree.
      )
(defconstructo JavadocJob []
  [base-uri
   source
   procs
   do-members
   message-functions
   (type-index-id (Oid/oid))
   (package-index-id (Oid/oid))
   (start-time (System/nanoTime))
   (end-time* (ref nil))
   (pkg-seq* (ref (package-seq base-uri)))
   (running* (ref procs))
   (package-results (BBQueue.))
   (result* (ref nil))
   (final-result (promise)) 
   (package-count* (ref 0))
   (type-count* (ref 0))
   (member-count* (ref 0))
   (completed-ok* (ref true))
   ]
  (start-nsec [this] start-time)
  (end-nsec [this] @end-time*)
  (packages-in [this] @package-count*)
  (types-in [this] @type-count*)
  (members-in [this] @member-count*)
  (source-id-of [this] (id-of source)) 
  (source-name-of [this] (name-of source))
  (source-desc-of [this] (description-of source))
  (type-index-id-of [this] type-index-id) 
  (package-index-id-of [this] package-index-id)
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
              )
       )
  (package-complete [this proc-id package-result type-count member-count]
    ;; NB: Don't put the (.add ...) in the dosync... it's mutable state!!!
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
        
        @(arg result True if the job succeeded.)
        )
  (job-complete [this result]
     (dosync 
       (ref-set result* (and @completed-ok* result))
       (ref-set end-time* (System/nanoTime))
       (deliver final-result result)))
  
  #_ (* Returns the completion status for the job.
        @(returns True, if the job completed OK.) )
  (results [this] @result*)
  #_(result-source [this] source)
  )


#_ (* Extracts index-related information from Javadoc on a sequence of package 
      as one of several threads of a Javadoc processing job. 
      
      @p @name normally is instantiated in multiple threads by a job processing
      a complete Javadoc tree. As such, @name works in a loop, wherein it retrieves 
      the next to-be-processed package name from the job, generates indexes and
      indexables for the package, each type in the package, and all of each
      type's members. 
      
      @p @name forwards the aggregate output :em collections of indexes and 
      indexables :em back to the job, where they're consolidated and ultimately
      returned as the overall job's output.
      
      @arg id The identity of the thread within the job\.\.\. just a number allowing
      one thread to be distinguished from its counterparts.
      @arg job The @(link JavadocJob) object for the job.
      )
(defn javadoc-package-process [id job ]
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
            (let [[_ _ pkg-name _] (first pkg-goop)
                 _ (package-msg (str (format "%d: package %s" id pkg-name)
                                     #_ [(count type-entries)
                                       (count indexables)
                                       (count indexes)]))
                  
                  [type-entries  member-count]
                  (type-extractor base-uri source-ref type-index-id pkg-goop)
                   
                  package-index-id (Oid/oid) 
                  _ (zap (new-Index 
                           package-index-id pkg-name (str "Index of " pkg-name) 
                           source-ref (new-specs nil nil type-entries)))
                  
                  package-entry
                  (do-ix JavadocPackage pkg-name source-ref type-iid package-index-id)
                  ]
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
      (binding [*out* *err*] (printf "%d: Exception -- %s\n" id (.getMessage e)))
      (.printStackTrace e)
      (process-complete job id false))))


#_ (* Consolidates the per-package results produced by @(link javadoc-package-process),
      and initiates job completion proceedings when everything has completed. 
      @p This normally runs in its own thread concurrently with the 
      @(link javadoc-package-process) threads, and processes their results as they
      become available.
      @arg job The @(link JavadocJob) object for the job.
      )
(defn reduction-process [job]
  (message "X: running")
  (try 
    (letfn [(completed-pkg-seq []
              (if-let [pkg-stuff (next-package-result job)]
                (lazy-seq (cons pkg-stuff (completed-pkg-seq)))))]
      (let [[type-entries pkg-entries]
            (reduce 
              (fn [[type-entries+ pkg-entries+]
                   [pkg-type-entries pkg-entry]]
                [(concat type-entries+ pkg-type-entries)
                 (conj pkg-entries+ pkg-entry)])
              [[] []] 
              (completed-pkg-seq))]
        (zap (new-JavadocTypeIndex
               (type-index-id-of job)
               (str (source-name-of job) "-types") 
               (str (source-desc-of job) " Types")
               (source-id-of job)
               (new-specs
                 "Classes" ["" "class" "enum" "interface" "type" "annotation"] type-entries
                 "Packages" ["package"] pkg-entries
                 "Convenience" [(do-ix Bookmark 
                                       :t ["index of classes" "class index"]
                                       (source-id-of job) 
                                       "allclasses-noframe.html" "Class Index" 
                                       (type-index-id-of job)) 
                                (do-ix Bookmark 
                                       :t ["index of packages" "package index"]
                                       (source-id-of job) 
                                       "overview-summary.html" "Package Index" 
                                       (type-index-id-of job))])))
        (zap (new-JavadocPackageIndex
               (package-index-id-of job) 
               (str (source-name-of job) "-packages")
               (str (source-desc-of job) " Packages")
               (source-id-of job) 
               (new-specs ["Packages" nil pkg-entries])))
        (job-complete job true)
        (message "X: complete")))
    (catch Exception e 
      (binding [*out* *err*] (printf "X: Exception -- %s\n" (.getMessage e)))
      (.printStackTrace e)
      (job-complete job false))))


#_ (* Processes a javadoc tree, extracting index information as it goes. @name can
      operate in a single-threaded mode (useful for debugging), or multithreaded.
      @p Arguments are as in @(l javadoc-extract).
      @returns A @(link JavadocJob) object describing the extraction job, and containing
      its results.
     )
(defnk javadoc-process [name description version base-uri remote-uri 
                        :threads 0 :members true :vm false :vt false :vp false ]
  (let [source (zap (new-JavadocSource name description version name remote-uri))
        job (make-JavadocJob 
              base-uri source 
              (if (= threads 0) 1 threads) members
              [(if (or vp vm vt) message (fn [_]))
               (if (or vt vm) message (fn [_]))
               (if (or vm) message (fn [_]))])]
    (message (str "Starting " name " at " base-uri))
    (if (zero? threads)
      (do
        (javadoc-package-process 0 job )
        (reduction-process job))
      (do 
        (future (reduction-process job))
        (dotimes [id threads] 
          (future (javadoc-package-process id job)))))
    (deref (final-result job))
    (if (completed-ok? job)
      (let [elapsed (/ (double (- (end-nsec job) (start-nsec job))) 1000000000)] 
        (message (format "complete: %.3f elapsed; %d packages, %d types, %d members"
                         elapsed (packages-in job) (types-in job) (members-in job))))
      (message "failed!"))
    (message-flush )
    job))  ; must be last!!!

#_ (* Extracts a javadoc tree, extracting index information as it goes, and
      deposits the results in a JSON-formatted file. 
      @p The output @name generates contains two top-level (root) indexes\: one 
      is an index of all of the types in the tree, and the other, an index 
      of all of the packages.
      @p @name can operate in a single-threaded mode (useful for debugging), 
      or multithreaded.
      
      @arg dest The name of the output file.
      @arg name A (short) name for the source tree\; used as the name for the 
      source object.
      @arg description A longer name or short description for the tree.
      @arg version A string notionally containing a version number of the source
      tree. 
      @arg pkg-terms A collection of term strings used for vocally specifying 
      the package index.
      @arg type-terms A collection of term strings used to vocally specify the 
      type index.
      @arg base-uri URI string that locates the Javadoc tree from which @name will
      extract index information.
      @arg remote-uri URI string for the location of the tree to be used
      when servicing requests for the tree's document content. This may be nil if the
      tree content will always be served locally.
      @option :threads If zero, operates in single-threaded mode. If nonzero,
      determines the number of extraction threads to run concurrently. Default value is 0.
      @option :members If true, extracts member information and constructs per-type 
      member indexes. Defaults to true. (Not currently implemented!!!)
      @option :vm If true, outputs a message describing each member encountered.
      Defaults to false.
      @option :vt If true, outputs a message describing each type 
      (class, interface, enum, annotation) encountered. Defaults to false.
      @option :vp If true, outputs a message describing each package encountered.
      Defaults to true.
      @returns A @(link JavadocJob) object describing the extraction job, and containing
      its results.
     )
(defn javadoc-extract [dest name description version 
                        pkg-terms type-terms base-uri remote-uri 
                        { :keys [threads members vm vt vp] } ]
  (let [out-stream (PrintStream. (File. dest))] 
    (binding [output-fn (fn [_ stuff] (.println out-stream stuff))
              externalizer (make-JSONExternalizer)]
      (let [job (javadoc-process name description version base-uri remote-uri  
                                 :threads threads :members members :vm vm :vt vt :vp vp)
            package-root 
            (new-JavadocRoot 
               (package-index-id-of job) (str name "-packages")
               (source-id-of job) (str description " packages"))
            type-root 
            (new-JavadocRoot 
               (type-index-id-of job) name (source-id-of job) description)]
        (zap package-root)
        (zap (new-RootEntry
               (Oid/oid) (str name "-packages") (str description " packages") 
               (source-id-of job) version (id-of package-root) pkg-terms))
        (zap type-root)
        (zap (new-RootEntry
               (Oid/oid) name description (source-id-of job) version 
               (id-of type-root) type-terms))
        (output-flush)
        (.close out-stream)))))

(defnk jdx [dest name description version 
                        pkg-terms type-terms base-uri remote-uri   
                        :threads 0 :members true :vm false :vt false :vp true]
  (javadoc-extract dest name description version pkg-terms type-terms base-uri remote-uri 
                   { :threads threads :members members :vm vm :vt vt :vp vp}))

#_(defn -main [& args]
  (let [[opts remaining] 
        (loop [[arg & remains+ :as remaining+] args
               opts+ { }]  ; <-- default options go here
          (let [[param & remains*] remains+
                [_ opt] (if arg (re-matches #"--(.*)" arg))
                [_ xopt attached] (if arg (re-matches #"-(\p{Alpha})(.*)" arg))]
            (cond 
              opt  ; multicharacter option, ala --title
              (condp = opt
               "all" (recur remains+ (assoc opts+ :all true))
                
                "add-css"
                (if param
                  (let [csss (vec (.split param ";"))]
                    (recur remains* (assoc opts+ :add-css csss)))
                  (throw (CJDException. "Missing parameter for --add-css")))
              
                "use-css"
                (if param
                  (let [csss (vec (.split param ";"))]
                    (recur remains* (assoc opts+ :use-css csss)))
                  (throw (CJDException. "Missing parameter for --use-css")))
              
                "docstrings" (recur remains+ (assoc opts+ :docstrings true))
                
                "exclude"
                (if param
                  (let [reqs (vec (.split param ";"))]
                    (recur remains* (assoc opts+ :exclude reqs)))
                  (throw (CJDException. "Missing parameter for --exclude")))
                
                "help" 
                (do
                  (cjd-help)
                  (System/exit 0))
              
                 "index"
                (if param
                  (recur remains* (assoc opts+ :index param))
                  (throw (CJDException. "Missing parameter for --index")))
                
                "nogen" (recur remains+ (assoc opts+ :nogen true))
                
                "noindex" (recur remains+ (assoc opts+ :no-index true))
                
                "overview"
                (if param
                  (recur remains* (assoc opts+ :overview param))
                  (throw (CJDException. "Missing parameter for --overview")))
                
                "requires"
                (if param
                  (let [reqs (vec (.split param ";"))]
                    (recur remains* (assoc opts+ :requires reqs)))
                  (throw (CJDException. "Missing parameter for --require")))
                
                "showopts" (recur remains+ (assoc opts+ :showopts true))
                
                "theme"
                (if param
                  (recur remains* (assoc opts+ :theme (keyword param)))
                  (throw (CJDException. "Missing parameter for --theme")))
                
                "throw" (recur remains+ (assoc opts+ :throw-on-warn true))
                
                "title" 
                (if param
                  (recur remains* (assoc opts+ :title param))
                  (throw (CJDException. "Missing parameter for --title")))
                
                "version"
                (do 
                  (println *cjd-version*)
                  (System/exit 0))
                
                "v"
                (let [vopts param #_(if attached attached param)
                      remains** remains* #_(if attached remaining+ remains*)]
                  (if (empty? vopts)
                    (throw (CJDException. "Missing parameter for -v"))
                    (recur remains** 
                           (assoc opts+ :v (set (map (fn [ch] (keyword (str ch))) vopts))))))
                 
                ; :else
                (throw (CJDException. (str "Unrecognized option: " opt))))
              
              #_xopt  ; single-character option, ala -v
              #_(condp = xopt
                "v"
                (let [vopts (if attached attached param)
                      remains** (if attached remaining+ remains*)]
                  (if (empty? vopts)
                    (throw (CJDException. "Missing parameter for -v"))
                    (recur remains** 
                           (assoc opts+ :v (set (map (fn [ch] (keyword (str ch))) vopts))))))
                
                ; :else
                (throw (CJDException. (str "Unrecognized option: " opt))))
              
              :else 
            [opts+ remaining+])))  ; <<--- EXIT
        
        [out-dir & files] remaining]
    #_(if-not out-dir 
      (throw (CJDException. "Missing output directory / input files")))
    #_(if (empty? files) 
      (throw (CJDException. "Missing input files")))
    (if (and out-dir  (not-empty files))
      (cjd-generator (vec files) out-dir opts)
      (cjd-help))))

