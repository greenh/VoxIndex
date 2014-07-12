#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Defines a very, very simple database of target document bases to be 
      used by Indexterous.
      @p This is all as confusing as hell, but here's what's happening here.
      We have a desire to serve the content behind some sources locally. TO this end, we 
      ensure that the source has a service-uri of the form "urn:indexterous:<dir>", where 
      <dir> is notionally a directory that will be locally served. 
      @p Then, in the server component, there are a couple corresponding obligations.
      First, it has to actually serve the content, which implies establishing a 
      correspondence between the <dir> component and an actual directory contining the
      appropriate content. Then, there's a need to at generated URIs, see if 
      their source starts with "urn:indexterous:...", and replace that with the server's
      base URI.
      )
(ns indexterous.config.config-base)

#_ (* Specifies a URI that denotes resources that are locally served. 
      )
(def local-service-prefix "urn:indexterous")

#_ (* Creates a URI that denotes that a resource locally served.
      @arg dir A string containing the relative URI of a resource.
      @returns A string containing the full local service URI of the resource.
      )
(defn locally-served-uri [dir] (str local-service-prefix ":" dir))

#_ (* Tests to see if a URI is locally served.
      @arg URI A string containing the URI to test.
      @returns True if the URI is a local-service URI.
      )
(defn locally-served? [uri] (.startsWith uri local-service-prefix))

#_ (* Returns the "local service path", the path portion of a local service URI.
      @arg uri The URI of a locally-served resoruce.
      @returns The path relative to the @(link local-service-prefix).
      )
(defn local-service-path [uri] (subs uri (inc (.length local-service-prefix))))

#_ (* Returns a "local service ID", defined to be the first component 
      of the local-service path of a local-service URI.  
      @p For a URI like "xxx/abc/stuff.html", where "xxx" is the 
      local-service prefix, the local-service path is "abc/stuff.html",
      and thus the local-service prefix is "abc".
     
     )
(defn local-service-id [uri] 
  (let [lsp (local-service-path uri)
        off (.indexOf lsp "/")]
    (if (neg? off) 
      lsp
      (subs lsp 0 off))))




