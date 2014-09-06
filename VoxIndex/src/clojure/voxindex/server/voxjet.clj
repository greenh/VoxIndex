#_ ( Copyright (c) 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* VoxIndex server based on Jetty.
     )
(ns voxindex.server.voxjet
  (:use
    [voxindex.server.gwt-servlet ]
    [voxindex.server.vox-server-params]
    [voxindex.server.service-environment]
;    [voxindex.server.index-servlet]
    [voxindex.server.vox-log]
    [indexterous.util.defnk]
    )
  (:import
    [java.io File FileWriter]
    [org.eclipse.jetty.server Handler Server NCSARequestLog ]
    [org.eclipse.jetty.server.handler 
     ContextHandlerCollection DefaultHandler ContextHandler
     HandlerCollection RequestLogHandler ResourceHandler]
    [org.eclipse.jetty.servlet 
     ServletHolder ServletContextHandler ServletHandler DefaultServlet]
    [org.eclipse.jetty.server.nio SelectChannelConnector]
    [org.eclipse.jetty.server.ssl SslSelectChannelConnector]
    [org.eclipse.jetty.util.component AbstractLifeCycle$AbstractLifeCycleListener]
    [org.eclipse.jetty.util.ssl SslContextFactory]
    [org.eclipse.jetty.util.thread QueuedThreadPool]
    )
  (:require 
    [voxindex.server.infrastructure :as infrastructure])
  )

(def ^{:private true} log-id "VoxJet")

;; This is stuff that is in the process of being moved to local-config
(def war-dir "./war/")

(def home-dir "./jetty")

(defn in-home [& paths] (apply str home-dir (interleave (repeat "/") paths )))

#_ (* Construct a VoxIndex server by configuring a simple-minded Jetty server.
      @arg env An @(il LocalEnvironment) object describing the server environment.
      @returns The Jetty server object.
      )
(defn make-server [env]
  (let 
    [thread-pool 
     (doto (QueuedThreadPool.) (.setMinThreads 10) (.setMaxThreads 50))
     conn 
     (doto (SelectChannelConnector.)
       (.setHost nil)
       (.setPort (service-port env))
       (.setMaxIdleTime 300000)
       (.setAcceptors 2)
       (.setStatsOn false)
       (.setLowResourcesConnections 20000)
       (.setLowResourcesMaxIdleTime 5000))
     
     ssl-conn
     (doto (SslSelectChannelConnector.
             (doto (SslContextFactory.)
               (.setKeyStorePath "./ssl/keystore")
               (.setKeyStorePassword "OBF:1u9x1vn61z0p1yta1ytc1z051vnw1u9l")
               (.setKeyManagerPassword "OBF:1v2j1uum1xtv1zej1zer1xtn1uvk1v1v")
               (.setTrustStore "./ssl/keystore")
               (.setTrustStorePassword "OBF:1u9x1vn61z0p1yta1ytc1z051vnw1u9l")
               ))
       (.setPort (service-secure-port env))
       (.setMaxIdleTime 300000))
     
     default-handler 
     (doto (DefaultHandler.)
       (.setServeIcon false))
     
     logger 
     (doto (RequestLogHandler.) 
       (.setRequestLog 
         (doto (NCSARequestLog. "./jetty/logs/jetty-yyyy_mm_dd.request.log")
           (.setAppend true)
           (.setRetainDays 90)
           (.setExtended true))))
     
     contexts (ContextHandlerCollection.)
     
     handlers 
     (doto (HandlerCollection.) 
       (.setHandlers (into-array Handler [contexts default-handler #_logger])))
     
     voxindex-servlet 
     (doto (ServletHolder. (make-VoxIndexServlet env)))

     srv-con-handler
     (doto (ServletContextHandler.)
       (.setResourceBase war-dir)
       (.setContextPath "/vox-index")
       (.addEventListener (make-VoxIndexListener))
       (.addServlet voxindex-servlet "/voxindex.VoxIndex/VoxLookup")
;       (.addServlet (doto (ServletHolder. (make-IndexServlet))) "/index-service")
       (.addServlet (ServletHolder. (DefaultServlet.)) "/")
       ; (.setInitParameter *db-name-param* "Myself")
       )
     
     server 
     (doto (Server.)
       (.setThreadPool thread-pool)
       (.addConnector conn)
       (.addConnector ssl-conn)
       (.setHandler handlers)
       (.setStopAtShutdown true)
       (.setSendServerVersion true)
       (.setSendDateHeader true)
       (.setGracefulShutdown 1000)
       (.addLifeCycleListener
         (proxy [AbstractLifeCycle$AbstractLifeCycleListener] []
          (lifeCycleStopping [event] (infrastructure/clear-sessions))))
       )
     ]
    (.addHandler contexts srv-con-handler)
    (doseq [[context-uri ctxt] (context-map-of env)] 
      (let [resource-base (context-base-of ctxt)
            resource-path (context-path-of ctxt)]
        (log-info log-id (str "context " context-uri " via " resource-path " at " resource-base))
        (let [handler 
              (doto (ContextHandler.)
                (.setContextPath resource-path)
                (.setResourceBase resource-base)
                (.setHandler (ResourceHandler.)))]
          (.addHandler contexts handler))))
    (doseq [[context-id resource-base] (use-map-of env)] 
      (do
        (log-info log-id (str "using " context-id " at " resource-base))
        (let [handler 
              (doto (ContextHandler.)
                (.setContextPath context-id)
                (.setResourceBase resource-base)
                (.setHandler (ResourceHandler.)))]
          (.addHandler contexts handler))))
    server))

;; moved to local-config
#_(defn- setprops [] 
   (do
     (System/setProperty "jetty.home" home-dir )
     (System/setProperty "START" (in-home "/start.config") )
     (System/setProperty "install.jetty.home" jetty-home )
     (System/setProperty "VERBOSE" "true")
     (System/setProperty "STOP.PORT" "8082")
     (System/setProperty "STOP.KEY" "secret")))

#_ (* A reference that returns the currently running server, if there is one, or nil
      if not.)
(defonce jetty-server (ref nil))

;#_ (* Starts a voxindex server. 
;      @p @name fires up a voxindex server, and saves the result so that the
;      resulting server can be shut down using @(link stop-server). @name uses
;      @(li defnk)-style parameters with useful defaults.
;     )
;(defnk start-server [:port 8080 :host "192.168.1.101" :ssl-port 8443 
;                     :serve-from "/content" :context-map {} :use-map {}]
;  (setprops) 
;  (let [env (make-ServiceEnvironment host port ssl-port serve-from context-map use-map) 
;        svr (make-server env)]
;    (dosync (ref-set jetty-server svr))
;    (.start svr) 
;    svr))

#_ (* Starts a voxindex server. 
      @p @name fires up a voxindex server, and saves the result so that the
      resulting server can be shut down using @(link stop-server). 
      @arg env A @(li ServiceEnvironment) object describing the desired server configuration.
      @returns The created server object.
     )
(defn start-server [env]
  #_(setprops) 
  (let [svr (make-server env)]
    (dosync (ref-set jetty-server svr))
    (.start svr) 
    svr))

#_ (* Shuts down a server started using @(li start-server).
      ) 
(defn stop-server []
  (let [svr (dosync (let [s @jetty-server] (ref-set jetty-server nil)  s))]
    (if svr (.stop svr))))

(defn dump-to [file-name]
  (with-open [fw (FileWriter. file-name)]
    (.append fw (.dump @jetty-server))
    (.flush fw)
    (.close fw)))

(defn dump-threads [file-name]
  (let [thread-map (Thread/getAllStackTraces)]
    (with-open [fw (FileWriter. file-name)]
      (doseq [thr (seq (.keySet thread-map))]
        (.append fw  (str "Thread: " thr "\n"))
        (doseq [sf (seq (.get thread-map thr))]
          (.append fw (str "  at " (.toString sf) "\n")))
        (.append fw "\n"))
      (.flush fw)
      (.close fw))))


