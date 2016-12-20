(ns witan.gateway.components.query-router-test
  (:require [witan.gateway.components.query-router :refer :all]
            [witan.gateway.protocols    :as p]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]))

#_(deftest query
    (let [qr (component/start (->QueryRouter {}))]
      (is (= {:test-query {:foo/bar "Hello" :hello/world "World"}}
             (p/route-query qr {:test-query [:foo/bar :hello/world]})))
      (component/stop qr)))
