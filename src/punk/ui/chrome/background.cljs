(ns punk.ui.chrome.background
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<! chan]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.chrome-event-channel :refer [make-chrome-event-channel]]
            [chromex.protocols.chrome-port :refer [post-message! get-sender]]
            [chromex.ext.tabs :as tabs]
            [punk.ui.encode :as encode]
            [chromex.ext.runtime :as runtime]))

(defonce tab-id->devtool (atom {}))

;; -- clients manipulation -----------------------------------------------------

(defn- remove-val [m v]
  (reduce-kv (fn [acc k v']
               (if-not (= v v')
                 (assoc acc k v')
                 acc))
             (empty m)
             m))

(comment (remove-val {:a "b" :c "d"} "b"))

(defn identify-devtool! [devtool tab-id]
  (log "BACKGROUND: devtool identified" (get-sender devtool) tab-id)
  (swap! tab-id->devtool assoc tab-id devtool))

(defn remove-devtool! [devtool]
  (log "BACKGROUND: devtool disconnected" (get-sender devtool))
  (swap! tab-id->devtool remove-val devtool))

(defn inject-content-script! [tab-id]
  (log "BACKGROUND: injecting content script" tab-id)
  (tabs/execute-script tab-id #js {:file "out/content-script.js"}))

;; -- extension events ---------------------------------------------------------

(defmulti route-message (fn [_ [type _]] type))

(defmethod route-message :default
  [_ _]
  nil)

(defmethod route-message :devtool/connect
  [client [_ tab-id]]
  (identify-devtool! client tab-id)
  (inject-content-script! tab-id))

(defmethod route-message :content-script/connect
  [client _]
  (let [tab-id (-> (get-sender client)
                   .-tab .-id)]
    (post-message! (@tab-id->devtool tab-id)
                   (encode/write [:content-script/connect tab-id]))))

(defmethod route-message :content-script/message
  [client [_ message]]
  (let [tab-id (-> (get-sender client)
                   .-tab .-id)]
    (post-message! (@tab-id->devtool tab-id)
                   (encode/write [:tab/message message]))))

;; -- client event loop --------------------------------------------------------


(defn run-client-message-loop! [client]
  (log "BACKGROUND: starting event loop for client:" (get-sender client))
  (go-loop []
    (when-some [message (<! client)]
      (log "BACKGROUND: got client message:" message "from" (get-sender client))
      (route-message client (encode/read message))
      (recur))
    (log "BACKGROUND: leaving event loop for client:" (get-sender client))
    (remove-devtool! client)))


;; -- chrome events ------------------------------------------------------------

(defn handle-client-connection! [client]
  ;; (add-client! client)
  (post-message! client (encode/write [:background/connect]))
  (run-client-message-loop! client))


;; -- main event loop ----------------------------------------------------------

(defn process-chrome-event [event-num event]
  (log (gstring/format "BACKGROUND: got chrome event (%05d)" event-num) event)
  (let [[event-id event-args] event]
    (case event-id
      ::runtime/on-connect (apply handle-client-connection! event-args)
      ;; ::tabs/on-created (tell-clients-about-new-tab!)
      nil)))

(defn run-chrome-event-loop! [chrome-event-channel]
  (log "BACKGROUND: starting main event loop...")
  (go-loop [event-num 1]
    (when-some [event (<! chrome-event-channel)]
      (process-chrome-event event-num event)
      (recur (inc event-num)))
    (log "BACKGROUND: leaving main event loop")))

(defn boot-chrome-event-loop! []
  (let [chrome-event-channel (make-chrome-event-channel (chan))]
    (tabs/tap-all-events chrome-event-channel)
    (runtime/tap-all-events chrome-event-channel)
    (run-chrome-event-loop! chrome-event-channel)))


;; -- main entry point ---------------------------------------------------------

(defn init! []
  (log "BACKGROUND: init")
  (boot-chrome-event-loop!))
