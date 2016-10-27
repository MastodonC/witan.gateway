(defproject witan.gateway "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [org.clojure/core.async "0.2.371"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/timbre "4.7.4"]
                 [clj-time "0.11.0"]
                 [cheshire "5.5.0"]
                 [http-kit "2.1.19"]
                 [ring-cors "0.1.8"]
                 [compojure "1.5.1"]
                 [clj-http "2.1.0"]
                 [aero "1.0.0-beta5"]
                 [clj-time "0.12.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [kixi/kixi.comms "0.1.11" :exclusions [cheshire]]]
  :source-paths ["src"]
  :profiles {:uberjar {:aot  :all
                       :main witan.gateway.system
                       :uberjar-name "witan.gateway-standalone.jar"}
             :dev {:source-paths ["dev-src"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [stylefruits/gniazdo "1.0.0"]]
                   :repl-options {:init-ns user}}})
