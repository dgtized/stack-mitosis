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
    (is (= [:a :b :c] (c/list-tree [a b c] :a))))
  (let [a {:DBInstanceIdentifier :a :ReadReplicaDBInstanceIdentifiers [:b :c]}
        b {:DBInstanceIdentifier :b :ReadReplicaDBInstanceIdentifiers [:d]}
        c {:DBInstanceIdentifier :c :ReadReplicaDBInstanceIdentifiers []}
        d {:DBInstanceIdentifier :c :ReadReplicaDBInstanceIdentifiers []}]
    (is (= [:a :b :d :c] (c/list-tree [a b c d] :a)))))

(deftest copy-tree
  (let [instances [{:DBInstanceIdentifier "source"}
                   {:DBInstanceIdentifier "target" :ReadReplicaDBInstanceIdentifiers ["a" "b"]}
                   {:DBInstanceIdentifier "a" :ReadReplicaDBInstanceIdentifiers ["c"]
                    :ReadReplicaSourceDBInstanceIdentifier "target"}
                   {:DBInstanceIdentifier "b" :ReadReplicaSourceDBInstanceIdentifier "target"}
                   {:DBInstanceIdentifier "c" :ReadReplicaSourceDBInstanceIdentifier "b"}]]
    (is (= [{:op :CreateDBInstanceReadReplica
             :request {:SourceDBInstanceIdentifier "source"
                       :DBInstanceIdentifier "target-temp"}}
            {:op :CreateDBInstanceReadReplica,
             :request {:SourceDBInstanceIdentifier "target-temp"
                       :DBInstanceIdentifier "a-temp"}}
            {:op :CreateDBInstanceReadReplica,
             :request {:SourceDBInstanceIdentifier "b-temp"
                       :DBInstanceIdentifier "c-temp"}}
            {:op :CreateDBInstanceReadReplica,
             :request {:SourceDBInstanceIdentifier "target-temp"
                       :DBInstanceIdentifier "b-temp"}}
            {:op :PromoteReadReplica,
             :request {:DBInstanceIdentifier "target-temp"}}]
           (c/copy-tree instances "source" "target"
                        (fn [instance]
                          (-> instance
                              (update :DBInstanceIdentifier c/aliased "temp")
                              ;; fixme need to account for nil at root
                              (update :ReadReplicaSourceDBInstanceIdentifier c/aliased "temp"))))))))

(deftest rename-tree
  (let [instances [{:DBInstanceIdentifier "root" :ReadReplicaDBInstanceIdentifiers ["a" "b"]}
                   {:DBInstanceIdentifier "a" :ReadReplicaDBInstanceIdentifiers ["c"]}
                   {:DBInstanceIdentifier "b"}
                   {:DBInstanceIdentifier "c"}]]
    (is (= [{:op :ModifyDBInstance
             :request
             {:DBInstanceIdentifier "c" :NewDBInstanceIdentifier "temp-c"
              :ApplyImmediately true}}
            {:op :ModifyDBInstance
             :request
             {:DBInstanceIdentifier "b" :NewDBInstanceIdentifier "temp-b"
              :ApplyImmediately true}}
            {:op :ModifyDBInstance
             :request
             {:DBInstanceIdentifier "a" :NewDBInstanceIdentifier "temp-a"
              :ApplyImmediately true}}
            {:op :ModifyDBInstance
             :request
             {:DBInstanceIdentifier "root" :NewDBInstanceIdentifier "temp-root"
              :ApplyImmediately true}}]
           (c/rename-tree instances "root" (partial c/aliased "temp"))))))

(deftest delete-tree
  (let [instances [{:DBInstanceIdentifier "source"}
                   {:DBInstanceIdentifier "target" :ReadReplicaDBInstanceIdentifiers ["a" "b"]}
                   {:DBInstanceIdentifier "a" :ReadReplicaDBInstanceIdentifiers ["c"]
                    :ReadReplicaSourceDBInstanceIdentifier "target"}
                   {:DBInstanceIdentifier "b" :ReadReplicaSourceDBInstanceIdentifier "target"}
                   {:DBInstanceIdentifier "c" :ReadReplicaSourceDBInstanceIdentifier "b"}]]
    (is (= [{:op :DeleteDBInstance
             :request {:DBInstanceIdentifier "c" :SkipFinalSnapshot true}}
            {:op :DeleteDBInstance
             :request {:DBInstanceIdentifier "b" :SkipFinalSnapshot true}}
            {:op :DeleteDBInstance
             :request {:DBInstanceIdentifier "a" :SkipFinalSnapshot true}}
            {:op :DeleteDBInstance
             :request {:DBInstanceIdentifier "target" :SkipFinalSnapshot true}}]
           (c/delete-tree instances "target")))))
