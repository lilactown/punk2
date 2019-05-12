(ns punk.ui.shells.remote
  (:require [punk.ui.core :as core]
            [clojure.core.async :as a]
            [cljs.tools.reader.edn :as edn]
            [hx.react :as hx :refer [defnc]]
            [hx.hooks :as hooks]
            [punk.ui.encode :as encode]
            ["react-dom" :as react-dom]))

(defonce in-chan (a/chan))

(defonce out-chan (a/chan))

(defonce error-chan (a/chan))

;; (defonce subscriber core/external-handler)

;; (f/reg-fx
;;  core/ui-frame :emit
;;  (fn [v]
;;    (a/put! out-chan (pr-str v))))

;; subscriber loop
;; (a/go-loop []
;;   (let [ev (a/<! in-chan)]
;;     (subscriber ev)
;;     (recur)))

(defonce state (atom {:status :closed}))

(defn connect [{:keys [port]}]
  (let [conn (js/WebSocket. "ws://localhost:9876/ws")]
    (swap! state assoc
           :conn conn
           :status :connecting)
    ;; websocket config
    (.addEventListener conn "open"
                       (fn [ev]
                         (println "Open ")
                         (swap! state assoc :status :open)))
    (.addEventListener conn "message"
                       (fn [ev]
                         (js/console.log (.-data ev) (encode/read (.-data ev)))
                         (a/put! in-chan (encode/read (.-data ev)))))
    (.addEventListener conn "error"
                       (fn [e]
                         (a/put!
                          error-chan
                          {:ts (js/Date.now)
                           :value e
                           :message (if (= (:status @state) :connecting)
                                      "An error occurred connecting to the remote server."
                                      "An error occurred in the web socket connection.")})))
    (.addEventListener conn "close"
                       (fn [ev]
                         (swap! state assoc :status :closed)))

    ;; send socket messages
    (a/go-loop []
      (let [ev (a/<! out-chan)]
        (.send conn ev)
        (recur)))))

(defn close []
  (when (:conn @state)
    (.close (:conn @state))))





(defn useDeref
  "Takes an atom. Returns the currently derefed value of the atom, and re-renders
  the component on change."
  ;; if no deps are passed in, we assume we only want to run
  ;; subscrib/unsubscribe on mount/unmount
  ([a]
   ;; create a react/useState hook to track and trigger renders
   (let [[v u] (hooks/useState @a)]
     ;; react/useEffect hook to create and track the subscription to the iref
     (hooks/useEffect
      (fn []
        (let [k (gensym "<-deref")]
          (add-watch a k
                     ;; update the react state on each change
                     (fn [_ _ _ v'] (u v')))
          ;; Check to ensure that a change has not occurred to the atom between
          ;; the component rendering and running this effect.
          ;; If it has updated, then update the state to the current value.
          (when (not= @a v)
            (u @a))
          ;; return a function to tell react hook how to unsubscribe
          #(remove-watch a k)))
      ;; pass in deps vector as an array
      ;; resubscribe if `a` changes
      [a])
     ;; return value of useState on each run
     v)))




(defnc Connection [_]
  (let [state (useDeref state)
        [hover set-hover] (hooks/useState false)]
    [:div {:class "connection"}
     ;; (prn-str state)
     [:button {:on-click (case (:status state)
                           :open #(close)
                           :closed #(connect {:port 9876})
                           :connecting nil)
               :on-mouse-enter #(set-hover true)
               :on-mouse-leave #(set-hover false)
               :class ["connection--status-button"
                       (case (:status state)
                         :open "connection--status-button-connected"
                         :closed "connection--status-button-disconnected"
                         :connecting nil)]}
      (case (:status state)
        :open [:<>
               [:span {:class "connection--status-indicator-connected"}]
               (if hover
                 "Disconnect"
                 "Connected")]
        :connecting [:span {:class "connection--status-indicator-connecting"}
                     "Connecting"]
        :closed [:<>
                 [:span {:class "connection--status-indicator-disconnected"}]
                 (if hover
                   "Connect"
                   "Disconnected")])]]))

(defn ^:export ^:dev/after-load start []
  (println "Starting!")
  (when (= :closed (:status @state))
    (connect {:port 9876}))
  (let [container (. js/document getElementById "remote-app")]
    (react-dom/render (hx/f [:<>
                             [Connection]
                             [core/Main {:tap-chan in-chan
                                         :error-chan error-chan}]]) container)))
