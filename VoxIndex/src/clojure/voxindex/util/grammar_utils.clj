#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Functions for constructing .NET-style grammars.
     )
(ns voxindex.util.grammar-utils
  (:import
    [system.speech.recognition 
     Choices
     Grammar
     GrammarBuilder
     SemanticResultKey
     SemanticResultValue
     ]
    [goop Speechify]
    )
  )

(defn srv-int [key val] (GrammarBuilder. (Speechify/getSrv key (int val))))
(defn srv-string [key val] (GrammarBuilder. (Speechify/getSrv key (str val))))
(defn srv-float [key val] (GrammarBuilder. (Speechify/getSrv key (double val))))
(defn srv-boolean [key val] (GrammarBuilder. (Speechify/getSrv key (boolean val))))

(defmacro make-gb [& items] 
  `(reduce 
    (fn [^GrammarBuilder gb# gb-item#] (.Append gb# gb-item#) gb#)
    (GrammarBuilder.)
    [~@items]))

(defn make-cv [phrase key & appends]
  (let [cv (srv-string phrase (str key))]
    (loop [[app & more] appends]
      (if app
        (do
          (. cv GrammarBuilder/Append app)
          (recur more))
        cv))))

(defmacro make-gb-array [& gb-items]
  `(into-array GrammarBuilder [~@gb-items]))

(defmacro make-choices [& choices]
  `(.ToGrammarBuilder (Choices. (make-gb-array ~@choices))))

(defmacro make-sem-res-key [^String key & gbs]
  `(GrammarBuilder. (SemanticResultKey. (str ~key) (make-gb-array ~@gbs))))

(defn gb-append [gb & whats]
  (reduce 
    (fn [gb what] (.Append gb what) gb)
    gb whats))

(defn gb-set-name [gb name]
  (.setName gb name)
  gb)

(defn add-choice [^Choices choices choice]
  (.Add choices choice)
  choices)

