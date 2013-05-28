#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

#_ (* Specifies a preassigned index IDs. These are, in effect, "well-known"
      IDs for a small selection of specialized indexes that generally always 
      have to be present.
      @p IDs here are assumed to be string representations of longs in hex,
      in support of the Mongo @(link org.bson.types.ObjectId ObjectId) type.
     )
(ns indexterous.config.assigned-ids)

(def max-preassigned-id    "000000000000000000000010")

(defn preassigned-index-id? [id] 
  (<  (.compareTo id max-preassigned-id) 0))

(def control-index-id     "000000000000000000000001") ; well-known control index ID
(def audiology-index-id   "000000000000000000000002") ; well-known audiology index ID
; sample ObjectId         "4e248f3f9d9a514005e5a16b"
