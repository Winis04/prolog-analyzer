(ns prolog-analyzer.analyzer.global-analysis
  (:require [prolog-analyzer.state :as state]
            [prolog-analyzer.utils :as utils :refer [case+]]
            [prolog-analyzer.record-utils :as ru]
            [prolog-analyzer.records :as r]
            [clojure.tools.logging :as log]
            [prolog-analyzer.parser :as parser]
            [prolog-analyzer.analyzer.core :as clause-analysis]))

(defn- log-if-empty [data]
  (when (empty? (utils/get-pred-identities data))
    (println (pr-str "No predicates found"))))

(defn- group-envs-by-pred-id [envs]
  (group-by #(vec (drop-last (utils/get-title %))) envs))

(defn- create-single-conclusion [env]
  (->> env
       utils/get-arguments
       (map-indexed (fn [i elem] {:id i :type (ru/simplify (utils/get-dom-of-term env elem))}))
       vec))

(defn- create-post-spec [envs]
  (->> envs
       (map create-single-conclusion)
       vec
       (hash-map :guard [] :conclusion)))

(defn contains-postspec? [data pred-id post-spec]
  (contains? (set (utils/get-post-specs pred-id data)) post-spec))

(defn length-of-post-spec [{guard :guard concl :conclusion}]
  (let [a (->> guard
               (map :type)
               (map r/length)
               (apply +))
        b (->> concl
               (map (partial map :type))
               (map (partial map r/length))
               (map (partial apply +))
               (apply +))]
    (+ a b)))


(defn add-if-new [data [_ _ arity :as pred-id] post-spec]
  (update-in data [:post-specs pred-id] #(-> %
                                             drop-last
                                             vec
                                             (conj post-spec)
                                             vec)))

(defn- create-new-data [in-data envs]
  (->> envs
       group-envs-by-pred-id
       (reduce-kv #(assoc %1 %2 (create-post-spec %3)) {})
       (reduce-kv add-if-new in-data)))

(defn post-spec-to-string [{guard :guard concl :conclusion}]
  (let [a (->> guard
               (map #(str "$" (:id %) ":" (r/to-string (:type %))))
               (clojure.string/join ", "))
        b (->> concl
               (map (partial map #(str "$" (:id %) ":" (r/to-string (:type %)))))
               (map (partial clojure.string/join ", "))
               (clojure.string/join "\n\t"))]
    (str "\nguard:\n\t" a "\n\n" "concl:\n\t" b "\n")))

(defn check-post-specs [data]
  (doseq [x (utils/get-pred-identities data)]
    (log/debug (utils/format-log x "Check - Number of Postspecs: " (count (utils/get-post-specs x data))))
    (doseq [y (utils/get-post-specs x data)]
      (log/debug (utils/format-log x "Check - Length: " (length-of-post-spec y)))
      (log/debug (utils/format-log x "Check - As String: " (post-spec-to-string y)))))
  data)



(defn fixpoint [writer counter in-data]
  (log/info "Fixpoint: Step " counter)
  (let [envs (clause-analysis/complete-analysis in-data)
        new-data (create-new-data in-data envs)]
    (check-post-specs new-data)
    (if (= in-data new-data)
      (do
        (log/info "Done")
        new-data)
      (recur writer (inc counter) new-data))))

(defn- dummy-post-spec [arity]
  (hash-map :guard [] :conclusion [(vec (map #(hash-map :id % :type (r/->AnySpec)) (range 0 arity)))]))

(defn- add-dummy-post-specs [data]
  (reduce (fn [d [_ _ arity :as pred-id]] (update-in d [:post-specs pred-id] #(vec (conj % (dummy-post-spec arity))))) data (utils/get-pred-identities data)))

(defn contains-userdef [spec]
  (case+ (r/spec-type spec)
         r/USERDEFINED true
         r/LIST (contains-userdef (:type spec))
         (r/OR, r/AND, r/TUPLE, r/COMPOUND) (some contains-userdef (:arglist spec))
         false))


(defmulti create-grounded-version (fn [_ i] i))
(defmethod create-grounded-version true [spec _]
  (case+ (r/spec-type spec)
         r/USERDEFINED (ru/grounded-version spec true)
         r/VAR (r/->GroundSpec)
         (r/OR, r/AND) (-> spec
                           (update :arglist (partial map #(create-grounded-version % true)))
                           (update :arglist set))
         r/LIST (update spec :type create-grounded-version true)
         (r/TUPLE, r/COMPOUND) (-> spec
                                   (update :arglist (partial map #(create-grounded-version % true)))
                                   (update :arglist vec))
         spec))

(defmethod create-grounded-version false [spec _]
  (case+ (r/spec-type spec)
         r/USERDEFINED (ru/grounded-version spec false)
         r/VAR (r/->ErrorSpec (str "Could not ground userdefined spec " (r/to-string spec)))
         (r/OR, r/AND) (-> spec
                           (update :arglist (partial map #(create-grounded-version % false)))
                           (update :arglist set))
         r/LIST (update spec :type create-grounded-version false)
         (r/TUPLE, r/COMPOUND) (-> spec
                                   (update :arglist (partial map #(create-grounded-version % false)))
                                   (update :arglist vec))
         spec))

(defn add-grounded-userdefs []
  (reset! state/grounded {})
  (doseq [p (keys @state/user-typedefs)
          initial [true false]
          :let [v (get @state/user-typedefs p)]]
    (swap! state/user-typedefs assoc (create-grounded-version p initial) (create-grounded-version v initial))))

(defn global-analysis [writer data]
  (reset! state/user-typedefs (:specs data))
  (add-grounded-userdefs)
  (let [cleared-data (-> data (dissoc :specs) add-dummy-post-specs)]
    (log-if-empty cleared-data)
    (fixpoint writer 0 cleared-data)))
