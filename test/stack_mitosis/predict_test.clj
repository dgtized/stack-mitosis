(ns stack-mitosis.predict-test
  (:require [stack-mitosis.predict :as p]
            [clojure.test :refer :all]))

(deftest modify
  (let [instances [{:DBInstanceIdentifier "a"}
                   {:DBInstanceIdentifier "b"}]]
    (is (= [{:DBInstanceIdentifier "a"}
            {:DBInstanceIdentifier "new-name"}]
           (p/predict instances
                      {:op :ModifyDBInstance
                       :request {:DBInstanceIdentifier "b"
                                 :NewDBInstanceIdentifier "new-name"}})))
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
