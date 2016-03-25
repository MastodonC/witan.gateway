(defproject witan.gateway "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.371"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/timbre "4.3.1"]
                 [clj-time "0.11.0"]
                 [clj-kafka "0.3.4"]
                 [cheshire "5.5.0"]
                 [http-kit "2.1.19"]
                 [cc.qbits/alia-all "3.1.3"]
                 [cc.qbits/hayt "3.0.0"]
                 [metosin/compojure-api "1.0.0"]
                 [clj-http "2.1.0"]
                 [aero "1.0.0-beta5"]]
  :source-paths ["src"]
  :main witan.gateway.system
  :profiles {:uberjar {:aot  [witan.gateway.system]
                       :main witan.gateway.system
                       :uberjar-name "witan.gateway-standalone.jar"}
             :dev {:source-paths ["dev-src"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [ring/ring-mock "0.3.0"]]
                   :repl-options {:init-ns user}}})
