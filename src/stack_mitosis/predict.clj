(ns stack-mitosis.predict
  (:require [stack-mitosis.lookup :as lookup]))

(defn attach
  [db child-id]
  (update db :ReadReplicaDBInstanceIdentifiers conj child-id))

(defmulti predict (fn [instances op] (get op :op)))

(defmethod predict :CreateDBInstanceReadReplica
  [instances op]
  (let [parent (get-in op [:request :SourceDBInstanceIdentifier])
        child (get-in op [:request :DBInstanceIdentifier])
        source (lookup/by-id instances parent)]
    (conj (update instances (lookup/position instances parent) attach child)
          (assoc (merge source (dissoc (:request op) :SourceDBInstanceIdentifier))
                 :ReadReplicaSourceDBInstanceIdentifier parent))))

(defmethod predict :PromoteReadReplica
  [instances op]
  (let [child (get-in op [:request :DBInstanceIdentifier])
        parent (get (lookup/by-id instances child) :ReadReplicaSourceDBInstanceIdentifier)]
    (letfn [(detach [db]
              (update db :ReadReplicaDBInstanceIdentifiers
                      (partial remove #(= % child))))
            (promote [db]
              (merge (dissoc db :ReadReplicaSourceDBInstanceIdentifier)
                     (dissoc (:request op) :DBInstanceIdentifier)))]
      (-> instances
          (update (lookup/position instances parent) detach)
          (update (lookup/position instances child) promote)))))

(defmethod predict :ModifyDBInstance
  [instances op]
  (letfn [(new-name [db]
            (merge (if-let [new-id (get-in op [:request :NewDBInstanceIdentifier])]
                     (assoc db :DBInstanceIdentifier new-id)
                     db)
                   ;; merge in everything else in request
                   (dissoc (:request op) :NewDBInstanceIdentifier :DBInstanceIdentifier)))]
    (update instances (lookup/position instances
                                       (get-in op [:request :DBInstanceIdentifier])) new-name)))

(defmethod predict :DeleteDBInstance
  [instances op]
  (let [db-id (get-in op [:request :DBInstanceIdentifier])]
    (remove #(= (:DBInstanceIdentifier %) db-id) instances)))

(defn state [instances operations]
  (reduce predict instances operations))
