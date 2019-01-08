(ns prolog-analyzer.analyzer.pretty-printer
  (:require [prolog-analyzer.utils :as utils]
            [ubergraph.core :as uber]
            [loom.graph]
            [loom.attr]
            [clojure.pprint :refer [pprint]]
            [clojure.string]))

(defmulti to-string :type)
(defmethod to-string :var [{n :name}] n)
(defmethod to-string :atom [{term :term}] term)
(defmethod to-string :atomic [{term :term}] term)
(defmethod to-string :integer [{value :value}] value)
(defmethod to-string :number [{value :value}] value)
(defmethod to-string :float [{value :value}] value)
(defmethod to-string :anon_var [{n :name}] n) 
(defmethod to-string :list [{head :head tail :tail :as arg}]
  (cond 
    (= "[]" (:term tail)) (str "[" (to-string head) "]")
    (= :var (:type tail)) (str "[" (to-string head) "|" (to-string tail) "]")
    (= :anon_var (:type tail)) (str "[" (to-string head) "|" (to-string tail) "]")
    (= :list (:type tail)) (str "[" (clojure.string/join ", " (map to-string (utils/get-elements-of-list arg))) "]")
    :else "blabla"))
(defmethod to-string :compound [{functor :functor arglist :arglist}]
  (str functor "(" (clojure.string/join ", " (map to-string arglist)) ")"))
(defmethod to-string :default [arg] (println arg) "yo")


(defn print-in-two-columns [n str1 str2]
  (let [diff (- n (count str1))]
    (print str1)
    (doseq [x (range 0 diff)] (print " "))
    (println str2)))

(defn print-in-columns [[n & ns] str & strs]
  (let [diff (- n (count str))]
    (print str)
    (doseq [x (range 0 diff)] (print " "))
    (if (nil? ns)
      (println (clojure.string/join " " strs))
      (apply print-in-columns ns strs))))


(defn pretty-print-graph [graph]
  (let [nodes (uber/nodes graph)
        nd-num (count nodes)
        edges (uber/edges graph)
        edg-num (count edges)]
    (println nd-num "Nodes:")
    (doseq [node nodes]
      (print "\t")
      (print-in-columns [29] (to-string node) (uber/attrs graph node)))
    (println edg-num "Edges:")
    (doseq [edge edges]
      (let [src (uber/src edge)
            dest (uber/dest edge)]
        (print "\t")
        (print-in-columns [10 4 15]
                          (to-string src)
                          "->"
                          (to-string dest)
                          (str (uber/attrs graph edge)))))))

(defn pretty-print-analysis-result [res]
  (doseq [[[clause-id pre-spec] g] res]
    (do
      (println "#" clause-id ":" pre-spec)
      (pretty-print-graph g)
      (println "--------------------------------------------------------------------\n"))))
