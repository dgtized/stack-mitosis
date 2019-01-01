(ns stack-mitosis.wait
  (:require [clojure.core.async :as a]))

(defn block [time]
  (Thread/sleep time)
  (println "waited" time)
  (let [state (rand-nth (concat (repeat 3 :done)
                                (repeat 6 :in-progress)
                                (repeat 1 :failed)))]
    (some #{:done :failed} [state])))

(defn waiter [pred-fn delay max-attempts]
  (let [completed (a/chan)]
    (a/go-loop [attempt 0]
      (a/<! (a/timeout delay))
      (if-let [resp (pred-fn)]
        (a/>! completed resp)
        (if (< attempt max-attempts)
          (recur (inc attempt))
          (a/>! completed :max-attempts))))
    completed))

(defn transition-to
  "Maps current rds to status to cases to retry, fail or completed.

  From https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.DBInstance.Status.html"
  [state]
  (condp contains? (get state :DBInstanceStatus)
    #{"backing-up" "backtracking" "configuring-enhanced-monitoring"
      "configuring-iam-database-auth" "configuring-log-exports"
      "converting-to-vpc" "creating" "deleting" "maintenance" "modifying"
      "moving-to-vpc" "rebooting" "renaming" "resetting-master-credentials"
      "starting" "stopping" "storage-optimization" "upgrading"}
    :in-progress
    #{"failed" "inaccessible-encryption-credentials" "incompatible-credentials"
      "incompatible-network" "incompatible-option-group"
      "incompatible-parameters" "incompatible-restore" "restore-error"
      "storage-full"}
    :failed
    #{"stopped" "available"}
    :done
    ;; handle unknown?
    ))

(comment
  (a/<!! (waiter #(block 10) 100 5)))

;; (defn poll-until [predicate {:timeout 1000 :delay 100}])
