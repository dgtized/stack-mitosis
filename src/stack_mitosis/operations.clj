(ns stack-mitosis.operations)

(defn tags
  [arn]
  {:op :ListTagsForResource
   :request {:ResourceName arn}})

(defn rename
  [old new]
  {:op :ModifyDBInstance
   :request {:DBInstanceIdentifier old
             :NewDBInstanceIdentifier new
             :ApplyImmediately true}})

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

(defn create-replica
  [source replica]
  {:op :CreateDBInstanceReadReplica
   :request {:SourceDBInstanceIdentifier source
             :DBInstanceIdentifier replica}})

(defn promote
  [id]
  {:op :PromoteReadReplica
   :request {:DBInstanceIdentifier id}})
