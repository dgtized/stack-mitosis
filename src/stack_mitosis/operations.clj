(ns stack-mitosis.operations)

(defn tags
  [arn]
  {:op :ListTagsForResource
   :request {:ResourceName arn}})

(defn delete
  [id]
  {:op :DeleteDBInstance
   :request {:DBInstanceIdentifier id
             :SkipFinalSnapshot true}})

(defn modify
  [id options]
  {:op :ModifyDBInstance
   :request
   (merge {:DBInstanceIdentifier id
           :ApplyImmediately true}
          options)})

(defn rename
  [old new]
  (modify old {:NewDBInstanceIdentifier new}))

(defn enable-backups
  [id]
  (modify id {:BackupRetentionPeriod 1}))

(defn create
  [options]
  {:op :CreateDBInstance
   :request options})

(defn create-replica
  [source replica]
  {:op :CreateDBInstanceReadReplica
   :request {:SourceDBInstanceIdentifier source
             :DBInstanceIdentifier replica}})

(defn promote
  [id]
  {:op :PromoteReadReplica
   :request {:DBInstanceIdentifier id}})

(defn describe [id]
  {:op :DescribeDBInstances
   :request {:DBInstanceIdentifier id}})

(defn shell-command [cmd]
  {:op :shell-command
   :request {:cmd cmd}})

(defn polling-operation
  "Calculate if operation requires a describe to poll for completion."
  [action]
  (when (contains? #{:CreateDBInstance :CreateDBInstanceReadReplica
                     :PromoteReadReplica :ModifyDBInstance}
                   (:op action))
    (-> action :request :DBInstanceIdentifier describe)))

(defn transition-to
  "Maps current rds status to in-progress, failed or done

  From https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.DBInstance.Status.html"
  [state]
  (if (:ErrorResponse state)
    :failed
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
      ;; unknown or missing
      nil
      :failed
      )))

(defn completed?
  [described-instances]
  (->> described-instances
       :DBInstances
       first
       transition-to
       (contains? #{:done :failed})))
