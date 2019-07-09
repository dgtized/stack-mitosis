(ns stack-mitosis.interpreter-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging.test :as tlog]
            [stack-mitosis.interpreter :as sut]
            [stack-mitosis.operations :as op]))

(defn- eval-plan [ops]
  (tlog/with-log
    (let [r (with-out-str (sut/evaluate-plan nil ops))]
      {:output (str/split-lines r)
       :logging (map :message (tlog/the-log))})))

(deftest evaluate-plan
  (with-redefs [sut/databases (fn [x] [])]
    (is (= {:output
            ["  exit: 1"]
            :logging
            ["Invoking {:op :shell-command, :request {:cmd \"false\"}}"
             "Executing [false] failed with status 1"]}
           (eval-plan [(op/shell-command "false")
                       (op/shell-command "true")]))
        "early exit")
    (is (= {:output
            ["1" "  exit: 0"
             "2" "  exit: 0"]
            :logging
            ["Invoking {:op :shell-command, :request {:cmd \"echo 1\"}}"
             "{:ok Executing [echo 1] succeeded}"
             "Invoking {:op :shell-command, :request {:cmd \"echo 2\"}}"
             "{:ok Executing [echo 2] succeeded}"]}
           (eval-plan [(op/shell-command "echo 1")
                       (op/shell-command "echo 2")]))
        "execute all")))
