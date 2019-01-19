(ns stack-mitosis.lookup)

(defn by-id
  [instances id]
  (->> instances
       (filter #(= (:DBInstanceIdentifier %) id))
       first))
