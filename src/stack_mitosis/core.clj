(ns stack-mitosis.core
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [cognitect.aws.client.api :as aws]
            [stack-mitosis.helpers :refer [bfs-tree-seq topological-sort update-if]]
            [stack-mitosis.lookup :as lookup]
            [stack-mitosis.operations :as op]
            [stack-mitosis.predict :as predict]
            [stack-mitosis.sudo :as sudo]
            [stack-mitosis.wait :as wait]))

;; TODO: thread this client to all that use it
(def rds (aws/client {:api :rds :credentials-provider (sudo/provider)}))

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

(defn generate-password
  ([] (generate-password 20))
  ([n] (let [chars (map char (concat (range (int \0) (int \9))
                                     (range (int \A) (int \Z))
                                     (range (int \a) (int \z))))]
         (reduce str (take n (repeatedly #(rand-nth chars)))))))

(defn make-test-env []
  (let [template {:DBInstanceClass "db.t3.micro"
                  :Engine "postgres"
                  :AllocatedStorage 5
                  :MasterUsername "root"}]
    ;; create & create replica of a fresh instance take ~6 minutes
    [(op/create (merge {:DBInstanceIdentifier "mitosis-root"
                        :MasterUserPassword (generate-password)}
                       template))
     (op/create (merge {:DBInstanceIdentifier "mitosis-alpha"
                        :MasterUserPassword (generate-password)}
                       template))
     (op/create-replica "mitosis-alpha" "mitosis-beta")
     #_(op/create-replica "mitosis-beta" "mitosis-gamma")
     ]))

(defn cleanup-test-env []
  (conj (delete-tree (predict/state [] (make-test-env)) "mitosis-alpha")
        (op/delete "mitosis-root")))

(comment
  (evaluate-plan rds (make-test-env))
  (evaluate-plan rds (replace-tree (databases rds) "mitosis-root" "mitosis-alpha"))
  (evaluate-plan rds (cleanup-test-env))
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

(defn -main []
  (log/info "starting"))
