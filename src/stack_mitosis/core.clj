(ns stack-mitosis.core
  (:require [cognitect.aws.client.api :as aws]
            [clojure.string :as str]))

(def rds (aws/client {:api :rds}))

(defn databases
  [rds]
  (:DBInstances (aws/invoke rds {:op :DescribeDBInstances})))

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

(defn aliased [prefix name]
  (str prefix "-" name))

(defn instance-by-id
  [instances id]
  (->> instances
       (filter #(= (:DBInstanceIdentifier %) id))
       first))

(defn list-tree
  [instances root]
  (tree-seq (partial instance-by-id instances)
            #(:ReadReplicaDBInstanceIdentifiers (instance-by-id instances %))
            root))

(defn copy-tree
  [instances source target transform]
  (let [df (map (comp transform (partial instance-by-id instances))
                (list-tree instances target))]
    (conj (mapv #(create-replica (if-let [parent (:ReadReplicaSourceDBInstanceIdentifier %)]
                                   parent source)
                                 (:DBInstanceIdentifier %)) df)
          (promote (:DBInstanceIdentifier (first df))))))

(defn rename-tree
  [instances source transform]
  ;; FIXME need reverse topologic sort
  (let [tree (reverse (list-tree instances source))]
    (map rename tree (map transform tree))))

(defn delete-tree
  [instances root]
  (->> (list-tree instances root)
       ;; FIXME need reverse topologic sort
       reverse
       (map delete)))

(defn replicate-tree
  [source targets]
  (conj (mapv create-replica (cons source targets) targets)
        (promote (first targets))))

(defn replace-tree
  [instances source target]
  (concat (copy-tree instances source target identity)
          (rename-tree instances target (partial aliased "old"))
          (rename-tree instances (aliased "temp" target) #(str/replace % "temp-" ""))
          ;; re-deploy
          (delete-tree instances (aliased "old" target))))

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

  (filter #(re-find #"mysql" (:Engine %)) instances)

  (map (fn [{:keys [DBInstanceIdentifier ReadReplicaDBInstanceIdentifiers DBInstanceArn]}]
         {:id DBInstanceIdentifier
          :arn DBInstanceArn
          :replicas ReadReplicaDBInstanceIdentifiers})
       instances)

  (aws/invoke rds (tags "")))

(defn -main []
  (println "hi"))
