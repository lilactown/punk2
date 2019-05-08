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
    ;; websocket config
    (.addEventListener conn "open"
                       (fn [ev]
                         (println "Open ")
                         (swap! state assoc :status :open)))
    (.addEventListener conn "message"
                       (fn [ev]
                         (js/console.log (.-data ev) (encode/read (.-data ev)))
                         (a/put! in-chan (encode/read (.-data ev)))))
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
  (let [state (useDeref state)]
    (hooks/useEffect (fn []
                       (connect {:port 9876})
                       #(close)) [])
    (println "render")
    [:div {:style {:background-color "#eee"
                   :padding "10px"}}
     (prn-str state)]))

(defn ^:export ^:dev/after-load start []
  (println "Starting!")
  (let [container (or (. js/document getElementById "connection")
                      (let [new-container (. js/document createElement "div")]
                        (. new-container setAttribute "id" "connection")
                        (-> js/document .-body (.appendChild new-container))
                        new-container))]
    (react-dom/render (hx/f [:<>
                             [Connection]
                             [core/Main {:tap-chan in-chan}]]) container)))
