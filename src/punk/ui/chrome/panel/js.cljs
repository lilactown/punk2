(ns punk.ui.chrome.panel.js)

(def cljs? "Is the web page a CLJS app?"
  "!!cljs.core.tap_GT_")

(defn punk
  ([] "__PUNK__")
  ([& xs] (apply str "__PUNK__" (map #(str "." (name %)) xs))))

(defn kw [x]
  (str "cljs.core.keyword(" x ")"))

(def setup "Setup for punk listening"
  (str "
console.log(\"setting up\");
" (punk) " = {};
" (punk :currentId) " = 0;
" (punk :db) " = [];
" (punk :tapFn) " = function tapFn(x) {
  var entry = cljs.core.vector(
                " (kw "\"punk/tap\"") ",
                " (punk :currentId) ",
                 x,
                 cljs.core.hash_map(
                   " (kw "\"ts\"") ", Date.now(),
                   " (kw "\"meta\"") ", cljs.core.meta(x)
                 ));
  " (punk :db :push) "(entry);
  " (punk :currentId) "++;
  window.postMessage({ data: cljs.core.pr_str(entry), source: \"punk\" });
};
"))

(def add-tap! "Adds a tap listener to the page"
  (str "
console.log(\"adding tap\");
cljs.core.add_tap(" (punk :tapFn) ");
"))
