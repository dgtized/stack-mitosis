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
  ;; TODO handle many other keys for changes other than renames
  (letfn [(new-name [db]
            (assoc db :DBInstanceIdentifier
                   (get-in op [:request :NewDBInstanceIdentifier])))]
    (update instances (position instances op) new-name)))
