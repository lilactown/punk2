(ns punk.ui.lib
  (:require [hx.hooks :as hooks]
            [hx.hooks.alpha :as alpha]
            [datascript.core :as ds]
            [cljs.core.async :as a]))

(defn useDataScript [schema init]
  ;; figure out a way to reload on schema / init change...
  (let [[[db tx-info] set-db] (alpha/useStateOnce
                               #(let [db (ds/empty-db schema)]
                                  ;; (swap! alpha/states assoc ::app-db conn)
                                  [(ds/db-with db init) nil])
                               ::app-db)
        transact (hooks/useCallback (fn [tx-data]
                                      (set-db (fn [[db _]]
                                                (let [{:keys [db-after] :as tx-info} (ds/with db tx-data)]
                                                  [db-after tx-info]))))
                                    [set-db db])]
    ;; (prn db)
    [db transact]))

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
       #(do (a/put! cleanup? true))))
   [chan on-take on-close]))
