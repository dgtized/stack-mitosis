(ns stack-mitosis.predict
  (:require [stack-mitosis.lookup :as lookup]))

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
    (update instances (lookup/position instances
                                       (get-in op [:request :DBInstanceIdentifier])) new-name)))

(defn state [instances operations]
  (reduce predict instances operations))
