(ns medley)

(defn- editable? [coll]
  (instance? clojure.lang.IEditableCollection coll))

(defn- reduce-map [f coll]
  (if (editable? coll)
    (persistent! (reduce-kv (f assoc!) (transient (empty coll)) coll))
    (reduce-kv (f assoc) (empty coll) coll)))


(defn map-vals
  "Maps a function over the values of an associative collection."
  [f coll]
  (reduce-map (fn [xf] (fn [m k v] (xf m k (f v)))) coll))
