(ns stack-mitosis.request-test
  (:require [stack-mitosis.request :as r]
            [clojure.test :refer :all]
            [stack-mitosis.operations :as op]))

(deftest explain
  (is (= ":ModifyDBInstance              foo\n\t{:ApplyImmediately true, :NewDBInstanceIdentifier \"bar\"}"
         (r/explain (op/rename "foo" "bar")))))

