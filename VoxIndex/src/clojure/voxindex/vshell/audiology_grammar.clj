#_ ( Copyright (c) 2013 - 2014 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )
#_ (* Grammar support for user interface voice control commands.
     )
(ns voxindex.vshell.audiology-grammar
  (:use
    [voxindex.server.vox-log]
    [voxindex.util.grammar-utils]
    [voxindex.vshell.grammar]
    [voxindex.vshell.grammar-html]
    [voxindex.vshell.recognizer-base]
    [indexterous.config.assigned-ids]
    [extensomatic.extensomatic]
    [indexterous.index.db]
    )
  (:import
    [system.speech.recognition 
     Choices
     Grammar
     GrammarBuilder
     SemanticResultKey
     SemanticResultValue
     ]
    [voxindex.shared VoxIndexIDs]
    )  
  )

(defn aurn [cmd] (VoxIndexIDs/voxCmdURN cmd))

;;; N.B. Do not hyphenate command names---"newtab" works OK, "new-tab" won't.
;;; Note that command names must be valid Java IDs, and must be lower-case...
;;; e.g., "scrollup" is OK, "scrollUp" and "scroll-up" aren't.
(def audiology-cmds [
   [["back" "backward" "go back"] (aurn "back") "Go back to the previously page"] 
   [["forward" "go forward"] (aurn "forward") "Go foward to the next page"]
   [["top" "beginning" "to beginning"] (aurn "top") "Go to the top of the page"]
   [["bottom" "end" "to end"] (aurn "bottom") "Go to the bottom of the page"]
   ; [["show log"] (aurn "log") "Show the log"]
   [["grammar" "help"] (aurn "grammar") "Show this page"]
   [["down" "page down" "next" "next page"] (aurn "move:1")]
   [["down 1" ] (aurn "move:1") nil]
   [["down 2" ] (aurn "move:2") nil]
   [["down 3" ] (aurn "move:3") nil]
   [["down 4" ] (aurn "move:4") nil]
   [["down 5" ] (aurn "move:5") nil]
   [["down 6" ] (aurn "move:6") nil]
   [["down 7" ] (aurn "move:7") nil]
   [["down 8" ] (aurn "move:8") nil]
   [["down 9" ] (aurn "move:9") nil]
   [["down 10" ] (aurn "move:10") nil]
   [["scroll down" "scroll" "down some"] (aurn "scrolldown")]
   [["up" "page up" "previous" "previous page"] (aurn "move:-1")]
   [["up 1" ] (aurn "move:-1") nil]
   [["up 2" ] (aurn "move:-2") nil]
   [["up 3" ] (aurn "move:-3") nil]
   [["up 4" ] (aurn "move:-4") nil]
   [["up 5" ] (aurn "move:-5") nil]
   [["up 6" ] (aurn "move:-6") nil]
   [["up 7" ] (aurn "move:-7") nil]
   [["up 8" ] (aurn "move:-8") nil]
   [["up 9" ] (aurn "move:-9") nil]
   [["up 10" ] (aurn "move:-10") nil]
   [["scroll up" "up some" "previous some"] (aurn "scrollup")]
   [["off" "go away" "stop" "begone"] (aurn "stop")]
   ; [["exit" "quit"] (aurn "exit")]
   [["new tab" "create a new tab"] (aurn "newtab") "Create a new tab"]
   [["tab 1" ] (aurn "tab:1") nil]
   [["tab 2" ] (aurn "tab:2") nil]
   [["tab 3" ] (aurn "tab:3") nil]
   [["tab 4" ] (aurn "tab:4") nil]
   [["tab 5" ] (aurn "tab:5") nil]
   [["tab 6" ] (aurn "tab:6") nil]
   [["tab 7" ] (aurn "tab:7") nil]
   [["tab 8" ] (aurn "tab:8") nil]
   [["tab 9" ] (aurn "tab:9") nil]
   [["tab 10" ] (aurn "tab:10") nil]
   [["tabs"] (aurn "tabs") "Show a list of tabs"]
   [["close tab" ] (aurn "close-tab") nil]
   [["close tab 1" ] (aurn "close-tab:1") nil]
   [["close tab 2" ] (aurn "close-tab:2") nil]
   [["close tab 3" ] (aurn "close-tab:3") nil]
   [["close tab 4" ] (aurn "close-tab:4") nil]
   [["close tab 5" ] (aurn "close-tab:5") nil]
   [["close tab 6" ] (aurn "close-tab:6") nil]
   [["close tab 7" ] (aurn "close-tab:7") nil]
   [["close tab 8" ] (aurn "close-tab:8") nil]
   [["close tab 9" ] (aurn "close-tab:9") nil]
   [["close tab 10" ] (aurn "close-tab:10") nil]
   [["yes" "yes yes" "yeah" "right" "okay" "correct" "accept"] (aurn "accept") nil]
   [["no" "no no" "wrong" "not okay" "nope" "incorrect" "reject"] (aurn "reject") nil]
   [["cancel"] (aurn "cancel") nil]
   [["show next"] (aurn "nextvariant") nil]
   [["show previous"] (aurn "previousvariant") nil]
   ])

(def ^{ :private true } command-uri-key "command-uri")


(defn make-audiology-grammar [] 
  (let [cmd-choices
        (make-sem-res-key command-uri-key
          (.ToGrammarBuilder 
            (Choices.
              (into-array GrammarBuilder 
                (reduce 
                  (fn [gbs+ [phrases cmd-uri _]] 
                    (reduce 
                      (fn [gbs++ phrase] (conj gbs++ (make-cv phrase cmd-uri)))
                      gbs+ phrases))
                  []  audiology-cmds)))))
        grammar (Grammar. cmd-choices)]
    (.setName grammar audiology-index-id)
    grammar))

(defconstructo AudiologyGrammar 
  [(GrammarHandlerBase 
     (grammar (make-audiology-grammar)) (grammar-id audiology-index-id) 
     (index-name "Audiology commands") recognizer)] [] 
  { :new-sym new-AudiologyGrammar }
  
  GrammarEventHandler 
  (on-load [this] )
  
  (on-recognition [this sem-map conid-map result-thingy]
    (let [{ cmd-uri command-uri-key } sem-map]
      (command! result-thingy cmd-uri)))
  
  (on-unload [this] )
  )



