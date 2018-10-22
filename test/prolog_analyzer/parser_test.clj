(ns prolog-analyzer.parser-test
  (:require [prolog-analyzer.parser :as sut]
            [clojure.test :refer :all]))


(def reference-map
  {"foo(a)" [:Fact [:Name "foo"] [:Args [:Atom "a"]]]
   "bar(b,X)" [:Fact [:Name "bar"] [:Args [:Atom "b"] [:Var "X"]]]})


(deftest parse-empty
  (testing "Parsing whitespace strings"
    (are [x] (= '() (sut/parse x))
      ""
      "\n"
      "\n\n"
      "%Normal Comment\n"
      "\n%Second Line Comment\n"
      "         "
      "\t\t\t")))

(deftest parse-with-comment
  (testing "Parsing text with comments"
    (are [x y] (= x (sut/parse y))
      '([:Fact [:Name "a"]], [:Fact [:Name "b"]])
      "a.\n%This is a comment\nb."
      '([:Fact [:Name "c"]], [:Fact [:Name "d"]], [:Fact [:Name "e"]])
      "c.%This is a comment at the end of line\n%Another comment\nd.%In-Line Comment\ne.\n\n"
      '([:Fact [:Name "f"]])
      "f.\n\n%End of File-Comment")))

(deftest parse-error
  (testing "Parsing things, that are wrong"
    (are [x] (contains? (sut/parse x) :reason)
      "a"
      "a.b."
      "a :- "
      "a :- ."
      "a(c,d"
      "a).")))

;; Test for parsing the component
(deftest parse-facts
  (testing "Simple Facts"
    (are [x y] (= x (sut/prolog-parser y :start :Fact))
      [:Fact [:Name "asdasdasd"]] "asdasdasd."
      [:Fact [:Name "foo"] [:Arglist [:Atom "a"]]] "foo(a)."
      [:Fact [:Name "bar"] [:Arglist [:Atom "b"] [:Var "X"]]] "bar(b,X).")))

(deftest parse-rules
  (testing "Simple Rules"
    (are [x y] (= x (sut/prolog-parser y :start :Rule))
      [:Rule [:Name "foo"] [:Arglist [:Atom "a"]]
       [:Goal [:Name "bar"] [:Arglist [:Atom "b"] [:Var "X"]]]]
      "foo(a) :- bar(b,X)."
      ;;
      [:Rule [:Name "foo"] [:Arglist [:Var "A"] [:Var "B"]]
       [:Goal [:Name "a"]]
       [:Semicolon]
       [:Goal [:Name "b"]]
       [:Komma]
       [:Goal [:Name "e"]]]
      "foo(A,B) :- a;b,e."
      )))

(deftest parse-directCall
  (testing "Parsing of direct calls"
    (are [x y] (= x (sut/prolog-parser y :start :DirectCall))
      [:DirectCall
       [:Goal [:Name "enable_all_specs"]]]
      ":- enable_all_specs."
      [:DirectCall
       [:Goal
        [:Name "assert"]
        [:Arglist
         [:Compound
          [:Functor "foo"] [:Arglist [:Atom "a"] [:Atom "b"]]]]]]
      ":- assert(foo(a,b))."
      )))

(deftest parse-if
  (testing "Parsing of If"
    (are [y x] (= x (sut/prolog-parser y :start :If))
      "(a -> c ; d)"
      [:If
       [:Goal [:Name "a"]]
       [:Then]
       [:Goal [:Name "c"]]
       [:Else]
       [:Goal [:Name "d"]]]
      "(a -> (f;e); x)"
      [:If
       [:Goal [:Name "a"]]
       [:Then]
       [:InBrackets
        [:Goal [:Name "f"]]
        [:Semicolon]
        [:Goal [:Name "e"]]]
       [:Else]
       [:Goal [:Name "x"]]]
      "(a -> (b -> c;d); e)"
      [:If
       [:Goal [:Name "a"]]
       [:Then]
       [:Goal
        [:If
         [:Goal [:Name "b"]]
         [:Then]
         [:Goal [:Name "c"]]
         [:Else]
         [:Goal [:Name "d"]]]]
       [:Else]
       [:Goal [:Name "e"]]]
      )))



(deftest process-rule
  (testing "testing processing of rule"
    (are [y x] (= x (sut/process-string y))
      "foo(a,X) :- c(X),d."
      '({"foo" {:arity 2
                :arglist [{:term "a", :type :atom}
                          {:term "X", :type :var}]
                :body [{:goal "c"
                        :arity 1
                        :arglist [{:term "X", :type :var}]
                        :module :user}
                       {:goal "d"
                        :arity 0
                        :arglist []
                        :module :user}]}})
      )))

(deftest transform-to-map-test
  (testing "Test, if the different components are correctly transformed to a map"
    (are [x y z] (= z (sut/transform-to-map (sut/prolog-parser x :start y)))
      "a" :Atom {:term "a", :type :atom}
      "X" :Var {:term "X", :type :var}
      "_" :Var {:term :anonymous, :type :var}
      "123" :Number {:term "123", :type, :number}
      "foo(a,b)" :Goal {:goal "foo"
                        :arity 2
                        :arglist [{:term "a" :type :atom} {:term "b" :type :atom}]
                        :module :user}
      "!" :Goal {:goal :Cut
                 :arity 0
                 :arglist []
                 :module :built-in}
      "X is Y+2" :Goal {:goal :is-assignment
                        :left {:term "X" :type :var}
                        :right [{:term "Y" :type :var}
                                {:term "2" :type :number}]
                        :module :built-in}
      "X = 3" :Goal {:goal :unify-assignment
                     :left {:term "X" :type :var}
                     :right {:term "3" :type :number}
                     :module :built-in}
      "foo(a,b,c)" :Compound {:term "foo"
                              :type :compound
                              :arity 3
                              :arglist [{:term "a" :type :atom}
                                        {:term "b" :type :atom}
                                        {:term "c" :type :atom}]
                              :infix false}
      "foo(a,bar(b,c))" :Compound {:term "foo"
                                   :type :compound
                                   :arity 2
                                   :arglist [{:term "a" :type :atom}
                                             {:term "bar"
                                              :type :compound
                                              :arity 2
                                              :arglist [{:term "b" :type :atom}
                                                        {:term "c" :type :atom}]
                                              :infix false}]
                                   :infix false}

      "A/B/C" :Compound {:term "/"
                         :type :compound
                         :arity 2
                         :arglist [{:term "A" :type :var}
                                   {:term "/" :type :compound
                                    :arity 2
                                    :arglist [{:term "B" :type :var}
                                              {:term "C" :type :var}]
                                    :infix true}]
                         :infix true}
      "[H|T]" :List {:term :list
                     :type :list
                     :head [{:term "H", :type :var}]
                     :tail {:term "T", :type :var}}
      "[1,2|T]" :List {:term :list
                       :type :list
                       :head [{:term "1" :type :number}
                              {:term "2" :type :number}]
                       :tail {:term "T" :type :var}}
      "[1,2,3]" :List {:term :list
                       :type :list
                       :content [{:term "1" :type :number}
                                 {:term "2" :type :number}
                                 {:term "3" :type :number}]}
      "[1,2|[3,4]]" :List {:term :list
                           :type :list
                           :head [{:term "1" :type :number}
                                  {:term "2" :type :number}]
                           :tail {:term :list
                                  :type :list
                                  :content [{:term "3" :type :number}
                                            {:term "4" :type :number}]}}
      "[]" :List {:term :list
                  :type :list}
      "[1]" :List {:term :list
                   :type :list
                   :content [{:term "1" :type :number}]}
      "(a,b -> c,d ; e,f)" :If {:goal :if
                                :cond [{:goal "a" :arity 0 :arglist [] :module :user}
                                       {:goal "b" :arity 0 :arglist [] :module :user}]
                                :then [{:goal "c" :arity 0 :arglist [] :module :user}
                                       {:goal "d" :arity 0 :arglist [] :module :user}]
                                :else [{:goal "e" :arity 0 :arglist [] :module :user}
                                       {:goal "f" :arity 0 :arglist [] :module :user}]}

      )))

(sut/transform-to-map (sut/prolog-parser "[1,2|L]" :start :List))
(sut/prolog-parser "[1|[2,3]]" :start :List)
(sut/prolog-parser "[1,2,3]" :start :List)
(sut/prolog-parser "[]" :start :List)
