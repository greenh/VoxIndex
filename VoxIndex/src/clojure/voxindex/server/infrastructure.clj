#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Very simple-minded implementation of a user database.
      )
(ns voxindex.server.infrastructure
  (:use
    [extensomatic.extensomatic]
    [voxindex.server.vox-log]
    )
  (:import 
    [org.joda.time DateTime]
    [org.mindrot.jbcrypt BCrypt])
  )

(def ^{:private true} log-id "VoxIndex.Infrastructure")

(def expiration-msec (* 1000 60 60 24 7))  ;; a week, for now

#_ (* Representation of a user. )
(defconstructo User [] [user-id credentials db-name can-update]
  { :new-sym new-User }
  (user-id-of [this] user-id) 
  (user-db-name-of [this] db-name)
  (authenticate [this cred] #_(= credentials cred) true)
  (can-update? [this] can-update)
  )

(defextenso HasUser [] [user]
  (user-of [this] user))

(defn- moosh [userid password] (str userid "@@@" password))

#_ (* Our miserable wretched paltry cheap sorry excuse for a user dartabase.
      )
(def users*
  (ref { 
        "greenh" 
        (new-User "greenh"
                  (BCrypt/hashpw (moosh "hhgreen@ieee.org" "poo") (BCrypt/gensalt 12))
                  "Myself" true)
        "guest" 
        (new-User "guest" 
                  (BCrypt/hashpw (moosh "guest" "guest") (BCrypt/gensalt 12)) 
                  "public" false)
        }))

#_ (* Looks to see if a user ID represents a valid user, and if so,
      if the credentials agree.
      @arg user-id A string containing the user ID to authenticate.
      @arg credentials Credentials (whatever they are!).
      @returns If authentication succeeds, the @(il User) object 
      representing the user, or nil if not.
      )
(defn authenticate-user [user-id credentials]
  (if-let [user (@users* user-id)]
    (if (authenticate user credentials)
      user)))

(defn get-user [user-id]
  (@users* user-id))

#_ (* Session object. 
      )
(defconstructo Session 
  [(HasUser user)] 
  [db
   recognizer
   (session-id (str (java.util.UUID/randomUUID)))
   (create-time (DateTime/now)) 
   (expiration-time (+ (System/currentTimeMillis) expiration-msec))
   (last-request-time* (ref (DateTime/now)))
   (request-count* (ref 0))
   (last-wave* (ref nil))
   ]
  { :new-sym new-Session }
  (session-id-of [this] session-id)
  (session-db-of [this] db)
  (create-time-of [this] create-time)
  (expiration-of [this] expiration-time)
  (expired? [this] (> (System/currentTimeMillis) expiration-time))
  (last-request-time-of [this] @last-request-time*)
  (request-count-of [this] @request-count*)
  (session-requested [this wave] 
    (let [new-time (DateTime/now)] 
      (dosync
        (alter request-count* inc)
        (ref-set last-request-time* new-time)
        (ref-set last-wave* wave)))
    nil)
  (recognizer-of [this] recognizer)
  (last-wave-of [this] @last-wave*)
  (write-last-wave-to [this fname] 
    (let [f (java.io.DataOutputStream. (java.io.FileOutputStream. fname))
          wave @last-wave*]
      (.write f wave 0 (count wave))))
  
  java.lang.Object
  (toString [this] (str "#<Session " session-id " " (user-id-of user) ">")))

#_ (* Our simple-minded excuse for a session database.)
(defonce sessions* (ref { }))

(defn get-session [id] (@sessions* id))

(defn activate-session [session]
  (dosync
    (alter sessions* assoc (session-id-of session) session))
  nil)

(defn terminate-session [session-id]
  (dosync
    (alter sessions* dissoc session-id))
  nil)

(defn clear-sessions []
  (let [active 
        (dosync
          (let [active @sessions*]
            (ref-set sessions* {})
            active))]
    (doseq [[session-id session] active]
      (println "Snuffing old session" session-id))))




