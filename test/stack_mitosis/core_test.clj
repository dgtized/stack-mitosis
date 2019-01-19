(ns stack-mitosis.core-test
  (:require [stack-mitosis.core :as c]
            [clojure.test :refer :all]
            [stack-mitosis.operations :as op]))

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
    (is (= [(op/create-replica "source" "temp-target")
            (op/create-replica "temp-target" "temp-a")
            (op/create-replica "temp-b" "temp-c") ;; out of order, b-temp doesn't exist yet
            (op/create-replica "temp-target" "temp-b")
            (op/promote "temp-target")]
           (c/copy-tree instances "source" "target" (partial c/transform "temp"))))))

(deftest rename-tree
  (let [instances [{:DBInstanceIdentifier "root" :ReadReplicaDBInstanceIdentifiers ["a" "b"]}
                   {:DBInstanceIdentifier "a" :ReadReplicaDBInstanceIdentifiers ["c"]}
                   {:DBInstanceIdentifier "b"}
                   {:DBInstanceIdentifier "c"}]]
    (is (= [(op/rename "b" "temp-b")
            (op/rename "c" "temp-c")
            (op/rename "a" "temp-a")
            (op/rename "root" "temp-root")]
           (c/rename-tree instances "root" (partial c/aliased "temp"))))))

(deftest delete-tree
  (let [instances [{:DBInstanceIdentifier "source"}
                   {:DBInstanceIdentifier "target" :ReadReplicaDBInstanceIdentifiers ["a" "b"]}
                   {:DBInstanceIdentifier "a" :ReadReplicaDBInstanceIdentifiers ["c"]
                    :ReadReplicaSourceDBInstanceIdentifier "target"}
                   {:DBInstanceIdentifier "b" :ReadReplicaSourceDBInstanceIdentifier "target"}
                   {:DBInstanceIdentifier "c" :ReadReplicaSourceDBInstanceIdentifier "b"}]]
    (is (= [(op/delete "b")
            (op/delete "c")
            (op/delete "a")
            (op/delete "target")]
           (c/delete-tree instances "target")))))

(deftest replace-tree
  (let [instances [{:DBInstanceIdentifier "production"}
                   {:DBInstanceIdentifier "staging" :ReadReplicaDBInstanceIdentifiers ["staging-replica"]}
                   {:DBInstanceIdentifier "staging-replica" :ReadReplicaSourceDBInstanceIdentifier "staging"}]]
    (is (= [(op/create-replica "production" "temp-staging")
            (op/create-replica "temp-staging" "temp-staging-replica")
            (op/promote "temp-staging")
            (op/rename "staging-replica" "old-staging-replica")
            (op/rename "staging" "old-staging")
            (op/rename "temp-staging-replica" "staging-replica")
            (op/rename "temp-staging" "staging")
            (op/delete "old-staging-replica")
            (op/delete "old-staging")]
           (c/replace-tree instances "production" "staging")))))
