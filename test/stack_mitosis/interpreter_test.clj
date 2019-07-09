(ns stack-mitosis.interpreter-test
  (:require [stack-mitosis.interpreter :as sut]
            [clojure.test :refer :all]
            [stack-mitosis.operations :as op]
            [clojure.string :as str]))

(defn- clean-logging [output]
  (map #(str/replace-first % #".*\|.*\|.*\| " "")
       (str/split-lines output)))

(defn- eval-plan [ops]
  (clean-logging (with-out-str (sut/evaluate-plan nil ops))))

(deftest evaluate-plan
  (with-redefs [sut/databases (fn [x] [])]
    (testing "early exit"
      (is (= ["Invoking {:op :shell-command, :request {:cmd \"false\"}}"
              "  exit: 1"
              "Executing [false] failed with status 1"]
             (eval-plan [(op/shell-command "false")
                         (op/shell-command "true")]))))
    (testing "execute all"
      (is (= ["Invoking {:op :shell-command, :request {:cmd \"true\"}}"
              "  exit: 0"
              "{:ok Executing [true] succeeded}"
              "Invoking {:op :shell-command, :request {:cmd \"true\"}}"
              "  exit: 0"
              "{:ok Executing [true] succeeded}"]
             (eval-plan [(op/shell-command "true")
                         (op/shell-command "true")]))))))
