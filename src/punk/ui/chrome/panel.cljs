(ns punk.ui.chrome.panel
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [chromex.ext.devtools.inspected-window :as inspected]
            [punk.ui.encode :as encode]
            [punk.ui.chrome.panel.js :as pjs]))

(def tab-id (inspected/get-tab-id))

(defonce connection (atom nil))

(defonce message-log (atom []))

;; -- a message loop -----------------------------------------------------------

(defmulti route-message! (fn [[type _]] type))

(defmethod route-message! :default
  [message]
  (log "PANEL: unknown message type" message)
  nil)

(defmethod route-message! :content-script/connect
  [_]
  (log "PANEL: installing tap")
  (inspected/eval pjs/add-tap!))

(defn process-message! [message]
  (log "PANEL: got message:" message)
  (route-message! message))

(defn run-message-loop! [message-channel]
  (log "PANEL: starting message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (swap! message-log conj message)
      (process-message! (encode/read message))
      (recur))
    (log "PANEL: leaving message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port (encode/write [:devtool/connect tab-id]))
    (run-message-loop! background-port)
    background-port))

;; -- main entry point ---------------------------------------------------------

(defn init! []
  (log "PANEL: init")
  (when-not @connection
    (inspected/eval pjs/setup)
    (reset! connection (connect-to-background-page!))))

(init!)
