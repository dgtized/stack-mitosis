(ns stack-mitosis.lookup)

(defn by-id
  "Instance referenced by id"
  [instances db-id]
  (->> instances
       (filter #(= (:DBInstanceIdentifier %) db-id))
       first))

(def exists? by-id)

(defn position
  "Offset of db referenced by id"
  [instances db-id]
  (->> instances
       (keep-indexed
        (fn [idx db]
          (when (= (:DBInstanceIdentifier db) db-id) idx)))
       first))

(defn parent
  [instances db-id]
  (get (by-id instances db-id)
       :ReadReplicaSourceDBInstanceIdentifier))

(defn replicas
  [instances db-id]
  (get (by-id instances db-id)
       :ReadReplicaDBInstanceIdentifiers))
