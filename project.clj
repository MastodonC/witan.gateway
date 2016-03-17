(defproject witan.gateway "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.371"]
                 [clj-time "0.11.0"] ; required due to bug in `lein-ring uberwar`
                 [metosin/compojure-api "1.0.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [base64-clj "0.1.1"]
                 [com.taoensso/timbre "4.3.1"]
                 [clj-kafka "0.3.4"]
                 [cheshire "5.5.0"]
                 [http-kit "2.1.19"]]
  :uberjar-name "witan.gateway.jar"
  :source-paths ["src"]
  :profiles {:uberjar {:aot  [witan.gateway.server]
                       :main witan.gateway.server}
             :dev {:source-paths ["src" "dev-src"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :repl-options {:init-ns user}}})
