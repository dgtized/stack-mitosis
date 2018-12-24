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
