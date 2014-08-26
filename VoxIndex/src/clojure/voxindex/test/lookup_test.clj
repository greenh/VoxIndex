
(use 'voxindex.vshell.recognizer-base)
(use 'voxindex.vshell.recognizer)
(use 'indexterous.index.db)
(import 'com.mongodb.Mongo)
(def mongo (Mongo.))
(def db (new-IndexterousDB mongo "Myself"))
(open-db db)
(def rec (new-Recognizer db))
(start rec)
(use 'indexterous.config.assigned-ids)
(def base-ids #{ control-index-id audiology-index-id })
(use 'indexterous.index.index)
(use 'indexterous.index.document)


; (def rr (lookup rec base-ids (wfn "open css")))