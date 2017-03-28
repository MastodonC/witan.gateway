(def metrics-version "2.7.0")
(defproject witan.gateway "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/core.async "0.2.371"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/timbre "4.8.0"]
                 [com.cognitect/transit-clj "0.8.290"]
                 [cheshire "5.6.3"]
                 [clj-time "0.11.0"]
                 [http-kit "2.1.19"]
                 [ring-cors "0.1.8"]
                 [ring/ring-core "1.5.1"]
                 [compojure "1.5.1"]
                 [clj-http "2.1.0"]
                 [environ "1.1.0"]
                 [aero "1.0.0-beta5"]
                 [clj-time "0.12.0"]
                 [zookeeper-clj "0.9.4"]
                 [kixi/kixi.comms "0.2.8"]
                 [kixi/kixi.log "0.1.4"]
                 [kixi/kixi.metrics "0.4.0"]
                 [metrics-clojure ~metrics-version]
                 [metrics-clojure-jvm ~metrics-version]
                 [metrics-clojure-ring ~metrics-version]
                 [buddy/buddy-sign "1.3.0"]]
  :source-paths ["src"]
  :profiles {:uberjar {:aot  :all
                       :main witan.gateway.system
                       :uberjar-name "witan.gateway-standalone.jar"}
             :dev {:source-paths ["dev-src"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [stylefruits/gniazdo "1.0.0"]
                                  [me.raynes/fs "1.4.6"]]
                   :repl-options {:init-ns user}}}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
