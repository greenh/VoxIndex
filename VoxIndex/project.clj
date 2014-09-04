
#_ ( Copyright (c) 2012 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* VoxIndex provides services for speech recognition-based lookup of
      terms over modestly sized vocabularies that are typically derived from
      
      that ) 
(defproject voxindex "0.0.0-SNAPSHOT"
  :description "VoxIndex"
  :url "https://github.com/greenh/VoxIndex"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure and lots of other fun things"}
  :target-path "bin/"
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"] 
  :test-paths ["test"]
  :compile-path "classes"
  :dependencies [
    [org.clojure/clojure "1.6.0"]
    [extensomatic "0.2.0"] 
    [voxindex/indexterous "0.0.0-SNAPSHOT"]
    [voxindex/indexterous-javadoc "0.0.0-SNAPSHOT"] 

    [org.mongodb/mongo-java-driver "2.11.1"] 
    [com.google.gwt/gwt-user "2.6.1"] 
    [com.google.gwt/gwt-servlet "2.6.1"] 
    [com.google.gwt/gwt-elemental "2.6.1"]
    
    [hiccup "1.0.3"]
    [cheshire "5.1.1"]
    [commons-lang "2.6"]
    [org.eclipse.jetty/jetty-websocket "7.6.10.v20130312"] 
    [org.eclipse.jetty/jetty-servlet "7.6.10.v20130312"] 
    
    [org.mindrot/jbcrypt "0.3m"]

    ]
  :plugins [
    [org.apache.maven/maven-ant-tasks "2.1.3"]
    [no-man-is-an-island/lein-eclipse "2.0.0"]
    [lein-clojars "0.9.1"]
    [cjd "0.1.0"]
    ]
  :cjd-source "src/clojure"
  :cjd-dest "doc/dark"
  :cjd-opts { :theme :dark :exclude [#"src/clojure/cjd-doc.*"]
             :title "VoxIndex" :v #{:f :n} :overview "project.clj"
             ; :requires "cjd-doc.req" 
             }
  )