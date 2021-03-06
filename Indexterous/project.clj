
#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Provides a modestly brain-damaged but workable storage back end for VoxIndex. 
      
      
      )


(defproject voxindex/indexterous "0.0.0-SNAPSHOT"
  :description "Indexterous"
  :url "https://github.com/greenh/Indexterous"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure and lots of other fun things"}
  :target-path "bin/"
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"] 
  :test-paths ["test"]
  :compile-path "classes"
  :aot [#"indexterous\..*"] 
  :dependencies [
    [org.clojure/clojure "1.5.1"]
    [cheshire "5.1.1"] 
    [org.mongodb/mongo-java-driver "2.11.1"] 
    [extensomatic "0.2.0"] 
    ]
  :plugins [
    [no-man-is-an-island/lein-eclipse "2.0.0"]
    [lein-clojars "0.9.1"]
    [cjd "0.1.0"]
    ]
  :omit-source true
  :jar-exclusions [#"cjd_doc.*"]
  
  :cjd-source "src/clojure"
  :cjd-dest "doc/dark"
  :cjd-opts { :theme :dark :exclude [#"src/clojure/indexterous_cjd.*"]
             :title "Indexterous" :v #{:f :n} :overview "project.clj"
             ; :requires "indexterous-cjd.indexterous-cjd" 
             }
  )


