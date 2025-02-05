(ns prolog-analyzer.parser.parser-test
  (:require [prolog-analyzer.parser.parser :as sut]
            [prolog-analyzer.records :as r]
            [prolog-analyzer.utils :as utils :refer [case+]]
            [prolog-analyzer.record-utils :as ru]
            [prolog-analyzer.state :as state]
            [clojure.spec.alpha :as s]
            [clojure.test :as t]
            [prolog-analyzer.specs :as specs]
            [clojure.java.io :as io]
            [midje.sweet :refer :all]))

(defn properties []
  (read-string (slurp (io/file "properties.edn"))))

(defn f [path]
  (sut/process-prolog-file (properties) path))

(facts
 (fact "About Post Specs"
       (:post-specs (f "resources/simple-example.pl"))
       =>
       (contains {["simple_example" "foo" 2]  [(r/->Postspec [] [[{:id 0 :type (r/->IntegerSpec)} {:id 1 :type (r/->IntegerSpec)}] [{:id 0 :type (r/->AtomSpec)} {:id 1 :type (r/->AtomSpec)}]])]})))
