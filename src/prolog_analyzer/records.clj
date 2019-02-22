(ns prolog-analyzer.records
  (:require [clojure.tools.logging :as log]
            [clojure.string]))

(declare to-string)

(defn to-arglist [list]
  (clojure.string/join ", " (map to-string list)))

(defprotocol printable
  (to-string [x]))

(defn get-elements-of-list [{head :head tail :tail}]
  (if (= "[]" (:term tail))
    (list head)
    (conj (get-elements-of-list tail) head)))


(defrecord AnyTerm [type term]
  printable
  (to-string [_] (str term)))

(defrecord GroundTerm [type term]
  printable
  (to-string [_] (str term)))

(defrecord NonvarTerm [type term]
  printable
  (to-string [_] (str term)))

(defrecord VarTerm [type name]
  printable
  (to-string [_] (str name)))

(defrecord AnonVarTerm [type name]
  printable
  (to-string [_] (str name)))

(defrecord AtomTerm [type term]
  printable
  (to-string [_] (str term)))

(defrecord AtomicTerm [type term]
  printable
  (to-string [_] (str term)))

(defrecord IntegerTerm [type value]
  printable
  (to-string [_] (str value)))

(defrecord FloatTerm [type value]
  printable
  (to-string [_] (str value)))

(defrecord NumberTerm [type value]
  printable
  (to-string [_] (str value)))

(defrecord ListTerm [type head tail]
  printable
  (to-string [x] (cond
                   (= "[]" (:term tail)) (str "[" (to-string head) "]")
                   (instance? VarTerm tail) (str "[" (to-string head) "|" (to-string tail) "]")
                   (instance? AnonVarTerm tail) (str "[" (to-string head) "|" (to-string tail) "]")
                   (instance? ListTerm tail) (str "[" (to-arglist (get-elements-of-list x)) "]"))))

(defrecord CompoundTerm [type functor arglist]
  printable
  (to-string [_] (str functor "(" (to-arglist arglist) ")")))

(defrecord Spec [spec]
  printable
  (to-string [x]
    (case spec
      :any "Any"
      :var "Var"
      :atom "Atom"
      :atomic "Atomic"
      :ground "Ground"
      :nonvar "Nonvar"
      :number "Number"
      :integer "Integer"
      :float "Float"
      :list (str "List(" (to-string (:type x)) ")")
      :tuple (str "Tuple(" (to-arglist (:arglist x)) ")")
      :exact (str "Exact(" (:value x) ")")
      :specvar (str "Specvar(" (:name x) ")")
      :compound (str (:functor x) "(" (to-arglist (:arglist x)) ")")
      :and (str "And(" (to-arglist (:arglist x)) ")")
      :one-of (str "OneOf(" (to-arglist (:arglist x)) ")")
      :user-defined (if (contains? x :arglist)
                      (str (:name x) "(" (to-arglist (:arglist x)) ")")
                      (str (:name x)))
      :error (str "ERROR: " (:reason x))
      "PRINT ERROR SPEC")))


(defn map-to-term [m]
  (case (:type m)
    :anon_var (map->AnonVarTerm m)
    :var (map->VarTerm m)
    :any (map->AnyTerm m)
    :ground (map->GroundTerm m)
    :nonvar (map->NonvarTerm m)
    :atom (map->AtomTerm m)
    :atomic (map->AtomicTerm m)
    :number (map->NumberTerm m)
    :integer (map->IntegerTerm m)
    :float (map->FloatTerm m)
    :list (map->ListTerm (-> m
                         (update :head map-to-term)
                         (update :tail map-to-term)))
    :compound (map->CompoundTerm (update m :arglist #(map map-to-term %)))
    (log/error "No case for" m "in map-to-term")))

(defn map-to-spec [m]
  (case (:spec m)
    (:var, :any, :ground, :nonvar, :atom, :atomic, :number, :integer, :float) (map->Spec m)
    :list (map->Spec (update m :type map-to-spec))
    :compound (map->Spec (update m :arglist #(map map-to-spec %)))
    (do
      (map->Spec m)
      (log/error "No case for" m "in map-to-term"))))


(defn make-term:var [name]
  (VarTerm. :var name))

(defn make-term:anon_var [name]
  (AnonVarTerm. :anon_var name))

(defn make-term:any [name]
  (AnyTerm. :any name))

(defn make-term:ground [name]
  (GroundTerm. :ground name))

(defn make-term:nonvar [name]
  (NonvarTerm. :nonvar name))

(defn make-term:atom [term]
  (AtomTerm. :atom term))

(defn make-term:atomic [term]
  (AtomicTerm. :atomic term))

(defn make-term:number [value]
  (NumberTerm. :number value))

(defn make-term:integer [value]
  (IntegerTerm. :integer value))

(defn make-term:float [value]
  (FloatTerm. :float value))

(defn make-term:list [head tail]
  (ListTerm. :list head tail))

(defn make-term:compound [functor arglist]
  (CompoundTerm. :compound functor arglist))


(defn make-spec:var []
  (Spec. :var))

(defn make-spec:atom []
  (Spec. :atom))

(defn make-spec:atomic []
  (Spec. :atomic))

(defn make-spec:integer []
  (Spec. :integer))

(defn make-spec:float []
  (Spec. :float))

(defn make-spec:number []
  (Spec. :number))

(defn make-spec:ground []
  (Spec. :ground))

(defn make-spec:nonvar []
  (Spec. :nonvar))

(defn make-spec:any []
  (Spec. :any))

(defn make-spec:list [type]
  (assoc (Spec. :list) :type type))

(defn make-spec:tuple [arglist]
  (assoc (Spec. :tuple) :arglist arglist))

(defn make-spec:exact [value]
  (assoc (Spec. :exact) :value value))

(defn make-spec:specvar [name]
  (assoc (Spec. :specvar) :name name))

(defn make-spec:compound [functor arglist]
  (-> (Spec. :compound)
      (assoc :functor functor)
      (assoc :arglist arglist)))

(defn make-spec:one-of [arglist]
  (assoc (Spec. :one-of) :arglist arglist))

(defn make-spec:and [arglist]
  (assoc (Spec. :and) :arglist arglist))

(defn make-spec:user-defined
  ([name] (assoc (Spec. :user-defined) :name name))
  ([name arglist] (-> (Spec. :user-defined)
                      (assoc :name name)
                      (assoc :arglist arglist))))

(defn make-spec:error [reason]
  (assoc (Spec. :error) :reason reason))


