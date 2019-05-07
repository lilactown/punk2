(ns punk.ui.devcards
  (:require [devcards.core :as dc :include-macros true]
            [hx.react :as hx :refer [defnc]]
            [clinch.hooks :as hooks]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            ["react-syntax-highlighter" :as rsh]
            ["react-syntax-highlighter/dist/esm/styles/hljs" :as hljs]
            [goog.object :as gobj]
            [punk.ui.core :as core]))

(devcards.core/start-devcard-ui!)

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

(dc/defcard main
  (hx/f [:div {:style {:height "300px"}
               :class "punk-body-container"}
         [core/Main]]))

(dc/defcard tap-list
  (hx/f [:div {:style {:height "300px"}
               :class "punk-body-container"}
         ;; [TapEntry ]
         ;; [TapEntry ]
         ;; [TapEntry ]
         [core/TapList {:entries [{:ts 1555279100481 :value med-structure :metadata nil}
                                  {:ts 1555276265237 :value {:foo 'bar} :metadata nil}
                                  {:ts 1555274864007 :value {:foo 'bar} :metadata nil}]}]]))

(dc/defcard inspector
  (hx/f [:div {:style {:height "300px"}
               :class "punk-body-container"}
         [core/Inspector {:value med-structure}]]))

(dc/defcard code-view
  (hx/f [:div {:style {:height "300px"}
               :class "punk-body-container"}
         [core/CodeView {:value med-structure}]]))

(dc/defcard coll-view
  (hx/f [:div {:style {:height "300px"}
               :class "punk-body-container"}
         [core/CollView {:value med-structure}]]))
