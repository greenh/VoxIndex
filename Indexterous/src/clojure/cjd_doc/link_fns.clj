#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Additional link resolvers for CJD's use. 
      )
(ns cjd-doc.link-fns
  (:use 
    [cjd.link-resolver]))

(add-external-resolvers
  (fn [ns sym]
    (if (or (re-matches #"org\.bson\..*" (name ns)) 
            (re-matches #"com\.mongodb\..*" (name ns)))
      (let [cls (.replaceAll (name ns) "\\." "/")]
        (str "http://api.mongodb.org/java/2.7.0/" cls ".html" 
             (if sym (str "/" (name sym)))))))
  (fn [ns sym]
      (if (re-matches #"org\.eclipse\.jetty\..*" (name ns))
        (let [cls- (.replaceAll (name ns) "\\." "/")
              cls (.replaceAll cls- "\\$" ".")]
          (str "http://download.eclipse.org/jetty/stable-7/apidocs/" cls ".html" 
               (if sym (str "/" (name sym))))))))
