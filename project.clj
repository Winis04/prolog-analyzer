(defproject prolog-analyzer "1.0.0"
  :description "A library for analyzing clojure code"
  :url "https://github.com/isabelwingen/prolog-analyzer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.logging "0.4.1"]
                 [midje "1.9.9"]
                 [org.clojure/data.json "0.2.6"]
                 [ubergraph "0.5.2"]
                 [tableflisp "0.1.0"]]
  :main ^:skip-aot prolog-analyzer.core
  :target-path "target/%s"
  :jvm-opts ["-Xms2g" "-Xmx6g"]
  :profiles {:uberjar {:aot :all}})
