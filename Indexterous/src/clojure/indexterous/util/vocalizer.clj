#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Functions for converting words -- and particularly programming-language 
      identifiers -- into sequences of vocalizable elements. )
(ns indexterous.util.vocalizer
  (:use
    [indexterous.util.string-utils]
    )
  )

(defn stringify
  "Turns a _list_ of symbols into a list of strings. Thus:
(stringify '(a b c)) => (\"a\" \"b\" \"c\") "
  [items]
  (loop [[item & more] items
           strs nil]
      (if item
        (recur
          more
          (if (string? item) item (cons (str item) strs))
          ;(str item)
          )
        (vec (reverse strs)))))

(defn restring [toks]
  (apply str (interleave toks (repeat " "))))

(defmacro tmod [target op & reps]
  (if (= op '->)
    (let [t (re-pattern (str "(?i) " target " "))
          rs (str " " (restring (stringify reps)) )]
      `[~t ~rs])
    (throw (IllegalArgumentException. "Missing -> in token-mod"))))

(defmacro token-rep-map [& mods]
  (let [xmods (map (fn [mod] (cons 'tmod mod)) mods)]
    `(reduce (fn [acc# [target# reps#]] (conj acc# [target# reps#])) [] [~@xmods])))


(defmacro prefmod [target op & reps]
  (if (= op '->)
    (let [t (re-pattern (str "(?i) " target))
          rs (str " " (restring (stringify reps)) " ")]
      `[~t ~rs])
    (throw (IllegalArgumentException. (str "Missing -> in prefix-rep: " )))))

(defmacro prefix-rep-list [& mods]
  (let [xmods (map (fn [mod] (cons 'prefmod mod)) mods)]
    `(reduce (fn [acc# [target# reps#]] (conj acc# [target# reps#])) [] [~@xmods])))

(load "/indexterous/config/vocalization_maps")

;(def token-mods (token-rep-map token-modifications))

;(def prefix-mods (prefix-rep-list prefix-modifications))

(defn flat-map [func coll] 
  (let [x (flatten (map func coll))]
    ;(println "---" x)
    x))

(defmacro whiz [wfn targets] 
  (let [p (gensym) wfn* (concat wfn (list p))]
    `(map (fn [~p] ~wfn*) ~targets)))

(defmacro fwhiz [wfn targets] 
  (let [p (gensym) wfn* (concat wfn (list p))]
    `(flatten (map (fn [~p] ~wfn*) ~targets))))


#_ (* Simple-minded generator for vocal representations of programming-language
      identifiers.
      @p Accepts a string which notionally represents a Java (or similar) identifier
      and uses it to generate a collection of strings that are plausible 
      vocalized representations of the identifier.
     
      @p Sequence of operations\:
      @(ul
         @li ?
         @li "xxx" -> " xxx ". We're fundamentally generating a string of tokens
         here, where the tokens are separated by spaces. This ensures that tests
         for tokens beginning and ending work across the entire string, and we 
         don't have to special-case start and end of string.
         @li "xxx.yyy" -> ["xx yy" "xx dot yy"]
         @li "xxxYyyyZzzz" -> "xxx Yyyy Zzzz"
         @li "aaaa9876" -> "aaaa 9876"
         @li "1234zzzz" -> "1234 zzzz"
         @li " _xxx" and "xxx_ " -> "underscore xxx" and "xxx underscore", respectively.
         We only pronounce underscores at the start and end of IDs.
         @li "xxx?yyy" -> "xxx yyy", where "?" is any of "_(),". Note that 
         underscores in the middle are just separators.
         @li "X9X9_ZZZ" -> "X9X9 ZZZ". This deals with upper-cased IDs that have words
         separated by underscores.
         @li "1234" -> ["1 2 3 4" "1234"]. Digits individually, or as a number.
         @li " 0 " -> [" 0 ", " o "]. Zero as "zero" or "o".
         @li Replace tokens prefixes from the @(link prefix-mods) list with their modified strings.
         @li Replace entire tokens with replacements from the @(link token-mods) list.
         @li "XYZ" -> "X Y Z". This deals with any remaining strings of upcased lettes, notably
         abbreviations.
         @li Condense multiple spaces to a single space.
         @li Remove spaces at the beginning
         @li Remove spaces at the end
         @li Lower-case everything.
        )
     

;      @(session
;         (id-vocalizer "JavaIdentifier")
;         ["java identifier"] 
;         (id-vocalizer "javax.thing.JavaIdentifier")
;         ["java x dot thing dot java identifier" "java x thing java identifier"] 
;         )
      @arg term A term to be "vocalized".
      @returns A collection of strings representing alternative possible
      vocalizations for @(arg term).
      )
(defn id-vocalizer [^String term]
  (->> 
    term
    ((fn [string] 
      (if (re-matches #"[\p{Upper}\d_]+" string)
        (.toLowerCase string)
        string)))
    ((fn [string] (str " " string " ")))
    ((fn [string] 
       (if (re-find #"\." string)
         [(re-replace-all #"\." " dot " string) (re-replace-all #"\." " " string)]
         [string])))
    (whiz (re-replace-all #"(\p{Lower})(\p{Upper})" "$1 $2"))
    (whiz (re-replace-all #"(\p{Alpha})(\p{Digit})" "$1 $2"))
    (whiz (re-replace-all #"(\p{Digit})(\p{Alpha})" "$1 $2"))
    (whiz (re-replace-all #" _|_ " " underscore "))
    (whiz (re-replace-all #"[_(),]" " "))
    (whiz (re-repeat #" ([\p{Upper}\d_]+)_" "$1 "))
    (fwhiz 
      ((fn [string]
         (if (re-find #"\d{2,}" string)
           [string (re-repeat #"(\d)(\d)" "$1 $2" string)]
           string))))
    (fwhiz 
      ((fn [string]
         (if (re-find #" 0 " string)
           [string (re-replace-all #" 0 " " o " string)]
           string))))
    (whiz 
      ((fn [string]
         (reduce (fn [s [pref mod]] (re-replace-all pref mod s)) string prefix-mods ))))
    (whiz 
      ((fn [string]
         (reduce (fn [s [tok mod]] (re-replace-all tok mod s)) string token-mods ))))
    (whiz (re-repeat #"(\p{Upper})(\p{Upper})" "$1 $2"))
    (whiz (re-replace-all #"  " " "))
    (whiz (re-replace-first #"^ +" ""))
    (whiz (re-replace-first #" +$" ""))
    (whiz (.toLowerCase))
    ))


(defn test-voc [strings]
  (doseq [term strings]
    (loop [[ch & more] (id-vocalizer term)]
      (if ch
        (do 
          (println term "->" ch) 
          (recur more))))))

(def vox 
  [
   "AbstractAnnotationValueVisitor6"
   "AclNotFoundException"
   "ArrayList"
   "BorderUIResource.EmptyBorderUIResource"
   "DefaultTableCellRenderer.UIResource"
   "DefaultTextUI"
   "GSSCredential"
   "GZIPInputStream"
   "HOLDING"
   "HTMLFrameHyperlinkEvent"
   "JFormattedTextField.AbstractFormatterFactory"
   "JMXConnectorServerProvider"
   "JobKOctets" 
   "JobKOctetsProcessed"
   "JPEGHuffmanTable"
   "JSpinner.NumberEditor"
   "LayoutManager2"
   "LIFESPAN_POLICY"
   "SOAPBodyElement"
   "SQLNonTransientConnectionException"
   "SQLXML"
   "SSLContextSpi"
   "SslRMIClientSocketFactory"
   "URIReferenceException"
   "W3CEndpointReferenceBuilder"
   "X500PrivateCredential"
   "X509CRL"
   "X509CRLEntry"
   "X509CRLSelector"
   "XADataSource"
   "XPathFilter2ParameterSpec"
   "XSLTTransformParameterSpec"
   "_IDLTypeStub"
         ])

(def html-entity-directs 
  #{ "and" "or" 
     "alpha" "beta" "gamma" "delta" "epsilon" "zeta" "eta" "theta" "iota" "kappa"
     "lambda" "mu" "nu" "xi" "omicron" "pi" "rho" "sigma" "tau" "phi" "upsilon" 
     "chi" "psi" "omega" 
    })

(def html-entity-map 
  {
   "exists" "there exists"
   "forall" "for all"
   "harr" "left right arrow"
   "rarr" "right arrow"
   "larr" "left arrow"
   "perp" "perpendicular"
   "auml" "a"
   "euml" "e" 
   "iuml" "i"
   "ouml" "o" 
   "uuml" "u"
   })


#_ (* Simple-minded phrase vocal-representation generator.
      @p @name accepts a string (which may contain pertinent HTML tags) representing a 
      word or phrase such as one might get from a document table of contents or index,
      and uses it to generate a collection of strings that are plausible 
      vocalized representations of the identifier.
     
      @p Sequence of operations\:
      @(ul
         @li "xxx" -> " xxx ". We're fundamentally generating a string of tokens
         here, where the tokens are separated by spaces. This ensures that tests
         for token beginning and ending work across the entire string, and we 
         don't have to special-case start and end of string.
         @li "<whatever>" -> "". Remove HTML tags.
         @li Translate (a few, select) HTML entities to pronounceable names.
         @li "Xyszds, Z." -> "Xyszds". Viciously delete people's first initials.
         @li "xxx*yyy" -> "xxx star yyy".  
         @li "xxxYyyyZzzz" -> "xxx Yyyy Zzzz"
         @li "aaaa9876" -> "aaaa 9876"
         @li "1234zzzz" -> "1234 zzzz"
         @li "xxx?yyy" -> "xxxyyy", where "?" is any of "'"
         @li "xxx(yyy)" -> "xxx of yyy"
         @li "xxx?yyy" -> "xxx yyy", where "?" is any of "-_,.". 
         @li "1234" -> ["1 2 3 4" "1234"]. Digits individually, or as a number.
         @li " 0 " -> [" 0 ", " o "]. Zero as "zero" or "o".
         @li Replace tokens prefixes from the @(link prefix-mods) list with their modified strings.
         @li Replace entire tokens with replacements from the @(link token-mods) list.
         @li "XYZ" -> "X Y Z"
         @li Condense multiple spaces to a single space.
         @li Remove spaces at the beginning
         @li Remove spaces at the end
         @li Lower-case everything.
        )
      @arg term A term to be "vocalized".
      @returns A collection of strings representing alternative possible
      vocalizations for @(arg term).
      )
(defn ix-vocalizer [^String term]
  (->> 
    term
    ((fn [string] (str " " string " ")))
    (re-replace-all #"<sup>2" " squared ")   ; ugh! :)
    (re-replace-all #"<sup>" " super ")
    (re-replace-all #"<sub>" " sub ")
    (re-replace-all #"<[^>]*>" "")   ; zap HTML tags
    ((fn [string0]    ; substitute known HTML entities
       (loop [string string0]
         (let [[_ start ent end] (re-matches #"([^\&]*)\&([^;]+);(.*)" string)]
           (if ent
              (recur (str start
                          (if (get html-entity-directs (.toLowerCase ent)) 
                            ent
                            (if-let [rep (get html-entity-map ent)]
                              rep
                              ent))
                          end))
              string)))))
    (re-replace-all #"([(\p{Alpha} ]+),(?: \p{Alpha}\.)+" "$1")  ; author last names only
    (re-replace-all #"\*" " star ")  ; "star" for "*"
    (re-replace-all #"(\p{Lower})(\p{Upper})" "$1 $2")
    (re-replace-all #"(\p{Alpha})(\p{Digit})" "$1 $2")
    (re-replace-all #"(\p{Digit})(\p{Alpha})" "$1 $2")
    (re-replace-all #"\'" "")    ; goodbye, possessives
    (re-replace-all #"(\p{Alpha})\(([^\)]+)\)" "$1 of $2")
    (re-replace-all #"[\.\-\|\~_(),]" " ")
    list
    (fwhiz 
      ((fn [string]
         (if (re-find #"\d{2,}" string)
           [string (re-repeat #"(\d)(\d)" "$1 $2" string)]
           string))))
    (fwhiz 
      ((fn [string]
         (if (re-find #" 0 " string)
           [string (re-replace-all #" 0 " " o " string)]
           string))))
    (whiz 
      ((fn [string]
          (reduce (fn [s [tok mod]] (re-replace-all tok mod s)) string token-mods ))))
    (whiz (re-repeat #"(\p{Upper})(\p{Upper})" "$1 $2"))
    (whiz (re-replace-all #"  " " "))
    (whiz (re-replace-first #"^ +" ""))
    (whiz (re-replace-first #" +$" ""))
    (whiz (.toLowerCase))))

(def ix-strings [
  "&chi;<sup>2</sup>"
  "de Finetti's theorem"
  "<i>k</i>-DL"
  "AND-OR graph"
  "domain"
  "AC-3"
  "<i>A(s)</i>"
  "<span style=\"font-variant: small-caps\">and-or</span> tree"
  "DPLL"
  "&epsilon;-ball"
  "<i>b*</i>"
  "IDA* search"
  ]
  )

(defn ix-test [strings]
  (doseq [term strings]
    (println (enquote term) "->" (map enquote (ix-vocalizer term)))))

