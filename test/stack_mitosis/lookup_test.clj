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
