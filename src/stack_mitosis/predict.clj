(ns stack-mitosis.predict)

(defmulti change (fn [db op] (get op :op)))

;; (defmethod change :CreateDBInstanceReadReplica)
;; (defmethod change :PromoteReadReplica)
(defmethod change :ModifyDBInstance
  [db op]
  ;; TODO handle many other keys
  (assoc db :DBInstanceIdentifier (get-in op [:request :NewDBInstanceIdentifier])))

(defn position
  "Offset of db referenced by identifier"
  [instances op]
  (let [instance (get-in op [:request :DBInstanceIdentifier])]
    (first (keep-indexed
            (fn [idx db]
              (when (= (:DBInstanceIdentifier db) instance) idx))
            instances))))

(defn predict [instances op]
  (update instances (position instances op) change op))
