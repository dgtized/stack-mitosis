(ns stack-mitosis.interpreter-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging.test :as tlog]
            [stack-mitosis.interpreter :as sut]
            [stack-mitosis.operations :as op]))

(defn- eval-plan [ops]
  (tlog/with-log (with-out-str (sut/evaluate-plan nil ops))
    (map :message (tlog/the-log))))

(deftest evaluate-plan
  (with-redefs [sut/databases (fn [x] [])]
    (is (= ["Invoking {:op :shell-command, :request {:cmd \"false\"}}"
            "Executing [false] failed with status 1"]
           (eval-plan [(op/shell-command "false")
                       (op/shell-command "true")]))
        "early exit")
    (is (= ["Invoking {:op :shell-command, :request {:cmd \"true\"}}"
            "{:ok Executing [true] succeeded}"
            "Invoking {:op :shell-command, :request {:cmd \"true\"}}"
            "{:ok Executing [true] succeeded}"]
           (eval-plan [(op/shell-command "true")
                       (op/shell-command "true")]))
        "execute all")))
