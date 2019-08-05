(ns punk.ui.core
  (:require [hx.react :as hx :refer [defnc]]
            [hx.hooks :as hooks]
            [hx.hooks.alpha :as alpha]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [clojure.core.async :as a]
            [clojure.string :as s]
            [goog.object :as gobj]
            [datascript.core :as ds]
            [punk.ui.lib :as lib]
            [punk.ui.views :as views]))

(def routing-context (hx/create-context))

(def ts-format (tf/formatters :hour-minute-second))


;;
;; DB
;;

(defmulti db-event (fn [ev]
                     (prn ev)
                     (first ev)))

(defmulti db-subscribe (fn [app-db id _] id))


;;
;; Taps
;;

(defnc TapEntry [{:keys [ts value metadata selected? on-select on-inspect tap-lens]}]
  (let [lens-value (get-in value tap-lens value)]
    [:div {:class "taplist--entry"}
     (when selected?
       [:div {:class "taplist--entry--tools"}
        [:button
         {:on-click on-select}
         [:span {:class "taplist--entry--tools--close-icon"}]]
        [:button
         {:on-click on-inspect}
         [:span {:class "taplist--entry--tools--inspect-icon"}]]])
     [:div {:style {:display "flex"}
            :class ["taplist--entry--body" (when selected? "taplist--entry-selected")]
            :on-click #(when-not selected? (on-select))}
      [:div {:class "taplist--entry--timestamp"}
       (->> ts
            (tc/from-long)
            (t/to-default-time-zone)
            (tf/unparse ts-format))]
      [:div (cond-> {:class "taplist--entry--value"}
              (not= lens-value value)
              (assoc :style {:border "2px solid rgba(0, 250, 0, 0.3)"}))
       [views/Code {:value lens-value :pprint? selected?}]]
      #_[:div {:style {:flex 2}} metadata]]]))


(defnc TapList [{:keys [entries inspect-value tap-lens]}]
  (let [[selected update-selected] (alpha/useStateOnce nil ::tap-list.selected)]
    [:div {:class "taplist"}
     (if (empty? entries)
       [:div {:style {:text-align "center"
                      :margin-top "50px"}}
        [:h2 "Nothing here yet."]]
       (for [entry entries]
         (let [{:keys [id]} entry]
           [TapEntry (assoc entry
                            :tap-lens tap-lens
                            :key id
                            :selected? (= selected id)
                            :on-select #(if (= selected id)
                                          (update-selected nil)
                                          (update-selected id))
                            :on-inspect #(inspect-value entry))])))]))

;;
;; Tap Lens
;;

(defn- format-lens-part
  [lens-part]
  (cond
    ;; is a keyword
    (= ":" (first lens-part))
    (->> lens-part rest (clojure.string/join "") keyword)

    ;; is an index
    (false? (js/isNaN (js/Number lens-part)))
    (js/parseInt lens-part 10)

    :else
    lens-part))

(defn- format-lens
  [set-lens value]
  (set-lens (map
             format-lens-part
             (clojure.string/split (-> value .-target .-value) #" "))))

(defnc TapLens [{:keys [update-lens]}]
  (let [bracket-styles {:font-size "20px"}]
    [:div {:style {:display "flex"
                   :align-items "center"
                   :width "100%"
                   :padding "20px"}}
     [:span {:style bracket-styles} "["]
     [:input {:style {:width "100%"
                      :margin-left "20px"
                      :margin-right "20px"
                      :height "30px"
                      :padding "8px"}
              :on-change update-lens
              :placeholder "Search in object (ex: :some :deep :value)"}]
     [:span {:style bracket-styles} "]"]]))

;;
;; Inspector
;;

(def views
  {:punk.view/tree views/TreeView
   :punk.view/code views/CodeView})

(defnc Inspector [{:keys [value]}]
  (let [[current-view set-current-view] (hooks/useState :punk.view/tree)]
    [:div {:class "inspector"
           :style {:position "relative"
                   :display "flex"
                   :flex-direction "column"}}
     [:div {:style {:flex 1
                    :min-height "0px"
                    :overflow-y "scroll"}}
      [(views current-view) {:value value}]]
     [:div {:style {:background "#ffffef"
                    :height "35px"
                    :z-index 2}}
      [:select {:value (str current-view)
                :on-change #(set-current-view (keyword (s/replace-first (-> % .-target .-value) #":" "")))}
       (for [view-id (keys views)]
         [:option (str view-id)])]]]))


;;
;; Top controls
;;

(defnc TopControls [_]
  (let [[router set-route] (hooks/useContext routing-context)]
    [:div {:class "top-controls"}
     (for [route (:routes router)]
       [:button {:key (:id route)
                 :class ["top-controls--tab" (when (= (:id route) (:current router))
                                               "top-controls--tab-selected")]
                 :on-click #(set-route (:id route))}
        (:label route)])]))


;;
;; Errors
;;

(defnc Errors [{:keys [chan]}]
  (let [[errors set-errors] (alpha/useStateOnce [] ::errors)
        [live-errors set-live-errors] (alpha/useStateOnce #{} ::live-errors)
        add-error (hooks/useCallback #(do (set-errors conj %)
                                          (set-live-errors conj %)) [set-errors])]
    (lib/useChan chan
             add-error
             ;; should never close
             identity
             ::errors)
    [:div {:class "errors"}
     (for [err live-errors]
       [:div {:class "errors--message"}
        (:message err)
        [:span {:class "errors--close"
                :on-click #(set-live-errors disj err)}
         "ⅹ"]])]))




;;
;; Main
;;


;; Events

(defmethod db-event :entry [[_ id value]]
  [{:db/id -1
    :entry/value value
    :entry/id id
    :entry/ts (js/Date.now)}])

(defmethod db-event :punk/tap [[_ id value]]
  [{:db/id -1
    :entry/value value
    :entry/id id
    :entry/ts (js/Date.now)}])


;; Subscriptions

(defmethod db-subscribe :taps/entries [app-db _ _]
  (->> (ds/q '[:find ?tx ?id ?v ?ts
               :where
               [?e :entry/value ?v ?tx]
               [?e :entry/id ?id ?tx]
               [?e :entry/ts ?ts ?tx]]
             app-db)
       (sort-by first #(compare %2 %1))
       (map (fn [[_ id v ts]]
              {:id id
               :value (:value v)
               :ts ts}))))



(defnc Main [{:keys [initial-taps tap-chan error-chan]}]
  (let [[app-db transact] (lib/useDataScript nil [])
        update-taps (hooks/useCallback (fn [ev]  (transact (db-event ev)))
                                       [transact db-event])
        [router update-router] (alpha/useStateOnce {:current :tap-list
                                                    :routes [{:id :tap-list
                                                              :label "Taps"}
                                                             {:id :inspector
                                                              :label "Inspector"}]}
                                                   ::router)
        set-route (hooks/useCallback #(update-router assoc :current %)
                                     [update-router])
        [inspected update-inspected] (alpha/useStateOnce nil ::inspected)
        [query update-query] (hooks/useState '[:find ?v ?tx :where [?e :entry/value ?v ?tx]])
        [search update-search] (hooks/useState (str query))
        [tap-lens update-lens] (hooks/useState '())]

    (lib/useChan tap-chan
                 update-taps
                 identity
                 ::taps)

    (do (prn app-db)
        (prn (ds/q query app-db)))

    [:provider {:context routing-context
                :value [router set-route]}
     [Errors {:chan error-chan}]
     [:div {:style {:border "1px solid #d3d3d3"
                    :height "100%"
                    :display "flex"
                    :flex-direction "column"}}
      [:div {:style {:background "#efffff"
                     :height "35px"}}
       [TopControls]]
      [:div {:style {:background "#ffffff"
                     :flex 1
                     :min-height "0px"
                     :overflow-y "scroll"}}

       [TapLens {:update-lens (partial format-lens update-lens)}]

       (case (:current router)
         :tap-list [TapList {:entries (db-subscribe app-db :taps/entries)
                             :inspect-value #(do (update-inspected (:value %))
                                                 (set-route :inspector))
                             :tap-lens tap-lens}]
         :inspector [Inspector {:value (get-in inspected tap-lens inspected)}])]
      ;; [:div {:style {:height "180px"
      ;;                :display "flex"
      ;;                :flex-direction "column"
      ;;                :padding "10px"}}
      ;;  [:div {:style {:text-align "center"}}
      ;;   [:button {:type "button"
      ;;             :on-click #(update-query (cljs.reader/read-string search))} "Submit"]]
      ;;  [:textarea {:type "text"
      ;;              :value search
      ;;              :style {:margin "auto"
      ;;                      :min-width "500px"
      ;;                      :flex 1}
      ;;              :on-change #(update-search (-> % .-target .-value))}]]
      ]]))
