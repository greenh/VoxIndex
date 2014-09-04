#_ ( Copyright (c) 2011, 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Set of parameters and a few simple functions for running the android scraper
     in a (MY!) local environment.
     @p Beats having to type everything in every time!
     )
(ns a-sources
   (:use 
    [extensomatic.extensomatic]
    [indexterous.android.user.scraper] 
    [indexterous.util.defnk]
    )
   (:require 
    [indexterous.exintern.json :as json]
    )
   )

(defconstructo A-spec [] 
  [name description version pkg-terms type-terms source-uri service-uri]
  { :new-sym new-Aspec }
  )

(def an-sources
  {:an 
   (new-Aspec 
     "android-4.1.2" "Android API 4.1.2" "4.1.2"
     ["android packages" ]
     ["android" "android classes" "android 4 1 2"]
     "C:\\Users\\hhgreen\\AppData\\Local\\Android\\android-sdk\\docs"
     "urn:local:android-4.2.1")
   })


(defnk runsource [id :threads 0 :members true :vm false :vt false :vp true
                        :include nil :exclude nil]
  (let [src (get an-sources id)]
    (indexterous.android.user.scraper/scrape 
      (str "out/" (.name src) ".json")
      (.name src)
      (.description src)
      (.version src)
      (.pkg-terms src)
      (.type-terms src)
      (.source-uri src)
      (.service-uri src)
      { :threads threads :members members :vm vm :vt vt :vp vp
                    :include include :exclude exclude }
      )
    ))

(defnk runsources [:threads 0 :members true :vm false :vt false :vp true
                        :include nil :exclude nil]
  (doseq [[id src] an-sources]
    (runsource id :threads threads :members members :vm vm :vt vt :vp vp
                  :include include :exclude exclude)))

(defn dbload [db-id id opts]
  (let [src (get an-sources id)]
    (json/dbload db-id (str "out/" (.name src) ".json") opts)))

(defn dbload-all [db-id opts]
  (doseq [[id src] an-sources]
    (println (str "Loading " (.description src) " from " "out/" (.name src) ".json"))
    (dbload db-id id opts)))

