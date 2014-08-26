(ns voxindex.vshell.grammar-html
  (:use
    [hiccup.core]
    [hiccup.def])
  )

(defhtml gi-block [& stuff] [:div.gi-block stuff])

(defhtml gi-head [& stuff] [:div.gi-head stuff])
(defhtml gi-name [& stuff]  [:span.gi-name (apply str stuff)])

(defhtml gi-voc-item [& stuff] [:div.gi-voc-item stuff])
(defhtml gi-term [& stuff]  [:span.gi-term (apply str stuff)])
(defn gi-terms [& items] (interpose " or " (map gi-term items)))

(defn gi-term-list [prefix & terms] 
  (gi-voc-item 
    (apply gi-terms 
           (map (fn [x] (str prefix (if-not (empty? x) " ") x)) terms))))

(defhtml gi-non-term [& stuff]  [:span.gi-non-term (apply str stuff)])

