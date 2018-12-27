(ns stack-mitosis.helpers-test
  (:require [stack-mitosis.helpers :as h]
            [clojure.test :refer :all]))

(deftest topo
  (is (= [:c :d :b :a]
         (h/topological-sort {:a #{:b :d} :b #{:c}})))
  (is (= nil (h/topological-sort {:a #{:b :d :a} :b #{:c}}))))
