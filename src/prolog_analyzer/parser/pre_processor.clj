(ns prolog-analyzer.parser.pre-processor
  (:require [prolog-analyzer.utils :as utils :refer [case+]]
            [prolog-analyzer.records :as r]
            [prolog-analyzer.record-utils :as ru]
            [clojure.tools.logging :as log]
            [prolog-analyzer.state :as state]
            [clojure.spec.alpha :as s]
            [prolog-analyzer.specs :as specs]
            [orchestra.spec.test :as stest]
            [orchestra.core :refer [defn-spec]]
            [prolog-analyzer.parser.transform-to-records :as transform]
            [prolog-analyzer.parser.add-userdefs :as add-userdefs]
            [prolog-analyzer.parser.create-missing-annotations :as anno]
            ))

(def loaded-preds (atom {}))

(defn- set-correct-goal-module [pred->module-map {goal-name :goal arity :arity goal-module :module :as goal}]
  (cond
    (= "self" goal-module) (assoc goal :module (get pred->module-map goal-name "user"))
    (= :or goal-name) (-> goal
                          (assoc :module :built-in)
                          (update :arglist (partial map (partial map (partial set-correct-goal-module pred->module-map)))))
    (= :if goal-name) (-> goal
                          (assoc :module :built-in)
                          (update :arglist (partial map (partial map (partial set-correct-goal-module pred->module-map)))))
    :else goal))


(defn- calculate-pred-to-module-map [pred->module-maps caller-module imports]
  (let [used-modules (-> imports
                         (get caller-module)
                         (assoc "user" :all)
                         (assoc caller-module :all))
        import-all-modules (reduce-kv #(if (= :all %3) (conj %1 %2) %1) [] used-modules)
        weak-mappings (->> (select-keys pred->module-maps import-all-modules)
                           vals
                           (apply merge))
        strong-mappings (->> (select-keys pred->module-maps (keys (apply dissoc used-modules import-all-modules)))
                             (reduce-kv (fn [m k v] (assoc m k (select-keys v (get used-modules k)))) {})
                             vals
                             (apply merge))]
    (merge weak-mappings strong-mappings)))

(defn switch-map [m]
  (apply merge (for [[k v] m
                     y v]
                 {y k})))

(defn apply-to-value [f m]
  (reduce-kv #(assoc %1 %2 (f %3)) {} m))

(defn map-loaded-preds [l]
  (->> l
       (group-by first)
       (apply-to-value (partial map second))
       switch-map))

(defn calculate-pred-to-module-maps [data]
  (let [m (->> (select-keys data [:pre-specs :post-specs :preds])
               (vals)
               (mapcat keys)
               (group-by first)
               (apply-to-value map-loaded-preds))]
    (reduce #(assoc %1 %2 (calculate-pred-to-module-map %1 %2 (:imports data))) m (keys m))))

(defn fix-modules-in-clause [result [[source-module & _ :as pred-id] clause-number]]
  (update-in result [:preds pred-id clause-number :body] (partial map (partial set-correct-goal-module (get @loaded-preds source-module)))))


(defn set-correct-modules [data]
  (reset! loaded-preds (calculate-pred-to-module-maps data))
  (reduce fix-modules-in-clause data (utils/get-clause-identities data)))

(defn transform-singleton-lists [data]
  (reduce (fn [d [pred-id clause-number]] (update-in d [:singletons pred-id clause-number] #(apply vector (map r/map-to-term %)))) data (utils/get-clause-identities data)))

(defn- mark-self-calling-clause
  "Marks clauses that are calling themselves"
  [[_ pred-name arity] {body :body :as clause}]
  (if (some #(and (= pred-name (:goal %)) (= arity (:arity %))) body)
    (assoc clause :self-calling? true)
    (assoc clause :self-calling? false)))

(defn mark-self-calling-clauses [data]
  (loop [clause-ids (utils/get-clause-identities data)
         result data]
    (if-let [[pred-id clause-number] (first clause-ids)]
      (recur (rest clause-ids) (update-in result [:preds pred-id clause-number] (partial mark-self-calling-clause pred-id)))
      result)))

(defn remove-not-needed-stuff [data]
  (-> data
      (dissoc :specs)
      (dissoc :module)
      (dissoc :imports)
      ))


(s/fdef pre-process-single
    :args (s/cat :data map?)
    :ret ::specs/data)

(defn pre-process-single [data]
  (log/debug "Start Pre Process Single")
  (let [p (-> data
              mark-self-calling-clauses
              transform-singleton-lists
              transform/transform-args-to-term-records
              anno/start
              set-correct-modules
              add-userdefs/do-it
              remove-not-needed-stuff
              )]
    (log/debug "Done Pre Process Single")
    p))
