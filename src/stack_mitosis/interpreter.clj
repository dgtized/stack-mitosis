(ns stack-mitosis.interpreter
  (:require [clojure.tools.logging :as log]
            [cognitect.aws.client.api :as aws]
            [stack-mitosis.operations :as op]
            [stack-mitosis.planner :as plan]
            [stack-mitosis.predict :as predict]
            [stack-mitosis.sudo :as sudo]
            [stack-mitosis.wait :as wait]))

;; TODO: thread this client to all that use it
(def rds (aws/client {:api :rds :credentials-provider (sudo/provider)}))

(defn databases
  [rds]
  (:DBInstances (aws/invoke rds {:op :DescribeDBInstances})))

(defn interpret [rds action]
  (log/info "Invoking " action)
  (let [{:keys [ErrorResponse] :as result} (aws/invoke rds action)]
    (if ErrorResponse
      (do
        (log/error ErrorResponse)
        result)
      (do
        (log/info result)
        (when-let [operation (op/polling-operation action)]
          (let [started (. System (nanoTime))
                ret (wait/poll-until #(op/completed? (aws/invoke rds operation))
                                     {:delay 60000 :max-attempts 60})
                msecs (/ (double (- (. System (nanoTime)) started)) 1000000.0)
                status (-> (aws/invoke rds operation) :DBInstances first :DBInstanceStatus)
                msg (format "Completed after : %.2fs with status %s" (/ msecs 1000) status)]
            (log/info msg)
            ret))))))

(defn evaluate-plan
  [rds operations]
  (doseq [action operations
          :let [result (interpret rds action)]
          :while (not (:ErrorResponse result))]
    result))

(comment
  (evaluate-plan rds (plan/make-test-env))
  (-> (predict/state [] (plan/make-test-env))
      (plan/replace-tree "mitosis-root" "mitosis-alpha"))
  (evaluate-plan rds (plan/replace-tree (databases rds) "mitosis-root" "mitosis-alpha"))
  (evaluate-plan rds (plan/cleanup-test-env))
  )

(comment
  (keys (aws/ops rds))
  (aws/doc rds :CreateDBInstance) ;; for testing
  (aws/doc rds :DescribeDBInstances)
  (aws/doc rds :CreateDBInstanceReadReplica)
  (aws/doc rds :PromoteReadReplica)
  (aws/doc rds :ModifyDBInstance)
  (aws/doc rds :DeleteDBInstance)
  (aws/doc rds :ListTagsForResource)

  (def instances (databases rds))

  (map :DBInstanceIdentifier instances)
  (filter #(re-find #"mysql" (:Engine %)) instances)

  (map (fn [{:keys [DBInstanceIdentifier
                   ReadReplicaDBInstanceIdentifiers
                   ReadReplicaSourceDBInstanceIdentifier
                   DBInstanceArn]}]
         {:id DBInstanceIdentifier
          :arn DBInstanceArn
          :source ReadReplicaSourceDBInstanceIdentifier
          :replicas ReadReplicaDBInstanceIdentifiers})
       instances)

  (def example-id (:DBInstanceIdentifier (rand-nth instances)))
  (->> example-id op/describe (aws/invoke rds) :DBInstances first)
  (wait/poll-until #(op/completed? (aws/invoke rds (op/describe example-id)))
                   {:delay 100 :max-attempts 5})

  (aws/invoke rds (op/tags "")))
