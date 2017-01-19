(ns witan.gateway.components.query-router-test
  (:require [witan.gateway.components.query-router :refer :all]
            [witan.gateway.protocols    :as p]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]))

(defn test-func
  [a b c d]
  (+ a b c d))

(defn test-func2
  [a b c d]
  (str a b c d))

