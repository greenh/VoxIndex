(ns a-sources
   (:use 
    [extensomatic.extensomatic]
    [indexterous.android.client.scraper] 
    [indexterous.util.defnk]
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
    (indexterous.android.client.scraper/scrape 
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