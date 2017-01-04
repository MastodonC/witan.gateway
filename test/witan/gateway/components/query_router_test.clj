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

(deftest blob->function-test
  (let [functions {:foo/bar test-func
                   :fizz/buzz test-func2}]
    (is (fn? (blob->function functions :foo/bar 1 2 3 4)))
    (is (= 10 ((blob->function functions [:foo/bar 1 2] 3 4))))
    (is (= "123456" ((blob->function functions :fizz/buzz "1" "2" "3" "456"))))
    (is (= "124563" ((blob->function functions [:fizz/buzz "456" "3"] "1" "2"))))))
