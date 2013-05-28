#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Facilities for defining @(i exins), which are data structures that incorporate 
      support for externalization and internalization in various forms.
      @p This is an extension to the @(lt https://github.com/greenh/Extensomatic Extensomatic)
      data-structure definition package. 
      )
(ns indexterous.index.exin
  (:import
    [indexterous.util ParseException]
    )
  (:use 
    [indexterous.exintern.exintern-base]
    [extensomatic.extensomatic]
    [indexterous.util.string-utils]
    [clojure.pprint])
  )

(def ^:dynamic *-exin-debug-print* false)

(defn exin-debug [tf] (def ^:dynamic *-exin-debug-print* tf) tf)

(defn- -xln [& stuff]  (if *-exin-debug-print* (apply println stuff)) )
(defn- -xpp [caption form] (if *-exin-debug-print* (do (println caption) (pprint form))))

(defn- error [msg & why-bits]
  (throw (ParseException. (apply str msg (if-not (empty? why-bits) 
                                         (enquote (maxstr 80 why-bits)))))))


(defmacro defexin [exin-name id-fn &para-extensos &local-fields & local-methods]
  (if-not (symbol? exin-name) 
    (error "defexin: Extenso name must be a symbol: " exin-name))
  (if-not (vector? &para-extensos)
    (error "defexin: &para-extensos must be a vector: " &para-extensos))
  (if-not (vector? &local-fields)
    (error "defexin: " (enquote exin-name) "&local-fields must be a vector: " &local-fields))
  (let [[& para-extensos] &para-extensos
        [& local-fields] &local-fields]
    (let [[init-fields uninit-fields]
          (extenso-field-munger 'defexin para-extensos local-fields)
          
          field-names 
          (concat (map first init-fields) uninit-fields)
          
          extern-decs 
          (list 
            `Externalizable
            `(externalize [~'this ~'externalizer] 
               (externalize-fields ~'externalizer (~id-fn ~exin-name) ~@field-names))) 
;         #_(list 'externalize ['this 'externalizer] 
;                (list 'externalize-fields 'externalizer 
;                      (concat (list 'type-uri exin-name) field-names)))
          _ (-xpp (str ";;;;;;;;;;; exin " exin-name " extern-decs ;;;;;;;;;;;") 
                  extern-decs)

          constructo
          `(defconstructo ~exin-name ~&para-extensos ~&local-fields
             { :new-prefix "new-" }
             ~@local-methods
             ~@extern-decs)
        
          _ (-xpp (str ";;;;;;;;;;; exin " exin-name " constructo ;;;;;;;;;;;") 
                  constructo)
        
          intern-method 
          `(defmethod type-internalize (~id-fn ~exin-name) [~'ext ~'internalizer] 
             (internalize-fields ~'internalizer ~'ext 
                                 (new ~exin-name ~@field-names)
                                 ~@field-names))
        
          _ (-xpp (str ";;;;;;;;;;; exin " exin-name " intern-method ;;;;;;;;;;;") 
                  intern-method)
          
          pr-method
          `(defmethod clojure.core/print-method 
             ~exin-name [o# w#] (.write w# (.toString o#)))

          ]
      `(do
         ~constructo
         
         ~intern-method
         
         ~pr-method)))
  )