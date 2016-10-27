(ns witan.gateway.integration.base
  (:require [user :as repl]))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn cycle-system-fixture
  [a all-tests]
  (reset! a (repl/go))
  (Thread/sleep 2000)
  (all-tests)
  (repl/stop)
  (reset! a nil))

(defn wait-for-pred
  ([p]
   (wait-for-pred p 100))
  ([p tries]
   (wait-for-pred p tries 500))
  ([p tries ms]
   (loop [try tries]
     (when (and (pos? try) (not (p)))
       (Thread/sleep ms)
       (recur (dec try))))))
