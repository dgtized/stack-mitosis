(ns stack-mitosis.core
  (:require [clojure.string :as str]
            [cognitect.aws.client.api :as aws]
            [clojure.tools.logging :as log]
            [stack-mitosis.helpers :refer [topological-sort update-if bfs-tree-seq]]
            [stack-mitosis.lookup :as lookup]
            [stack-mitosis.operations :as op]
            [stack-mitosis.predict :as predict]
            [stack-mitosis.wait :as wait]))

(def rds (aws/client {:api :rds}))

(defn databases
  [rds]
  (:DBInstances (aws/invoke rds {:op :DescribeDBInstances})))

(defn aliased [prefix name]
  (str prefix "-" name))

(defn list-tree
  [instances root]
  (bfs-tree-seq (partial lookup/replicas instances) root))

(defn topo
  [instances ids]
  (->> (map #(set (lookup/replicas instances %)) ids)
       (zipmap ids)
       topological-sort))

(defn copy-tree
  [instances source target transform]
  (let [df (map (comp transform (partial lookup/by-id instances))
                (list-tree instances target))]
    (conj (mapv #(op/create-replica (if-let [parent (:ReadReplicaSourceDBInstanceIdentifier %)]
                                      parent source)
                                    (:DBInstanceIdentifier %)) df)
          (op/promote (:DBInstanceIdentifier (first df))))))

(defn rename-tree
  [instances source transform]
  (let [tree (topo instances (list-tree instances source))]
    (map op/rename tree (map transform tree))))

(defn delete-tree
  [instances root]
  (->> (list-tree instances root)
       (topo instances)
       (map op/delete)))

(defn transform
  [prefix instance]
  (-> instance
      (update :DBInstanceIdentifier (partial aliased prefix))
      (update-if [:ReadReplicaSourceDBInstanceIdentifier] (partial aliased prefix))))

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

(defn transition-to
  "Maps current rds status to in-progress, failed or done

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
    ;; unknown or missing
    nil
    :failed
    ))

(defn blocking [action]
  (when (contains? #{:CreateDBInstance :CreateDBInstanceReadReplica :PromoteReadReplica :ModifyDBInstance}
                   (:op action))
    (-> action :request :DBInstanceIdentifier op/describe)))

(defn completed?
  [described-instances]
  (->> described-instances
       :DBInstances
       first
       transition-to
       (contains? #{:done :failed})))

(defn interpret [action]
  (log/info "Invoking " action)
  (aws/invoke rds action)
  (when-let [operation (blocking action)]
    (let [started (. System (nanoTime))
          ret (wait/poll-until #(completed? (aws/invoke rds operation))
                               {:delay 60000 :max-attempts 60})
          msecs (/ (double (- (. System (nanoTime)) started)) 1000000.0)
          status (-> (aws/invoke rds operation) :DBInstances first :DBInstanceStatus)
          msg (str "Completed after : " msecs " msecs with status: " status)]
      (log/info msg)
      ret)))

(defn evaluate-plan [actions]
  ;; TODO: handle errors & break on first error (also expired tokens)
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

  (map :DBInstanceIdentifier instances)
  (filter #(re-find #"mysql" (:Engine %)) instances)

  (map (fn [{:keys [DBInstanceIdentifier ReadReplicaDBInstanceIdentifiers DBInstanceArn]}]
         {:id DBInstanceIdentifier
          :arn DBInstanceArn
          :replicas ReadReplicaDBInstanceIdentifiers})
       instances)

  (def example-id (:DBInstanceIdentifier (rand-nth instances)))
  (->> example-id op/describe (aws/invoke rds) :DBInstances first)
  (wait/poll-until #(completed? (aws/invoke rds (op/describe example-id)))
                   {:delay 100 :max-attempts 5})

  (aws/invoke rds (op/tags "")))

(defn -main []
  (log/info "starting"))
