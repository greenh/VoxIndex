#_ ( Copyright (c) 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Encapsulates information that describes the local "service environment".
      )
(ns voxindex.server.service-environment
  (:use
    [extensomatic.extensomatic]
    )
  (:require 
    [indexterous.index.index :as index]
    )
  )

#_ (* Object describing a locally served context, a tree of files served by the server
      (as opposed remotely served by others).
     )
(defconstructo LocalContext [] [uri path base]
  (context-uri-of [this] uri)
  (context-path-of [this] path)
  (context-base-of [this] base)
  )

#_ (* Organizes a collection of context descriptions into a map for use by
      a @(il ServiceEnvironment).
      @(arg contexts Zero or more tuples of the form @(form [uri path base]), where
            @arg uri A URI used to identify the context.
            @arg path The path used by the server to recognize the context.
            @arg base The local directory from which the context will be served.
       )
      @returns The context map.
      )
(defn local-context-map [& contexts]
  (reduce 
    (fn [cm [uri path base]]
      (let [context (make-LocalContext uri path base)] 
        (assoc cm uri context)))
    {} contexts))

(def no-sep-re #"[#/?].*")

#_ (* Looks at a relative URI and decides whether or not to add a "/" before
     the it's appended to a base URI.)
(defn- separate [rel-uri]
  (if (re-matches no-sep-re rel-uri)
    rel-uri
    (str "/" rel-uri)))

#_ (* Object that encapsulates all the parameters that describe local service environment,
      and provides methods for calculating, e.g., the URLs needed to externally reference
      locally supplied content.
      
      @field host The host name (or address) of the local server
      @field port The server's non-secure port number
      @field ssl-port The server's secure port number.
      @field local-content-base The URI path fragment that denotes locally served content.
      @field context-map A map relating each local context's identifying URI to its 
      @(li LocalContext) descriptions.
      @(field context-map-fn A function for mapping "local" sources to the URIs 
              that they'll be locally served at. This is of the form 
              @(fun [cmap local-uri]), where 
              @arg cmap is the value of @(name context-map) field
              @arg local-uri The source's URI to be mapped
              @returns A string containing the local URI for the source.)
      @field use-map
      )
(defconstructo ServiceEnvironment []
  [host
   port
   ssl-port
   local-content-base
   context-map
   use-map
   ]
;  #_ (* Returns the base URI path fragment for locally served content.
;        @returns A string containing the local content path.
;        ) 
;  (content-base-of [this] local-content-base)
;  
;  #_ (* Given the identity of a body of locally served content, returns 
;        the relative path to that content.
;        @arg content-id The content's identity.
;        @returns The relative path to the content.
;        )
;  (service-context [this context-id] (str local-content-base "/" context-id))
  
  #_ (* Returns the host name for the locally served content.
        @returns A string containing the host name.
        ) 
  (service-host-id [this] host)
  
  #_ (* Returns the port number for locally served content.
        @returns An integer containing the server's port number.
        ) 
  (service-port [this] port)
  
  #_ (* Returns the SSL port for local services.
        @returns An integer containing the server's secure port number.)
  (service-secure-port [this] ssl-port) 
  
  #_ (* Returns the identity of the server.
        @returns The local server's identity, e.g. "www.example.com:8080".
	      )
  (service-host [this] (if (= port 80) host (str host ":" port)))
  
  #_ (* Returns the identity of the server using SSL.
        @returns The local server's identity, e.g. "www.example.com:443".
        )
  (service-secure-host [this] (if (= ssl-port 443) host (str host ":" ssl-port)))
  
  #_ (* Returns the server's base HTTP URI.
        @returns A string containing the server's base URI, e.g. 
        "http://www.example.com:8080". 
	      )
  (service-uri-base [this] (str "http://" (service-host this)  ))
  
  #_ (* Returns the server's base HTTPS URI.
        @returns A string containing the server's base URI, e.g. 
        "https://www.example.com:3456". 
        )
  (service-secure-uri-base [this] (str "https://" (service-secure-host this)  ))
  
  (context-map-of [this] context-map)
  
  (use-map-of [this] use-map)
  
  (secure-map [this map-key] 
     (if-let [context (get context-map map-key)] 
       (str (service-secure-uri-base this) (context-path-of context))
       
       ;; This sucks, but it's better than nothing
       (binding [*out* *err*]
           (println (str "Missing context for key " map-key) ))))
  
  (nonsecure-map [this map-key] 
     (if-let [context (get context-map map-key)] 
         (str (service-uri-base this) (context-path-of context))
         
         ;; This sucks, but it's better than nothing
         (binding [*out* *err*]
           (println (str "Missing context for key " map-key) ))))
  
  #_ (* Generates a list of (URI, source-id, locator-key) tuples for a source.
        @arg source A @(l ConsultablySourced) derivative object.
        @arg secure True for SSL-based URL.
        @returns A collection of mapped (URI, source-id locator-key) tuples for the source.
        )
  (mapped-source-uris [this source secure]
    (let [source-id (index/id-of source)] 
      (map 
       (fn [[k locator]] 
         (if-let [[_ local-id] (re-matches #"urn\:local\:(.*)" locator)]
           [(if secure (secure-map this local-id) (nonsecure-map this local-id)) 
            source-id k]
           [locator source-id k]))
       (index/locator-map-of source))))
  
  #_ (* Generates a URI for an indexable with a source. 
        @p Either the path is\:
        @(ul 
           @li an absolute URI in its own right, in which case it's returned as-is
           @li relative to a locally-served context, as indicated by
           a locator of "urn:local:<context-id>", in which case we look up
           the base URI for the context, and extend it by the indexable's relative URI.
           @li relative to a URI specified by the locator, which we extend by
           the indexable's relative URI.)
        @arg source A @(l ConsultablySourced)-derivative source object.
        @arg indexable The indexable to generate the URI for.
        @arg secure If the 
        @returns The mapped URL for the source.
        )
  (mapped-uri [this source indexable secure]
    (let [rel-uri (index/relative-uri-of indexable)
          loc-key (index/locator-key-of indexable)
          locator (index/locator-at source loc-key)]
      (if (or (nil? loc-key) 
            (re-matches #"https*\://.*" rel-uri))  ;; is it _really_ relative?
        rel-uri ;; just return as-is

        ;; if it _is_ a relative uri, find the appropriate base to prepend
        (if-let [[_ local-id] (re-matches #"urn\:local\:(.*)" locator)] 
          ;; it's a locally-served context ID
          (if secure 
            (str (secure-map this local-id) (separate rel-uri))
            (str (nonsecure-map this local-id) (separate rel-uri)))
          ;; it's a conventional base URI
          (str locator (separate rel-uri))))))
  
  (service-index-service-base [this] "/vox-index/index-service")
  
  #_ (* Returns the URI for the server's index service.
        @returns The URI string.
        )
  (service-index-service-uri [this] 
    (str (service-uri-base this) (service-index-service-base this)))
  
  ) ;; ServiceEnvironment

