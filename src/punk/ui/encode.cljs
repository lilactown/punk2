(ns punk.ui.encode
  (:require [cljs.reader :as reader]
            [cljs.tagged-literals]))

(defn write [x]
  (prn-str x))

(defn read [x]
  (reader/read-string
    {:readers {'inst cljs.tagged-literals/read-inst
               'uuid cljs.tagged-literals/read-uuid
               'queue cljs.tagged-literals/read-queue}
               :default tagged-literal}
    x))
