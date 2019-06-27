(ns stack-mitosis.operations-test
  (:require [stack-mitosis.operations :as op]
            [clojure.test :refer :all]))

(deftest polling-operation
  (is (= (op/describe "foo")
         (op/polling-operation (op/create-replica "bar" "foo"))))
  (is (= (op/describe "rename-after")
         (op/polling-operation (op/rename "rename-before" "rename-after")))))

(deftest completed?
  (is (not (op/completed? {:ErrorResponse
                           {:Error
                            {:Type "Sender",
                             :Code "DBInstanceNotFound",
                             :Message "DBInstance foo not found."},
                            :RequestId "e176cadd-9689-4119-9ea3-9762eddc965f"},
                           :ErrorResponseAttrs {:xmlns "http://rds.amazonaws.com/doc/2014-10-31/"},
                           :cognitect.anomalies/category :cognitect.anomalies/not-found})))
  (is (op/completed? {:DBInstances [{:DBInstanceStatus "available"}]})))
