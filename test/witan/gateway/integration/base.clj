(ns witan.gateway.integration.base
  (:require [user :as repl]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]))

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
   (wait-for-pred p (or (env :wait-tries) 65)))
  ([p tries]
   (wait-for-pred p tries (or (env :wait-ms) 500)))
  ([p tries ms]
   (log/info "Waiting for predicate -" tries "x" ms)
   (loop [try tries]
     (when (and (pos? try) (not (p)))
       (Thread/sleep ms)
       (recur (dec try))))))
