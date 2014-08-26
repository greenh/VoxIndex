#_ ( Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* )
(ns voxindex.server.vox-server-params
  (:import
    [voxindex.server ConfigWrapper]
    )
  )

; servlet context initialization parameters
(def file-dump-param "file.dump.location")
(def db-name-param "database.name" )

(def jetty-home "C:\\Users\\hhgreen\\Libraries\\jetty-distribution-7.6.10.v20130312")

; context attributes
(def db-connection "db-conn")





