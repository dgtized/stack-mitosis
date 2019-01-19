(ns stack-mitosis.lookup-test
  (:require [stack-mitosis.lookup :as lookup]
            [clojure.test :refer :all]))

(deftest by-id
  (let [instance {:DBInstanceIdentifier :foo}]
    (is (= instance (lookup/by-id [instance] :foo)))
    (is (= nil (lookup/by-id [instance] :bar)))))
