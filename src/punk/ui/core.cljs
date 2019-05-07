(ns punk.ui.core
  (:require [hx.react :as hx :refer [defnc]]
            [clinch.hooks :as hooks]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            ["react-syntax-highlighter" :as rsh]
            ["react-syntax-highlighter/dist/esm/styles/hljs" :as hljs]
            [goog.object :as gobj]))

(def routing-context (hx/create-context))

(def ts-format (tf/formatters :hour-minute-second))

(def med-structure (into
                    '[:div {:style {:border "1px solid #d3d3d3"
                                    :height "100%"
                                    :display "flex"
                                    :flex-direction "column"}}
                      [:div {:style {:background "#efffff"
                                     :height "35px"}}
                       [TopControls]]
                      [:div {:style {:background "#ffefff"
                                     :flex 1}}
                       [TapList] [Asdf] [Jkl] 'Omg {:wtf 'bbq}]
                      [:div {:style {:background "#ffffef"
                                     :height "35px"}}]
                      (defnc Inspector [{:keys [value on-next on-back on-breadcrumb]}]
                        [:div {:class "inspector"
                               :style {:display "flex"
                                       :flex-direction "column"}}
                         [:div {:style {:flex 1
                                        :min-height "0px"
                                        :overflow-y "scroll"}}
                          #_[CodeView {:value value}]
                          [CollView {:value value}]]
                         [:div {:style {:background "#ffffef"
                                        :height "35px"}}
                          [:select
                           [:option ":punk.view/coll"]
                           [:option ":punk.view/code"]]]])]
                    (range 10)))

(defnc TopControls [_]
  (let [[_ set-route] (hooks/useContext routing-context)]
    [:div {:style {:height "100%"
                   :display "flex"}}
     [:button {:on-click #(set-route :tap-list)} "Taps"]
     [:button {:on-click #(set-route :inspector)} "Inspector"]
     #_[:button "Settings"]]))

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

(defnc TapEntry [{:keys [ts value metadata selected? on-click]}]
  (let [[_ set-route] (hooks/useContext routing-context)]
    [:div {:class "taplist--entry"}
     (when selected?
       [:div {:on-click #(on-click)
              :class "taplist--entry--tools"}
        #_[:button
           {:on-click on-click}
           [:span {:class "taplist--entry--tools--close-icon"}]]
        [:button
         {:on-click #(set-route :inspector)}
         [:span {:class "taplist--entry--tools--inspect-icon"}]]])
     [:div {:style {:display "flex"}
            :class ["taplist--entry--body" (when selected? "taplist--entry-selected")]
            :on-click #(when-not selected? (on-click))}
      [:div
       (->> ts
            (tc/from-long)
            (t/to-default-time-zone)
            (tf/unparse ts-format))]
      [:div {:style {:flex 1
                     :overflow "hidden"}}
       [Code {:value value :pprint? selected?}]]
      #_[:div {:style {:flex 2}} metadata]]]))

(defnc TapList [{:keys [entries]}]
  (let [[selected update-selected] (hooks/useStateOnce nil ::tap-list.selected)]
    [:div {:class "taplist"
           ;; :style {:overflow "auto"
           ;;         :min-height "0px"}
           }
     (for [[idx entry] (map-indexed #(vector %1 %2) entries)]
       [TapEntry (assoc entry
                        :key idx
                        :selected? (= selected idx)
                        :on-click #(if (= selected idx)
                                     (update-selected nil)
                                     (update-selected idx)))])]))

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

(defnc Inspector [{:keys [value on-next on-back on-breadcrumb]}]
  [:div {:class "inspector"
         :style {:display "flex"
                 :flex-direction "column"}}
   [:div {:style {:flex 1
                  :min-height "0px"
                  :overflow-y "scroll"}}
    #_[CodeView {:value value}]
    [CollView {:value value}]]
   [:div {:style {:background "#ffffef"
                  :height "35px"}}
    [:select
     [:option ":punk.view/coll"]
     [:option ":punk.view/code"]]]])

(defnc Main [{:keys [initial-taps]}]
  (let [[router update-router] (hooks/useState {:current :tap-list})
        [tap-list update-taps] (hooks/useState (or initial-taps []))
        [inspected-value update-inspected] (hooks/useState nil)]
    [:provider {:context routing-context
                :value [router #(update-router assoc :current %)]}
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
         :tap-list [TapList {:entries tap-list}]
         :inspector [Inspector {:value inspected-value}])]]]))
