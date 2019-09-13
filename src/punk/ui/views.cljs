(ns punk.ui.views
  (:require [hx.react :as hx :refer [defnc]]
            [hx.hooks :as hooks]
            [hx.hooks.alpha :as alpha]
            ["react" :as react]
            [goog.object :as gobj]
            [clojure.pprint]
            ["react-syntax-highlighter" :as rsh]
            ["react-syntax-highlighter/dist/esm/styles/hljs" :as hljs]))

(defn trunc
  [s n]
  (subs s 0 (min (count s) n)))

;;
;; Views
;;

(defnc Code [{:keys [value pprint? renderer]}]
  ;; {:wrap [(react/memo (fn [prev-props props]
  ;;                       (and
  ;;                        (= (gobj/get props "value")
  ;;                              (gobj/get prev-props "value"))
  ;;                        (= (gobj/get props "pprint?")
  ;;                              (gobj/get prev-props "pprint?")))))]}
  (let [val-str (if pprint?
                  (with-out-str
                    (cljs.pprint/pprint value))
                  (trunc (pr-str value) 200))]
    (if (> (count val-str) 500)
      [:pre [:code val-str]]
      [rsh/default {:language "clojure"
                    :style hljs/githubGist
                    :renderer renderer
                    :customStyle (if pprint?
                                   #js {:padding 0
                                        :background "none"}
                                   #js {:padding 0
                                        :background "none"
                                        :overflowX "hidden"})}
       val-str])))


(defnc CodeView [props]
  [:div {:style {:overflow "auto"
                 :max-height "100%"}
         :class "inspector--code-view"}
   [Code (assoc props
                :pprint? true)]])

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


