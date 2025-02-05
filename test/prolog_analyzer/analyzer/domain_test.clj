(ns prolog-analyzer.analyzer.domain-test
  (:require [prolog-analyzer.analyzer.domain :as sut]
            [prolog-analyzer.records :as r]
            [prolog-analyzer.record-utils :as ru]
            [prolog-analyzer.utils :as utils]
            [ubergraph.core :as uber]
            [prolog-analyzer.state :refer [user-typedefs]]
            [prolog-analyzer.test-helper :refer [to-term to-spec]]
            [midje.sweet :refer :all]))

(facts
 "Tuple in Add-to-dom"
 (fact
  "Simple Tuple"
  (-> (uber/digraph)
      (sut/add-to-dom (ru/to-head-tail-list (r/->VarTerm "E") (r/->VarTerm "F")) (r/->TupleSpec [(r/->AtomSpec) (r/->IntegerSpec)]))
      utils/env->map)
  => (contains {"E" "Atom"
                "F" "Integer"}))
 (fact
  "Complex Tuple"
  (-> (uber/digraph)
      (sut/add-to-dom
       (ru/to-head-tail-list (r/->VarTerm "E") (r/->VarTerm "F"))
       (r/->TupleSpec [(r/->OneOfSpec #{(r/->ListSpec (r/->FloatSpec)), (r/->ListSpec (r/->ErrorSpec "NO!"))}) (r/->AndSpec #{(r/->GroundSpec) (r/->ListSpec (r/->AnySpec))})]))
      utils/env->map)
  => (contains {"E" "List(Float)"
                "F" "List(Ground)"}))
 (fact
  "Tuples in Or"
  (-> (uber/digraph)
      (sut/add-to-dom
       (ru/to-head-tail-list (r/->VarTerm "E") (r/->VarTerm "F"))
       (r/->OneOfSpec #{(r/->TupleSpec [(r/->AtomSpec) (r/->IntegerSpec)]) (r/->TupleSpec [(r/->ErrorSpec "NO!") (r/->FloatSpec)])}))
      utils/env->map)
  => (contains {"E" "Atom"
                "F" "Integer"}))
 (fact
  "Empty List"
  (-> (uber/digraph)
      (sut/add-to-dom
       (r/->EmptyListTerm)
       (r/->TupleSpec []))
      utils/env->map)
  => (contains {"[]" "EmptyList"}))
 (fact
  "Empty List"
  (-> (uber/digraph)
      (sut/add-to-dom
       (r/->EmptyListTerm)
       (r/->TupleSpec [(r/->FloatSpec)]))
      utils/env->map)
  => (contains {"[]" "ERROR: No valid intersection of EmptyList and Tuple(Float)"}))


 )

(fact
 (-> (uber/digraph)
     (sut/add-to-dom
      true
      false
      (r/->ListTerm (r/->VarTerm "X") (r/->EmptyListTerm))
      (r/->TupleSpec [(r/->AtomSpec)]))
     utils/env->map)
 => (contains {"X" "Atom"}))


(def tree
  (r/make-spec:user-defined "tree" [(r/->SpecvarSpec "X")]))

(defn set-userdefs [env]
  (reset!
   user-typedefs
   {tree (r/->OneOfSpec #{(r/->ExactSpec "empty")
                          (r/->CompoundSpec "node" [tree (r/->SpecvarSpec "X") tree])})})
  env)

(facts
 "Userdefined"
 (fact
  "simple Example"
  (-> (uber/digraph)
      set-userdefs
      (sut/add-to-dom (r/->VarTerm "X") (r/make-spec:user-defined "tree" [(r/->IntegerSpec)]))
      utils/env->map)
  => {"X" "OneOf(Compound(node(tree(Integer), Integer, tree(Integer))), Exact(empty))"})
 (fact
  "Unknown userdef"
(-> (uber/digraph)
    set-userdefs
    (sut/add-to-dom (r/->VarTerm "X") (r/make-spec:user-defined "bla" [(r/->IntegerSpec)]))
    utils/env->map)
=> {"X" "Any"})
 (fact
  "Compound with userdef"
  (-> (uber/digraph)
      set-userdefs
      (sut/add-to-dom (r/->CompoundTerm "node" [(r/->AtomTerm "empty") (r/->IntegerTerm 3) (r/->AtomTerm "empty")]) (r/make-spec:user-defined "tree" [(r/->IntegerSpec)]))
      utils/env->map)
  => {"Compound(node(empty, 3, empty))" "Compound(node(Exact(empty), Integer, Exact(empty)))"
      "empty" "Exact(empty)"
      "3" "Integer"}
  ))

(facts
 "Overwrite"
 (fact "Var -> Int"
       (-> (uber/digraph)
           (uber/add-nodes-with-attrs [(r/->CompoundTerm "foo" [(r/->VarTerm "X")]) {:dom (r/->CompoundSpec "foo" [(r/->VarSpec)])}]
                                      [(r/->VarTerm "X") {:dom (r/->VarSpec)}])
           (sut/add-to-dom false true (r/->CompoundTerm "foo" [(r/->VarTerm "X")]) (r/->CompoundSpec "foo" [(r/->IntegerSpec)]))
           utils/env->map)
       => (contains {"X" "Integer"})
       (-> (uber/digraph)
           (uber/add-nodes-with-attrs [(r/->VarTerm "X") {:dom (r/->VarSpec)}])
           (sut/add-to-dom false true (r/->VarTerm "X") (r/->IntegerSpec))
           utils/env->map)
       => (contains {"X" "Integer"})
       )
 (fact "Any -> Var"
       (-> (uber/digraph)
           (uber/add-nodes-with-attrs [(r/->CompoundTerm "foo" [(r/->VarTerm "X")]) {:dom (r/->CompoundSpec "foo" [(r/->AnySpec)])}]
                                      [(r/->VarTerm "X") {:dom (r/->AnySpec)}])
           (sut/add-to-dom false true (r/->CompoundTerm "foo" [(r/->VarTerm "X")]) (r/->CompoundSpec "foo" [(r/->VarSpec)]))
           utils/env->map)
       => (contains {"X" "Var"})
       (-> (uber/digraph)
           (uber/add-nodes-with-attrs [(r/->VarTerm "X") {:dom (r/->AnySpec)}])
           (sut/add-to-dom false true (r/->VarTerm "X") (r/->VarSpec))
           utils/env->map)
       => (contains {"X" "Var"})
       )
 (fact "Var -> Var"
       (-> (uber/digraph)
           (uber/add-nodes-with-attrs [(r/->CompoundTerm "foo" [(r/->VarTerm "X")]) {:dom (r/->CompoundSpec "foo" [(r/->VarSpec)])}]
                                      [(r/->VarTerm "X") {:dom (r/->VarSpec)}])
           (sut/add-to-dom false true (r/->CompoundTerm "foo" [(r/->VarTerm "X")]) (r/->CompoundSpec "foo" [(r/->VarSpec)]))
           utils/env->map)
       => (contains {"X" "Var"})
       (-> (uber/digraph)
           (uber/add-nodes-with-attrs [(r/->VarTerm "X") {:dom (r/->VarSpec)}])
           (sut/add-to-dom false true (r/->VarTerm "X") (r/->VarSpec))
           utils/env->map)
       => (contains {"X" "Var"})
       )
 )
