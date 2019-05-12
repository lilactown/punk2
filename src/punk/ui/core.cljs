(ns punk.ui.core
  (:require [hx.react :as hx :refer [defnc]]
            [hx.hooks :as hooks]
            [hx.hooks.alpha :as alpha]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [clojure.core.async :as a]
            [clojure.string :as s]
            [clojure.pprint]
            ["react-syntax-highlighter" :as rsh]
            ["react-syntax-highlighter/dist/esm/styles/hljs" :as hljs]
            [goog.object :as gobj]
            [datascript.core :as ds]))

(def routing-context (hx/create-context))

(def ts-format (tf/formatters :hour-minute-second))


;;
;; Views
;;

(defnc Code [{:keys [value pprint?]}]
  [rsh/default {:language "clojure"
                :style hljs/githubGist
                :customStyle (if pprint?
                               #js {:padding 0
                                    :background "none"}
                               #js {:padding 0
                                    :background "none"
                                    :overflowX "hidden"})}
   (if pprint?
     (with-out-str
       (cljs.pprint/pprint value))
     (pr-str value))])


(defnc CodeView [props]
  [:div {:style {:overflow "auto"
                 :max-height "100%"}
         :class "inspector--code-view"}
   [Code (assoc props :pprint? true)]])

(defnc CollView [{:keys [value]}]
  (let [[current update-current] (hooks/useState 0)]
    [:div {:class "inspector--coll-view"}
     [:div {:class "inspector--coll-view--indices"
            :style {:overflow "scroll"}}
      (map-indexed (fn [idx _]
                     (vector :div
                             (merge {:on-click #(update-current idx)}
                                    (when (= idx current)
                                      {:class "inspector--coll-view--indices-selected"})) idx))
                   value)]
     [:div {:style {:flex 1
                    :padding "0 10px"}
            :class "inspector--coll-view--body"}
      [Code {:value (nth value current)
             :pprint? true}]]]))


(defnc TreeNode [{:keys [label value collapsed/default on-collapse children]}]
  (let [[collapsed? set-collapsed] (hooks/useState default)]
    [:div {:class "inspector--tree-node"}
     [:div {:class "inspector--tree-node--item"}
      [:div {:on-click #(set-collapsed not)}
       [:div {:class (if collapsed?
                       "inspector--tree-node--arrow-collapsed"
                       "inspector--tree-node--arrow")}]
       [:div {:class "inspector--tree-node--label"} label]]
      [:span {:class "inspector--tree-node--preview"}
       (when collapsed?
         [Code {:pprint? false :value value}])]]
     [:div {:class (if collapsed?
                     "inspector--tree-node--container-collapsed"
                     "inspector--tree-node--container")}
      (when-not collapsed?
        children)]]))


(defnc Tree [{:keys [value]}]
  [:div
   (cond
     (sequential? value)
     (for [[idx node] (map-indexed #(vector %1 %2) value)]
       [TreeNode {:label [Code {:value idx}] :value node :collapsed/default true :key idx}
        [Tree {:value node}]])

     (map? value)
     (for [[k node] value]
       [TreeNode {:label [Code {:value k}] :value node :collapsed/default true :key k}
        [Tree {:value node}]])


     (set? value)
     (for [[idx node] (map-indexed #(vector %1 %2) value)]
       [TreeNode {:label nil :value node :collapsed/default true :key idx}
        [Tree {:value node}]])

     :else [Code {:value value}])])


(defnc TreeView [{:keys [value on-nav]}]
  [:div {:class "inspector--tree-view"}
   (cond
     (sequential? value)
     (for [[idx node] (map-indexed #(vector %1 %2) value)]
       [TreeNode {:label [Code {:value idx}] :value node :collapsed/default true :key idx}
        [Tree {:value node}]])

     (map? value)
     (for [[k node] value]
       [TreeNode {:label [Code {:value k}] :value node :collapsed/default true :key k}
        [Tree {:value node}]])

     (set? value)
     (for [[idx node] (map-indexed #(vector %1 %2) value)]
       [TreeNode {:label nil :value node :collapsed/default true :key idx}
        [Tree {:value node}]])

     :else [Code {:value value}])])


;;
;; Main UI
;;


(defnc TapEntry [{:keys [ts value metadata selected? on-select on-inspect]}]
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
    [:div {:class "taplist--entry--value"}
     [Code {:value value :pprint? selected?}]]
    #_[:div {:style {:flex 2}} metadata]]])


(defnc TapList [{:keys [entries inspect-value]}]
  (let [[selected update-selected] (alpha/useStateOnce nil ::tap-list.selected)]
    [:div {:class "taplist"}
     (if (empty? entries)
       [:div {:style {:text-align "center"
                      :margin-top "50px"}}
        [:h2 "Nothing here yet."]]
       (for [entry entries]
         (let [{:keys [id]} entry]
           [TapEntry (assoc entry
                            :key id
                            :selected? (= selected id)
                            :on-select #(if (= selected id)
                                          (update-selected nil)
                                          (update-selected id))
                            :on-inspect #(inspect-value entry))])))]))

(def views
  {:punk.view/tree TreeView
   :punk.view/code CodeView})

(defnc Inspector [{:keys [value on-next on-back on-breadcrumb]}]
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


(defnc TopControls [_]
  (let [[router set-route] (hooks/useContext routing-context)]
    [:div {:class "top-controls"}
     (for [route (:routes router)]
       [:button {:key (:id route)
                 :class ["top-controls--tab" (when (= (:id route) (:current router))
                                               "top-controls--tab-selected")]
                 :on-click #(set-route (:id route))}
        (:label route)])]))


(defn useChan [chan on-take on-close tag]
  (hooks/useEffect
   (fn []
     ;; (js/console.log :useChan :resub tag on-take (= on-take @dep))
     (let [cleanup? (a/chan)]
       (a/go-loop []
         (let [[v ch] (a/alts! [chan cleanup?])]
           (cond
             (= ch cleanup?) (on-close)
             (nil? v) (on-close)
             :else (do (on-take v)
                       (recur)))))
         #(do (prn "unmounting")
              (a/put! cleanup? true))))
   [chan on-take on-close]))


(defn taps-reducer [taps-list [ev & payload]]
  (case ev
    ;; tied to old structure atm
    :entry (let [[id payload] payload]
             (conj taps-list {:id id
                              :value (:value payload)
                              :metadata (:meta payload)
                              :ts (js/Date.now)}))))

(defn on-close []
  (prn "Channel closed"))

(defnc Errors [{:keys [chan]}]
  (let [[errors set-errors] (alpha/useStateOnce [] ::errors)
        [live-errors set-live-errors] (alpha/useStateOnce #{} ::live-errors)
        add-error (hooks/useCallback #(do (set-errors conj %)
                                          (set-live-errors conj %)) [set-errors])]
    (js/console.log "errors" errors)
    (useChan chan
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

(defn useDataScript [schema init]
  (let [conn (hooks/useMemo #(let [conn (ds/create-conn schema)]
                               (prn "creating db")
                               (ds/transact! conn init)
                               conn)
                            [schema init])
        [db set-db] (hooks/useState @conn)
        transact (hooks/useCallback (fn [tx-data]
                                      (ds/transact! conn tx-data)
                                      (set-db @conn))
                                    [set-db conn])]
    (prn db)
    [db transact]))

(defn useDb [{:keys [schema init reducer subscriber]}]
  (let [[db transact] (useDataScript schema init)
        dispatch (hooks/useMemo (fn []
                                  (prn "new dispatch")
                                  (fn [event]
                                    (if-let [transaction (reducer event)]
                                      (transact transaction)
                                      (js/console.log "Empty transaction returned from reducer!"))))
                                    [transact reducer])
        subscribe (hooks/useCallback (fn [id opts]
                                       (subscriber db id opts))
                                     [subscriber db])]
    [db dispatch subscribe]))

(defmulti db-event first)

(defmethod db-event :navigation/push [[_ route-id]]
  [{:db/id -1 :navigation.history/route route-id}])

(defmethod db-event :entry [[_ id value]]
  [{:db/id -1
    :entry/value value
    :entry/id id
    :entry/ts (js/Date.now)}])

(defmethod db-event :inspect [[_ id]]
  [{:db/id -1 :inspector/value id}])

(defmulti db-subscribe (fn [app-db id _] id))

(defmethod db-subscribe :navigation/current [app-db _ _]
  (->> (ds/q '[:find ?v ?tx :where [?e :navigation.history/route ?v ?tx]] app-db)
       (sort-by second)
       (reverse)
       (ffirst)))

(defmethod db-subscribe :navigation/router [app-db _ _]
  {:current (db-subscribe app-db :navigation/current nil)
   :routes (map (fn [[id label]] {:id id :label label})
                (ds/q '[:find ?id ?label
                        :where
                        [?e :navigation.route/id ?id]
                        [?e :navigation.route/label ?label]]
                      app-db))})

(defmethod db-subscribe :taps/entries [app-db _ _]
  (->> (ds/q '[:find ?tx ?id ?v ?ts
               :where
               [?e :entry/value ?v ?tx]
               [?e :entry/id ?id ?tx]
               [?e :entry/ts ?ts ?tx]]
             app-db)
       (sort-by first)
       (reverse)
       (map (fn [[_ id v ts]]
              {:id id
               :value (:value v)
               :ts ts}))))

(defmethod db-subscribe :inspector/current [app-db _ _]
  (->> (ds/q '[:find ?v ?tx :where [?e :inspector/value ?v ?tx]] app-db)
       (sort-by second)
       (reverse)
       (ffirst)))

(def db-config
  {:schema nil
   :init [{:db/id -1
           :navigation.route/id :tap-list
           :navigation.route/label "Taps"}
          {:db/id -2
           :navigation.route/id :inspector
           :navigation.route/label "Inspector"}
          {:db/id -3
           :navigation.history/route :tap-list}]
   :reducer db-event
   :subscriber db-subscribe})

(defnc Main [{:keys [initial-taps tap-chan error-chan]}]
  (let [[app-db dispatch-db subscribe-db] (useDb db-config)
        ;; [tap-list update-taps] (alpha/useReducerOnce taps-reducer (or initial-taps '()) ::tap-list)
        update-taps (hooks/useMemo (fn []
                                     (prn "new update-taps")
                                     (fn [v]
                                         (dispatch-db v)))
                                       [dispatch-db])
        [inspected-value update-inspected] (alpha/useStateOnce nil ::inspected)
        set-route #(dispatch-db [:navigation/push %])]
    (prn (subscribe-db :taps/entries))
    (useChan tap-chan
             update-taps
             on-close
             ::taps)
    [:provider {:context routing-context
                :value [(subscribe-db :navigation/router) set-route]}
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
       (case (subscribe-db :navigation/current)
         :tap-list [TapList {:entries (subscribe-db :taps/entries)
                             :inspect-value #(do (update-inspected (:value %))
                                                 (set-route :inspector))}]
         :inspector [Inspector {:value inspected-value}])]]]))
