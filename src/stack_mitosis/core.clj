(ns stack-mitosis.core
  (:require [clojure.string :as str]
            [cognitect.aws.client.api :as aws]
            [stack-mitosis.helpers :refer [topological-sort update-if]]
            [stack-mitosis.wait :as wait]
            [stack-mitosis.predict :as predict]))

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

(defn topo
  [instances ids]
  (topological-sort
   (zipmap ids (map #(set (:ReadReplicaDBInstanceIdentifiers (instance-by-id instances %)))
                    ids))))

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
  (let [tree (topo instances (list-tree instances source))]
    (map rename tree (map transform tree))))

(defn delete-tree
  [instances root]
  (->> (list-tree instances root)
       (topo instances)
       (map delete)))

(defn transform
  [suffix instance]
  (-> instance
      (update :DBInstanceIdentifier aliased suffix)
      (update-if [:ReadReplicaSourceDBInstanceIdentifier] aliased suffix)))

(def predict identity) ;; placeholder
(defn replace-tree
  [instances source target]
  ;; actions in copy, rename & delete change the local instances db, so use
  ;; predict to update that db for calculating next set of operations by
  ;; applying computation thus far to the initial instances
  ;; TODO something something sequence monad
  (let [copy (copy-tree instances source target (partial transform "temp"))

        a (predict/state instances copy)
        rename-old (rename-tree a target (partial aliased "old"))

        b (predict/state instances (concat copy rename-old))
        rename-temp (rename-tree b (aliased "temp" target) #(str/replace % "temp-" ""))
        ;; re-deploy

        c (predict/state instances (concat copy rename-old rename-temp))
        delete (delete-tree c (aliased "old" target))]
    (concat copy rename-old rename-temp delete)))

(defn describe [id]
  (->> {:op :DescribeDBInstances :request {:DBInstanceIdentifier id}}
       (aws/invoke rds)
       :DBInstances
       first))

(defn transition-to
  "Maps current rds status to in-progress, failed or done

  From https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.DBInstance.Status.html"
  [state]
  (condp contains? (get state :DBInstanceStatus :in-progress)
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

(defn blocking [action]
  (when (contains? #{:CreateDBInstance :CreateDBInstanceReadReplica :PromoteReadReplica :ModifyDBInstance}
                   (:op action))
    (-> action :request :DBInstanceIdentifier describe transition-to)))

(defn interpret [action]
  (aws/invoke rds action)
  (when-let [operation (blocking action)]
    (wait/poll-until #(aws/invoke rds operation) {:delay 60000 :max-attempts 60})))

(defn evaluate-plan [actions]
  (map interpret actions))

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

  (def example-id (:DBInstanceIdentifier (rand-nth instances)))
  (describe example-id)
  (wait/poll-until #(transition-to (describe example-id))
                   {:delay 100 :max-attempts 5})

  (aws/invoke rds (tags "")))

(defn -main []
  (println "hi"))
