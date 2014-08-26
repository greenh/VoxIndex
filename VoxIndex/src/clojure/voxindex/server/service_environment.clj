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
  
;  #_ (* Determines whether a source is intended to served by a to-be-identified
;        "local" server, as opposed to a well-known server on the web.
;        @p Local sources are identified by a URN, instead of a URL... but the 
;        content is abitrary, as we look up actual URLs in 
;        )
;  (source-locally-served? [this source]
;     (boolean (re-matches #"urn:" (service-uri-of source))))
  
  #_ (* Generates a URI for a source, accounting for whether or not the source
        is locally served.
        @arg source A @(il ConsultablySourced) derivative object.
        @arg secure True for SSL-based URL.
        @returns The mapped URL for the source.
        )
  (mapped-source-uri [this source secure]
    (if (index/locally-served? source)
      (if-let [context (get context-map (index/service-uri-of source))] 
        (str (if secure (service-secure-uri-base this) (service-uri-base this)) 
              #_"/" (context-path-of context)))
      (index/service-uri-of source)))
  
  #_ (* Generates a URI for a path belonging to a source. 
        @p Either the path is relative to a specified source, in which case it's 
        appended to a base URI defined by the source, or is an absolute URI in its
        own right, in which case it's returned as-is.
        @arg source A @(il ConsultablySourced) derivative object.
        @arg path The path to be mapped.
        @returns The mapped URL for the source.
        )
  (mapped-uri [this source path secure]
    (if (re-matches #"[\w+-.]+:.*" path) 
      path 
      (str (mapped-source-uri this source secure) "/" path))          )
  
  (service-index-service-base [this] "/vox-index/index-service")
  
  #_ (* Returns the URI for the server's index service.
        @returns The URI string.
        )
  (service-index-service-uri [this] 
    (str (service-uri-base this) (service-index-service-base this))))


