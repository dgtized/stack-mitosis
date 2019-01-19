(ns stack-mitosis.predict
  (:require [stack-mitosis.lookup :as lookup]))

(defn attach
  [db child-id]
  (update db :ReadReplicaDBInstanceIdentifiers
          conj child-id))

(defn detach
  [db child-id]
  (update db :ReadReplicaDBInstanceIdentifiers
          (partial remove #(= % child-id))))

(defmulti predict (fn [instances op] (get op :op)))

(defmethod predict :CreateDBInstanceReadReplica
  [instances op]
  (let [parent (get-in op [:request :SourceDBInstanceIdentifier])
        child (get-in op [:request :DBInstanceIdentifier])
        source (lookup/by-id instances parent)]
    (conj (update instances (lookup/position instances parent) attach child)
          (-> source
              (merge (dissoc (:request op) :SourceDBInstanceIdentifier :ApplyImmediately))
              (assoc :ReadReplicaSourceDBInstanceIdentifier parent)
              (dissoc :ReadReplicaDBInstanceIdentifiers)))))

(defmethod predict :PromoteReadReplica
  [instances op]
  (let [child (get-in op [:request :DBInstanceIdentifier])
        parent (get (lookup/by-id instances child) :ReadReplicaSourceDBInstanceIdentifier)]
    (letfn [(promote [db]
              (merge (dissoc db :ReadReplicaSourceDBInstanceIdentifier)
                     (dissoc (:request op) :DBInstanceIdentifier :ApplyImmediately)))]
      (-> instances
          (update (lookup/position instances parent) detach child)
          (update (lookup/position instances child) promote)))))

(defmethod predict :ModifyDBInstance
  [instances op]
  (let [current-id (get-in op [:request :DBInstanceIdentifier])
        new-id (get-in op [:request :NewDBInstanceIdentifier])
        parent (get (lookup/by-id instances current-id) :ReadReplicaSourceDBInstanceIdentifier)]
    (letfn [(new-name [db]
              (merge (if new-id
                       (assoc db :DBInstanceIdentifier new-id)
                       db)
                     ;; merge in everything else in request
                     (dissoc (:request op)
                             :NewDBInstanceIdentifier :DBInstanceIdentifier :ApplyImmediately)))
            (rename-refs [db]
              (cond (and new-id (= (:ReadReplicaSourceDBInstanceIdentifier db) current-id))
                    ;; update children
                    (assoc db :ReadReplicaSourceDBInstanceIdentifier new-id)

                    (and new-id (= (:DBInstanceIdentifier db) parent))
                    (attach (detach db current-id) new-id)
                    :else db))]
      (mapv rename-refs
            (update instances (lookup/position instances current-id) new-name)))))

(defmethod predict :DeleteDBInstance
  [instances op]
  (let [db-id (get-in op [:request :DBInstanceIdentifier])]
    (->> instances
         (remove #(= (:DBInstanceIdentifier %) db-id))
         ;; may need to account for if delete is allowed if db has replicas
         (mapv #(detach % db-id)))))

(defn state [instances operations]
  (reduce predict instances operations))
