(ns stack-mitosis.cli-test
  (:require [clojure.test :as t :refer [deftest is]]
            [stack-mitosis.cli :as sut]
            [stack-mitosis.interpreter :as interpreter]
            [clojure.tools.logging.test :as tlog]))

(deftest parsing
  (is (= {:source "production"
          :target "staging"
          :restart "./restart.sh"}
         (sut/parse-args ["--source" "production" "--target" "staging"
                          "--restart" "./restart.sh" "production"]))))

(defn test-db [id subnet]
  {:DBInstanceIdentifier id :DBSubnetGroup {:VpcId subnet}})

(deftest check-preconditions
  (tlog/with-log ;; Test logging for *why* path failed?
    (let [rds {}
          options {:source "s" :target "t"}
          options-with-snapshot (merge options {:restore-snapshot true})
          same-subnet [(test-db "s" "subnet") (test-db "t" "subnet")]
          different-subnet [(test-db "s" "subnet-a") (test-db "t" "subnet-b")]]
      (is (= [true nil]
             (sut/check-preconditions rds same-subnet options))
          "passes preconditions if databases exist and no snapshot requested")
      (is (= [false nil]
             (sut/check-preconditions rds [] options))
          "fails preconditions because databases do not exist")
      (with-redefs [interpreter/latest-snapshot (fn [_ _] "snapshot-id")]
        (is (= [true "snapshot-id"]
               (sut/check-preconditions rds same-subnet options-with-snapshot))
            "passes preconditions if snapshot requested for same subnet")
        (is (= [true "snapshot-id"]
               (sut/check-preconditions rds different-subnet options-with-snapshot))
            "passes preconditions with a valid snapshot-id")
        (is (= [true "snapshot-id"]
               (sut/check-preconditions rds different-subnet options))
            "passes preconditions if a different-subnet forces a snapshot and snapshot exists"))
      (with-redefs [interpreter/latest-snapshot (fn [_ _] nil)]
        (is (= [false nil]
               (sut/check-preconditions rds same-subnet options-with-snapshot))
            "fails preconditions if no snapshot-id available even with same-subnet")
        (is (= [false nil]
               (sut/check-preconditions rds different-subnet options-with-snapshot))
            "fails preconditions if no snapshot-id available")
        (is (= [false nil]
               (sut/check-preconditions rds different-subnet options))
            "fails preconditions if no snapshot-id available but required")))))
