(ns punk.ui.encode
  (:require [cljs.reader :as reader]))

(defn write [x]
  (prn-str x))

(defn read [x]
  (reader/read-string x))
