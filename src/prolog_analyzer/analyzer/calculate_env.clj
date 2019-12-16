(ns prolog-analyzer.analyzer.calculate-env
  (:require [clojure.spec.alpha :as s]
            [orchestra.core :refer [defn-spec]]
            [orchestra.spec.test :as stest]
            [clojure.tools.logging :as log]
            [prolog-analyzer.analyzer.domain :as dom]
            [prolog-analyzer.analyzer.post-specs :as post-specs]
            [prolog-analyzer.record-utils :as ru]
            [prolog-analyzer.records :as r]
            [prolog-analyzer.state :as state]
            [prolog-analyzer.utils :as utils :refer [case+]]
            [ubergraph.core :as uber]
            [prolog-analyzer.specs :as specs]))

(def ^:private DEEPNESS 3)

(defn ^:private blabla [env term spec {initial? :initial overwrite? :overwrite}]
  (if overwrite?
    (dom/blabla-post-spec env term spec)
    (dom/blabla env initial? false term spec)))

(defn ^:private compatible-with-head [{initial? :initial :as parameters} head-dom term-dom]
  (case+ (ru/spec-type term-dom)
         r/TUPLE (let [new-dom (update term-dom :arglist #(assoc % 0 head-dom))]
                   (if (ru/non-empty-intersection new-dom term-dom initial?)
                     new-dom
                     nil))
         r/OR (let [new-dom (-> term-dom
                                (update :arglist (partial filter (partial compatible-with-head parameters head-dom)))
                                (update :arglist set)
                                (ru/simplify initial?))]
                (if (ru/non-empty-intersection new-dom term-dom initial?)
                  new-dom
                  nil))
         r/LIST (if (ru/non-empty-intersection head-dom (:type term-dom) initial?)
                  term-dom
                  nil)
         (if (ru/non-empty-intersection (r/->ListSpec head-dom) term-dom initial?)
           term-dom
           nil)))

(defn ^:private singleton-list? [term]
  (and (ru/empty-list-term? (ru/tail term))))

(defn ^:private deepness [spec]
  (case+ (ru/spec-type spec)
         (r/USERDEFINED, r/COMPOUND, r/TUPLE) (->> spec
                                                   :arglist
                                                   (map deepness)
                                                   (apply max 0)
                                                   inc)
         r/OR (->> spec
                   :arglist
                   (map deepness)
                   (apply max 0))
         r/LIST (-> spec :type deepness inc)
         1))

(defn ^:private get-matching-head [tail pair-id env]
  (let [head (some->> env
                      uber/edges
                      (filter #(= :is-head (uber/attr env % :relation)))
                      (filter #(= pair-id (uber/attr env % :pair)))
                      first
                      uber/src)]
    (assert (not (nil? head)) (str (utils/get-title env) " " (r/to-string tail)))
    head))

(defmulti ^:private process-edge (fn [_ env edge] (uber/attr env edge :relation)))

(defmethod process-edge :is-head [parameters env edge]
  (let [head (uber/src edge)
        term (uber/dest edge)
        head-dom (utils/get-dom-of-term env head)
        term-dom (utils/get-dom-of-term env term)
        filtered-dom (compatible-with-head parameters head-dom term-dom)]
    (if (singleton-list? term)
      (blabla env term (r/->TupleSpec [head-dom]) parameters)
      (blabla env term (or filtered-dom (r/DISJOINT "No term dom is compatible with head")) parameters))))

(defmethod process-edge :is-tail [parameters env edge]
  (let [tail (uber/src edge)
        term (uber/dest edge)
        tail-dom (utils/get-dom-of-term env tail)
        term-dom (utils/get-dom-of-term env term)
        head (ru/head term)
        head-dom (utils/get-dom-of-term env head)
        new-dom (case+ (ru/spec-type tail-dom)
                       r/TUPLE (update tail-dom :arglist #(->> %
                                                               (cons head-dom)
                                                               (apply vector)))
                       r/LIST (update tail-dom :type #(r/->OneOfSpec (hash-set % head-dom)))
                       term-dom)]
    (blabla env term new-dom parameters)))

(defmethod process-edge :arg-at-pos [parameters env edge]
  (let [child (uber/src edge)
        child-dom (utils/get-dom-of-term env child)
        parent (uber/dest edge)
        functor (:functor parent)
        args (count (:arglist parent))
        pos (uber/attr env edge :pos)
        new-dom (->> (r/->AnySpec)
                     (repeat args)
                     (apply vector)
                     (#(assoc % pos child-dom))
                     (apply vector)
                     (r/->CompoundSpec functor))]
    (if (> (deepness new-dom) DEEPNESS)
      env
      (blabla env parent new-dom parameters))))

(defmethod process-edge :default [defs env edge]
  env)

(defn ^:private process-edges [env parameters]
  (reduce (partial process-edge parameters) env (uber/edges env)))

(defn ^:private process-post-specs [env parameters]
  (reduce (fn [e [term spec]] (blabla e term spec (assoc parameters :overwrite true))) env (post-specs/get-next-steps-from-post-specs env)))

(defn ^:private post-process-step [env parameters]
  (-> env
      (process-edges parameters)
      (process-post-specs parameters)))

(defn ^:private post-process [env parameters]
  (loop [counter 0
         res env]
    (log/trace (utils/format-log env "Post Process - " counter))
    (let [next (post-process-step res parameters)]
      (if (utils/same? next res)
        res
        (recur (inc counter) next)))))

(defn-spec get-env-for-head ::specs/env
  "Calculates an environment from the header terms and the prespecs"
  [clause-id ::specs/clause-id, arglist ::specs/arglist, prespec-as-spec ::specs/spec]
  (log/trace (utils/format-log clause-id "Calculate env for head"))
  (let [parameters {:initial true}]
    (-> (uber/digraph)
        (utils/set-arguments arglist)
        (utils/set-title clause-id)
        (utils/change-current-step :head)
        (blabla (apply ru/to-head-tail-list arglist) prespec-as-spec parameters)
        dom/add-structural-edges
        (post-process parameters)
        )))

(defn-spec ^:private get-env-for-pre-spec-of-subgoal ::specs/env
  "Takes an environment and adds the information from the prespecs"
  [in-env ::specs/env, arglist ::specs/arglist, prespec-as-spec ::specs/spec]
  (log/trace (utils/format-log in-env "Calculate env for pre spec"))
  (let [parameters {:initial false}]
    (-> in-env
        (blabla (apply ru/to-head-tail-list arglist) prespec-as-spec parameters)
        dom/add-structural-edges
        (post-process parameters))))

(defn-spec ^:private get-env-for-post-spec-of-subgoal ::specs/env
  "Takes an environment and adds the information from the postspecs"
  [in-env ::specs/env, arglist ::specs/arglist, post-specs ::specs/post-specs]
  (log/trace (utils/format-log in-env "Calculate env for post spec"))
  (let [parameters {:initial false :overwrite true}]
    (-> in-env
        (post-specs/register-post-specs arglist post-specs)
        dom/add-structural-edges
        (post-process parameters))))

(defn-spec get-env-for-subgoal ::specs/env
  "Takes an environment and adds the information gained from the given subgoal."
  [in-env ::specs/env,
   subgoal-id ::specs/pred-id,
   arglist ::specs/arglist,
   prespec-as-spec ::specs/spec,
   post-specs ::specs/post-specs]
  (log/trace (utils/format-log in-env "Calculate env for subgoal"))
  (-> in-env
      (utils/change-current-step subgoal-id)
      (get-env-for-pre-spec-of-subgoal arglist prespec-as-spec)
      (get-env-for-post-spec-of-subgoal arglist post-specs)))
