#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Provides a servlet that returns pages describing the contents of indexes.
     @p N.B., this is somewhere between speculation and fantasy.  
     )
(ns voxindex.server.index-servlet
  (:import 
    [javax.servlet.http HttpServlet HttpServletResponse HttpServletRequest]
    )
  (:use
    [voxindex.server.vox-server-params]
    [voxindex.server.vox-log]
    [indexterous.util.string-utils]
    [indexterous.config.assigned-ids]
    [indexterous.index.db]
    [indexterous.index.index]
    [hiccup.core]
    [hiccup.def]
    )
  )

(def ^{:private true} log-id "index-servlet")

(defn param-map [query]
  (if (empty? query)
    { }
    (reduce 
      (fn [m+ [_ attr val]]  (assoc m+ (keyword attr) val))
      {} 
      (re-seq #"([^=;]+)=([^;]+)" query))))


(defproxy IndexServlet HttpServlet [] []
    (doGet [req resp]
      (let [servlet this
            params (param-map (.getQueryString req))
            ; _ (println "Query:" (enquote (.getQueryString req)))
            index-ids (if-let [iids (:index params)] 
                        (filter #(not (preassigned-index-id? %))
                          (map second (re-seq #",*([^:,]+)(?:\:[^,\:]+)*" iids)))
                        nil)]
        (log-info log-id "Index retrieval for " 
                  (if (empty? index-ids)
                    " root entries"
                    (apply str (interpose ", " index-ids))) )
        (if (empty? index-ids)
          ;;; no index IDs --- generate a listing of all the available indexes 
          (let [db (get-db servlet)
                root-entries (fetch-all db (root-collection db))
                goop
                (html5 { }
                  (html 
                    [:head (include-css "VoxIndex.css")
                     [:title "Index of indexes"]]
                    [:body
                     [:div 
                      [:span.index "Index of indexes"]
                      [:table.tt { :cellspacing 0 :style "width:100%"}
                       [:colgroup [:col.c1] [:col.c2]]
                       [:tbody
                        (first
                          (reduce 
                            (fn [[v+ n+] root-index]
                              (let [terms (root-terms-of root-index)
                                    desc (description-of root-index)] 
                                [(str v+
                                   (html 
                                     (if (odd? n+) 
                                       [:tr.odd [:td desc] [:td.t (first terms)]]
                                       [:tr.even [:td desc] [:td.t (first terms)]]))
                                   (html
                                     (for [term (rest terms)]
                                       (if (odd? n+) 
                                         [:tr.odd [:td ""] [:td.t term]]
                                         [:tr.even [:td ""] [:td.t term]]))))
                                 (inc n+)]))
                            ["" 0]
                            (sort-by description-of root-entries)))]]]
                     ]))

                writer (.getWriter resp)]
            ; (log-info log-id "fetched " (count root-entries) " root entries")
            (doto resp
              (.setContentType "text/html")
              (.setStatus HttpServletResponse/SC_OK)
              )
            (.println writer goop)
            (.close writer))
          
          ;;; index IDs were supplied --- generate a listing of their contents
          (let [db (get-db servlet)
                indexes (fetch db (index-collection db) 
                                { "_id" { "$in" (vec (map oid index-ids))}})
                goop 
                (html5 { }
                  (html 
                    [:head (include-css "VoxIndex.css")
                     [:title "Active indexes"]] 
                    [:body 
                     (for [index indexes]  
                         [:div 
                          [:span.index (str (description-of index) )]
                          (for [[category prefixes entries] (specs-of index)]
                            [:div
                             [:div.spec
                              (if (some not-empty prefixes)
                                (html 
                                  [:table { :cellspacing 0 :style "width:100%" }
                                   [:tbody 
                                    [:tr 
                                     [:td category] 
                                     [:td.pref (apply str (interpose 
                                                       (str (html [:i.or "or"]) "&ensp;") 
                                                       (map #(html [:span.t (str % "...&ensp;")]) 
                                                            prefixes)))]]]])
                                [:span category] )]
                             [:table.tt { :cellspacing 0 :style "width:100%"}
                              [:colgroup [:col.c1] [:col.c2]]
                              [:tbody 
                               (first 
                                 (reduce 
                                   (fn [[v+ n+] [source-term indexable terms]]
                                     [(str v+
                                           (html 
                                             (if (odd? n+) 
                                               [:tr.odd [:td.s source-term] [:td.t (first terms)]]
                                               [:tr.even [:td.s source-term] [:td.t (first terms)]]))
                                           (html
                                             (for [term (rest terms)]
                                               (if (odd? n+) 
                                                 [:tr.odd [:td ""] [:td.t term]]
                                                 [:tr.even [:td ""] [:td.t term]]))))
                                      (inc n+)])
                                   ["" 0]
                                   (sort-by entry-source-term-of entries)))
                               ]]])])]))
                writer (.getWriter resp)]
            ; (log-info log-id "fetched " (count indexes) " indexes")
            (doto resp
              (.setContentType "text/html")
              (.setStatus HttpServletResponse/SC_OK)
              )
            (.println writer goop)
            (.close writer)
            
            ))))
    )

