(ns voxindex.server.vox-log
  (:import 
    [org.eclipse.jetty.util.log Log]
    )
  )

(defmacro log-debug [who & whys] 
  `(Log/debug (str ~who ": " ~@whys)))
(defmacro log-info [who & whys] 
  `(Log/info (str ~who ": " ~@whys)))
(defmacro log-warn [who & whys]
  `(Log/warn (str ~who ": " ~@whys)))
(defmacro log-error [who ^Throwable what & whys]
  `(Log/warn (str ~who ": " ~@whys) ~what))
