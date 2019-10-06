(ns prolog-analyzer.specs
  (:require [prolog-analyzer.records :as r]
            [clojure.spec.alpha :as s]
            [ubergraph.core]))

(s/def ::spec r/is-spec?)
(s/def ::term r/is-term?)

(s/def ::specs (s/coll-of ::spec))

(s/def ::arglist (s/coll-of ::term))
(s/def ::arglists (s/coll-of ::arglist))

(s/def ::not-empty-arglist (s/coll-of ::term :min-count 1))

(s/def ::pre-spec (s/coll-of ::spec))
(s/def ::pre-specs (s/coll-of ::pre-spec))

(s/def ::id #(or (pos-int? %) (zero? %)))
(s/def ::type ::spec)
(s/def ::inner-guard (s/keys :req-un [::id ::type]))
(s/def ::guard (s/coll-of ::inner-guard))
(s/def ::inner-conclusion (s/coll-of ::inner-guard :min-count 1))
(s/def ::conclusion (s/coll-of ::inner-conclusion :min-count 1))
(s/def ::post-spec (s/keys :req-un [::guard ::conclusion]))
(s/def ::post-specs (s/coll-of ::post-spec))

(s/def ::pred-id (s/tuple string? string? ::id))
(s/def ::clause-id (s/tuple string? string? ::id ::id))

(s/def ::singletons (s/map-of ::pred-id (s/map-of ::id ::arglist)))
(s/def :key/pre-specs (s/map-of ::pred-id ::pre-specs))
(s/def :key/post-specs (s/map-of ::pred-id ::post-specs))


(s/def :key/goal #(or (string? %) (keyword? %)))
(s/def :key/module #(or (string? %) (keyword? %)))
(s/def :key/arity ::id)
(s/def :if-or/arglist (s/coll-of :key/body))
(s/def ::goal (s/or
               :normal (s/keys :req-un [:key/goal :key/module :key/arity ::arglist])
               :if-or (s/keys :req-un [:key/goal :key/module :key/arity :if-or/arglist])))
(s/def :key/body (s/coll-of ::goal))


(s/def :key/self-calling? boolean?)
(s/def ::clause (s/keys :req-un [::arglist :key/body]))
(s/def :key/preds (s/map-of ::pred-id (s/map-of ::id ::clause)))
(s/def ::data (s/keys :req-un [::singletons :key/pre-specs :key/post-specs :key/preds]))

(s/def ::env #(= ubergraph.core.Ubergraph (type %)))
(s/def ::envs (s/coll-of ::env))

(s/def ::step (s/tuple ::term ::spec))
(s/def ::steps (s/coll-of ::step))
