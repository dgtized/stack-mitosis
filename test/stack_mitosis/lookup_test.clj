(ns stack-mitosis.lookup-test
  (:require [stack-mitosis.lookup :as lookup]
            [clojure.test :refer :all]))

(deftest by-id
  (let [instance {:DBInstanceIdentifier :foo}]
    (is (= instance (lookup/by-id [instance] :foo)))
    (is (= nil (lookup/by-id [instance] :bar)))))

(deftest position
  (let [instances [{:DBInstanceIdentifier "a"}
                   {:DBInstanceIdentifier "b"}
                   {:DBInstanceIdentifier "c"}]]
    (is (= 0 (lookup/position instances "a")))
    (is (= 1 (lookup/position instances "b")))
    (is (= nil (lookup/position instances "missing")))))

(deftest clone-replica-attributes
  (let [instance {:DBInstanceIdentifier "a"
                  :Iops 100
                  :VpcSecurityGroups [{:VpcSecurityGroupId "sg-abcd" :Status "available"}]
                  :PerformanceInsightsEnabled false
                  :IAMDatabaseAuthenticationEnabled false
                  :StorageType "io1"
                  :DBInstanceArn "abcdef"}]
    (is (= {:StorageType "io1"
            :Iops 100
            :VpcSecurityGroupIds ["sg-abcd"]
            :EnablePerformanceInsights false
            :EnableIAMDatabaseAuthentication false}
           (lookup/clone-replica-attributes instance)))
    (is (= {:Iops 100}
           (lookup/clone-replica-attributes {:Iops 100})))))
