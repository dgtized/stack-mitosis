(ns stack-mitosis.core
  (:require [cognitect.aws.client.api :as aws]))

(def rds (aws/client {:api :rds}))

(comment
  (keys (aws/ops rds))

  (map (fn [{:keys [DBInstanceIdentifier ReadReplicaDBInstanceIdentifiers]}]
         {:id DBInstanceIdentifier
          :replicas ReadReplicaDBInstanceIdentifiers})
       (:DBInstances (aws/invoke rds {:op :DescribeDBInstances}))))

(defn -main []
  (println "hi"))
