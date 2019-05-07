(ns punk.ui.chrome.content-script
  (:require [chromex.ext.runtime :as runtime :include-macros true]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [clojure.core.async :refer [<! put! chan go go-loop] :include-macros true]
            [chromex.support :refer [runonce]]
            [punk.ui.encode :as encode]))

;; -- tab messages -------------------------------------------------------------

(defn process-tab-message! [bg-chan message]
  (log "CONTENT SCRIPT: got tab message:" message)
  (post-message! bg-chan (encode/write [:content-script/message message])))

(defn tab-message-loop! [bg-chan tab-chan]
  (log "CONTENT SCRIPT: starting tab message loop...")
  (go-loop []
    (when-some [message (<! tab-chan)]
      (process-tab-message! bg-chan message)
      (recur))
    (log "CONTENT SCRIPT: leaving tab message loop")))

(defn connect-to-tab! [bg-chan]
  (let [c (chan)]
    (js/window.addEventListener
     "message"
     (fn [ev]
       (when (= (.-source ev))
         (let [message (.-data ev)]
           (when (and (= (goog/typeOf message) "object")
                      (not (nil? message))
                      (= (.-source message) "punk"))
             (put! c (.-data message)))))))
    (tab-message-loop! bg-chan c)))


;; -- background loop ----------------------------------------------------------

(defn process-message! [message]
  (log "CONTENT SCRIPT: got background message:" message))

(defn run-message-loop! [message-channel]
  (log "CONTENT SCRIPT: starting background message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (process-message! (encode/read message))
      (recur))
    (log "CONTENT SCRIPT: leaving background message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port
                   (encode/write [:content-script/connect]))
    (run-message-loop! background-port)
    (connect-to-tab! background-port)))


;; -- main entry point ---------------------------------------------------------

(defn init! []
  (js/console.log "CONTENT SCRIPT: init")
  (connect-to-background-page!)
  )


(runonce (init!))
