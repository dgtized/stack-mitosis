(ns stack-mitosis.interpreter
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [cognitect.aws.client.api :as aws]
            [stack-mitosis.lookup :as lookup]
            [stack-mitosis.operations :as op]
            [stack-mitosis.planner :as plan]
            [stack-mitosis.predict :as predict]
            [stack-mitosis.sudo :as sudo]
            [stack-mitosis.wait :as wait]
            [stack-mitosis.request :as r]
            [stack-mitosis.shell :as shell]))

;; TODO: thread this client to all that use it
(def rds (aws/client {:api :rds :credentials-provider (sudo/provider)}))

(defn databases
  [rds]
  (:DBInstances (aws/invoke rds {:op :DescribeDBInstances})))

(defn describe
  [rds id]
  (aws/invoke rds (op/describe id)))

(defn- wait-for-action
  [rds action]
  (let [id (r/db-id action)
        [result-id completed-fn]
        (if-let [new-id (r/new-id action)]
          [new-id #(and (op/missing? (describe rds id))
                        (op/completed? (describe rds new-id)))]
          [id #(op/completed? (describe rds id))])]
    (let [started (. System (nanoTime))
          ret (wait/poll-until completed-fn {:delay 60000 :max-attempts 60})
          msecs (/ (double (- (. System (nanoTime)) started)) 1000000.0)
          status (-> (describe rds result-id) :DBInstances first :DBInstanceStatus)
          msg (format "Completed after : %.2fs with status %s" (/ msecs 1000) status)]
      (log/info msg)
      ret)))

;; TODO: implement "restart" action for running command
(defn interpret [rds action]
  (log/infof "Invoking %s" action)
  (let [consider (plan/attempt (databases rds) action)]
    (if (= (first consider) :skip)
      (log/infof "Skipping: %s" (second consider))
      (let [{:keys [ErrorResponse] :as result}
            (if-let [cmd (and (= :shell-command (:op action))
                              (get-in action [:request :cmd]))]
              (shell/bash cmd)
              (aws/invoke rds action))]
        (if ErrorResponse
          (do
            (log/error ErrorResponse)
            result)
          (do
            (log/info result)
            (when (op/blocking-operation? action)
              (wait-for-action rds action))))))))

(defn evaluate-plan
  [rds operations]
  (doseq [action operations
          :let [result (interpret rds action)]
          :while (not (:ErrorResponse result))]
    result))

(defn check-plan
  "Check plan against current state before evaluating."
  [state operations]
  (map plan/attempt (reductions predict/predict state operations) operations))

(comment
  (time (evaluate-plan rds (plan/make-test-env)))
  (-> (predict/state [] (plan/make-test-env))
      (plan/replace-tree "mitosis-root" "mitosis-alpha"))
  (evaluate-plan rds [(op/delete "temp-mitosis-alpha")])
  (evaluate-plan rds [(op/modify "temp-mitosis-alpha"
                                 {:BackupRetentionPeriod 1
                                  :PreferredMaintenanceWindow "sat:10:00-sat:11:00"})])

  (interpret rds (op/shell-command "echo restart"))

  ;; check plan
  (let [state (databases rds)]
    (check-plan state (plan/replace-tree state "mitosis-root" "mitosis-alpha")))

  ;; TODO: move attempt into planning, ie we should skip steps that already happen even in planning
  ;; change wait mechanics to poll all?
  ;; improve wait mechanics for rename and other modify actions
  ;; add --plan to CLI
  ;; add restart command runner to interpeter

  (filter #(re-find #"mitosis" %) (map :DBInstanceIdentifier (databases rds)))
  (time (evaluate-plan rds (plan/replace-tree (databases rds) "mitosis-root" "mitosis-alpha")))
  (time (evaluate-plan rds (plan/cleanup-test-env)))
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

  (map #(select-keys % [:StorageType :AllocatedStorage]) instances)
  (map #(select-keys % [:DBInstanceIdentifier :DBInstanceStatus]) (databases rds))
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
