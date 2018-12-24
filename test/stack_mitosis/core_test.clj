(ns stack-mitosis.core-test
  (:require [stack-mitosis.core :as c]
            [clojure.test :refer :all]))

(deftest replicate-tree
  (is (= [{:op :CreateDBInstanceReadReplica,
           :request {:SourceDBInstanceIdentifier "root", :DBInstanceIdentifier "a"}}
          {:op :CreateDBInstanceReadReplica,
           :request {:SourceDBInstanceIdentifier "a", :DBInstanceIdentifier "b"}}
          {:op :CreateDBInstanceReadReplica,
           :request {:SourceDBInstanceIdentifier "b", :DBInstanceIdentifier "c"}}
          {:op :PromoteReadReplica, :request {:DBInstanceIdentifier "a"}}]
         (c/replicate-tree "root" ["a" "b" "c"]))))

(deftest instances-by-id
  (let [instance {:DBInstanceIdentifier :foo :MultiAZ true}]
    (is (= instance (c/instance-by-id [instance] :foo)))
    (is (= nil (c/instance-by-id [instance] :bar)))))

(deftest list-tree
  (let [a {:DBInstanceIdentifier :a :ReadReplicaDBInstanceIdentifiers [:b]}
        b {:DBInstanceIdentifier :b :ReadReplicaDBInstanceIdentifiers [:c]}
        c {:DBInstanceIdentifier :c :ReadReplicaDBInstanceIdentifiers []}]
    (is (= [:a :b :c] (c/list-tree [a b c] :a)))))
