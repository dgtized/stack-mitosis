(ns stack-mitosis.predict-test
  (:require [stack-mitosis.predict :as p]
            [stack-mitosis.operations :as op]
            [clojure.test :refer :all]))

(deftest modify
  (let [instances [{:DBInstanceIdentifier "a"}
                   {:DBInstanceIdentifier "b"}]]
    (is (= [{:DBInstanceIdentifier "a"}
            {:DBInstanceIdentifier "new-name"}]
           (p/predict instances (op/rename "b" "new-name"))))
    (is (= [{:DBInstanceIdentifier "a"}
            {:DBInstanceIdentifier "b" :MultiAZ true}]
           (p/predict instances
                      {:op :ModifyDBInstance
                       :request {:DBInstanceIdentifier "b" :MultiAZ true}})))))

(deftest promote
  (let [instances [{:DBInstanceIdentifier "root"
                    :ReadReplicaDBInstanceIdentifiers ["leaf"]}
                   {:DBInstanceIdentifier "leaf"
                    :ReadReplicaSourceDBInstanceIdentifier "root"}]]
    (is (= [{:DBInstanceIdentifier "root"
             :ReadReplicaDBInstanceIdentifiers []}
            {:DBInstanceIdentifier "leaf"
             :BackupRetentionPeriod 1}]
           (p/predict instances
                      {:op :PromoteReadReplica
                       :request {:DBInstanceIdentifier "leaf"
                                 :BackupRetentionPeriod 1}})))))

(deftest create-replica
  (let [instances [{:DBInstanceIdentifier "root" :MultiAZ false}]]
    (is (= [{:DBInstanceIdentifier "root" :MultiAZ false
             :ReadReplicaDBInstanceIdentifiers ["replica"]}
            {:DBInstanceIdentifier "replica" :MultiAZ false :Port 123
             :ReadReplicaSourceDBInstanceIdentifier "root"}]
           (p/predict instances
                      {:op :CreateDBInstanceReadReplica
                       :request {:DBInstanceIdentifier "replica"
                                 :SourceDBInstanceIdentifier "root"
                                 :Port 123}})))))

(deftest delete
  (let [instances [{:DBInstanceIdentifier "a"}
                   {:DBInstanceIdentifier "b"}]]
    (is (= [{:DBInstanceIdentifier "a"}]
           (p/predict instances (op/delete "b"))))))
