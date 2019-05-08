(ns punk.ui.core
  (:require [hx.react :as hx :refer [defnc]]
            [hx.hooks :as hooks]
            [hx.hooks.alpha :as alpha]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [clojure.core.async :as a]
            [clojure.string :as s]
            ["react-syntax-highlighter" :as rsh]
            ["react-syntax-highlighter/dist/esm/styles/hljs" :as hljs]
            [goog.object :as gobj]))

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
      [:div {:style {:display "flex"
                     :background "rgba(200,200,200, 0.3)"
                     :padding-left "10px"}
             :on-click #(set-collapsed not)}
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
    [:div
     (->> ts
          (tc/from-long)
          (t/to-default-time-zone)
          (tf/unparse ts-format))]
    [:div {:style {:flex 1
                   :overflow "hidden"}}
     [Code {:value value :pprint? selected?}]]
    #_[:div {:style {:flex 2}} metadata]]])


(defnc TapList [{:keys [entries inspect-value]}]
  (let [[selected update-selected] (alpha/useStateOnce nil ::tap-list.selected)]
    [:div {:class "taplist"
           ;; :style {:overflow "auto"
           ;;         :min-height "0px"}
           }
     (for [entry entries]
       (let [{:keys [id]} entry]
         [TapEntry (assoc entry
                          :key id
                          :selected? (= selected id)
                          :on-select #(if (= selected id)
                                        (update-selected nil)
                                        (update-selected id))
                          :on-inspect #(inspect-value entry))]))]))

(def views
  {:punk.view/tree TreeView
   :punk.view/code CodeView})

(defnc Inspector [{:keys [value on-next on-back on-breadcrumb]}]
  (let [[current-view set-current-view] (hooks/useState :punk.view/tree)]
    (prn current-view)
    [:div {:class "inspector"
           ;; :style {:display "flex"
           ;;         :flex-direction "column"}
           :style {:position "relative"}
           }
     [:div {:style {:flex 1
                    :min-height "0px"
                    :overflow-y "scroll"}}
      [(views current-view) {:value value}]]
     [:div {:style {:background "#ffffef"
                    :height "35px"
                    :position "absolute"
                    :bottom 0
                    :width "100%"}}
      [:select {:value (str current-view)
                :on-change #(set-current-view (keyword (s/replace-first (-> % .-target .-value) #":" "")))}
       (for [view-id (keys views)]
         [:option (str view-id)])]]]))


(defnc TopControls [_]
  (let [[_ set-route] (hooks/useContext routing-context)]
    [:div {:style {:height "100%"
                   :display "flex"}}
     [:button {:on-click #(set-route :tap-list)} "Taps"]
     [:button {:on-click #(set-route :inspector)} "Inspector"]
     #_[:button "Settings"]]))



(defn useChan [chan on-take on-close]
  (let [cleanup? (hooks/useIRef false)]
    (hooks/useEffect
     (fn []
       (prn "Channel open")
       (a/go-loop []
         (if @cleanup?
           (on-close)
           (if-let [v (a/<! chan)]
             (do (on-take v)
                 (recur))
             (on-close))))
       #(reset! cleanup? true))
     [chan on-take on-close])))

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

(defnc Main [{:keys [initial-taps tap-chan]}]
  (let [[router update-router] (hooks/useState {:current :tap-list})
        [tap-list update-taps] (hooks/useReducer taps-reducer (or initial-taps '()))
        [inspected-value update-inspected] (hooks/useState nil)
        set-route #(update-router assoc :current %)]
    (js/console.log tap-list)
    (useChan tap-chan
             update-taps
             on-close)
    [:provider {:context routing-context
                :value [router set-route]}
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
       (case (:current router)
         :tap-list [TapList {:entries tap-list
                             :inspect-value #(do (update-inspected (:value %))
                                                 (set-route :inspector))}]
         :inspector [Inspector {:value inspected-value}])]]]))
