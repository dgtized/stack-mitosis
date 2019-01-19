(ns stack-mitosis.predict)

(defn position
  "Offset of db referenced by identifier"
  [instances op]
  (let [instance (get-in op [:request :DBInstanceIdentifier])]
    (first (keep-indexed
            (fn [idx db]
              (when (= (:DBInstanceIdentifier db) instance) idx))
            instances))))

(defmulti predict (fn [instances op] (get op :op)))

;; (defmethod change :CreateDBInstanceReadReplica)
;; (defmethod change :PromoteReadReplica)
(defmethod predict :ModifyDBInstance
  [instances op]
  (letfn [(new-name [db]
            (merge (if-let [new-id (get-in op [:request :NewDBInstanceIdentifier])]
                     (assoc db :DBInstanceIdentifier new-id)
                     db)
                   ;; merge in everything else in request
                   (dissoc (:request op) :NewDBInstanceIdentifier :DBInstanceIdentifier)))]
    (update instances (position instances op) new-name)))

(defn state [instances operations]
  (reduce predict instances operations))
