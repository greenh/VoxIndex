(ns cjd-doc.req
  (:use 
    [voxindex.vshell.bridge-start]
    [cjd-doc.gen]
    
    [cjd.link-resolver]
    )
  )

(start-bridge)

(add-external-resolvers 
  (fn [ns sym]
    (if (re-matches #"system\.speech\..*" (name ns))
      (let [cls (.replaceAll (name ns) "\\." "/")]
        (str "http://http://msdn.microsoft.com/en-us/library/" 
             (name ns) ".aspx" ))))
  (fn [ns sym]
    (if (re-matches #"javax\.servlet\..*" (name ns))
      (let [cls (.replaceAll (name ns) "\\." "/")]
        (str "http://docs.oracle.com/javaee/6/api/" 
             (name ns) ".html" )))))
