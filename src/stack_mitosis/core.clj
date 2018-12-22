(ns stack-mitosis.core
  (:require [cognitect.aws.client.api :as aws]))

(def rds (aws/client {:api :rds}))

(defn tags
  [arn]
  {:op :ListTagsForResource
   :request {:ResourceName arn}})

(defn rename
  [old new]
  {:op :ModifyDBInstance
   :request {:DbInstanceIdentifier old
             :NewDBInstanceIdentifier new
             :ApplyImmediately true}})

(defn delete
  [id]
  {:op :DeleteDBInstance
   :request {:DbInstanceIdentifier id
             :SkipFinalSnapshot true}})

(defn promote
  [id]
  {:op :PromoteReadReplica
   :request {:DbInstanceIdentifier id}})

(comment
  (keys (aws/ops rds))
  (aws/doc rds :CreateDBInstance) ;; for testing
  (aws/doc rds :DescribeDBInstances)
  (aws/doc rds :CreateDBInstanceReadReplica)
  (aws/doc rds :PromoteReadReplica)
  (aws/doc rds :ModifyDBInstance)
  (aws/doc rds :DeleteDBInstance)
  (aws/doc rds :ListTagsForResource)

  (map (fn [{:keys [DBInstanceIdentifier ReadReplicaDBInstanceIdentifiers DBInstanceArn]}]
         {:id DBInstanceIdentifier
          :arn DBInstanceArn
          :replicas ReadReplicaDBInstanceIdentifiers})
       (:DBInstances (aws/invoke rds {:op :DescribeDBInstances})))

  (aws/invoke rds (tags "")))

(defn -main []
  (println "hi"))
