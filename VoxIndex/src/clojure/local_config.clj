#_ ( Copyright (c) 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* A set of configuration elements for a particular VoxIndex environment.
      )
(ns local-config
  (:use 
    [voxindex.server.service-environment]
    [indexterous.util.defnk]
    )
  (:require 
    [indexterous.javadoc.javadoc] 
    [voxindex.vshell.javadoc.javadoc-grammar] 
    [indexterous.android.android-doc]
    [voxindex.vshell.android.android-grammar]
    [voxindex.server.voxjet :as voxjet] 
    )
  )

(def jetty-install "C:\\Users\\hhgreen\\Libraries\\jetty-distribution-7.6.10.v20130312")

(def jetty-home-dir "./jetty")

(defn home-file [& paths] (apply str jetty-home-dir (interleave (repeat "/") paths )))

(def local-contexts 
  (local-context-map
    ["urn:local:gwt-2.6.1" "/content/gwt-2.6.1" "C:\\Users\\hhgreen\\Libraries\\gwt-2.6.1\\doc\\javadoc"]
    ["urn:local:jdk-1.7u17" "/content/jdk-1.7u17" "C:\\Users\\hhgreen\\Libraries\\jdk-7u17-apidocs\\docs\\api"]
    ["urn:local:sphinx4" "/content/sphinx4" "C:\\Program Files (Noninstalled)\\sphinx4-1.0beta6\\javadoc"]
    ["urn:local:android-4.2.1" "/content/android" "C:\\Users\\hhgreen\\AppData\\Local\\Android\\android-sdk\\docs"]
    ))

(def use-contexts 
  {
   "/vox-front" "C:\\Users\\hhgreen\\Projects\\Indexterous-7\\VoxFront\\war"
   "/osc" "C:\\Users\\hhgreen\\Projects\\Indexterous-7\\Osc\\war"
   "/poo" "C:\\Users\\hhgreen\\Projects\\Indexterous-7\\Poo\\war"
   "/voxindex.VoxIndex" "C:\\Users\\hhgreen\\Projects\\Indexterous-7\\VoxIndex\\war\\voxindex.VoxIndex"
   })


(defn- setprops [] 
  (do
    (System/setProperty "jetty.home" jetty-home-dir )
    (System/setProperty "START" (home-file "/start.config") )
    (System/setProperty "install.jetty.home" jetty-install )
    (System/setProperty "VERBOSE" "true")
    (System/setProperty "STOP.PORT" "8082")
    (System/setProperty "STOP.KEY" "secret")))


(defnk local-environment [:port 8080 :host "192.168.1.101" :ssl-port 8443 
                          :serve-from "/content" :context-map local-contexts 
                          :use-map use-contexts]
  (setprops) 
  (make-ServiceEnvironment host port ssl-port serve-from context-map use-map))

(defn start [] (voxjet/start-server (local-environment)))
(defn stop [] (voxjet/stop-server))

