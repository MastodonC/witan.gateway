(ns witan.gateway.components.query-router-test
  (:require [witan.gateway.components.query-router :refer :all]
            [clojure.test :refer :all]))

(deftest detecting-namespace
  (testing "detecting single keyword query"
    (let [r (detect-namespace
             '{:workspaces/list-by-owner
               [:workspace/name :workspace/id :workspace/owner-name :workspace/modified]})]
      (is (= :workspaces r))))
  (testing "detecting function query"
    (let [r (detect-namespace
             '{(:workspaces/list-by-owner #uuid "00000000-0000-0000-0000-000000000000")
               [:workspace/name :workspace/id :workspace/owner-name :workspace/modified]})]
      (is (= :workspaces r)))))
