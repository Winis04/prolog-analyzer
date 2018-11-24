(defproject prolog-analyzer "0.1.1"
  :description "A library for analyzing clojure code"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [instaparse "1.4.9"]
                 [org.clojure/tools.logging "0.4.1"]]
  :main ^:skip-aot prolog-analyzer.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
