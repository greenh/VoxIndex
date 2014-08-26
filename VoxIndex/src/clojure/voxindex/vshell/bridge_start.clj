#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Provides mechanisms for firing up the jni4net bridge that allows the JVM to 
      access the limited set of .Net functionality used for speech recognition.
     )
(ns voxindex.vshell.bridge-start
  (:import 
    [net.sf.jni4net Bridge]
    [java.io File]))

(defonce ^:dynamic *bridge-started* (ref false))
(def proxydll "proxygen.dll")
(def goopdll "Goop.dll")

(defn start-bridge []
  (if-not @*bridge-started*
    (try 
      (do
        (Bridge/init)
        (Bridge/LoadAndRegisterAssemblyFrom (File. proxydll))
        (Bridge/LoadAndRegisterAssemblyFrom (File. goopdll))
        (dosync (ref-set *bridge-started* true)))
      (catch Throwable t 
        (println "Error initializing jni4net:")
        (.printStackTrace t)))))