(ns stack-mitosis.request
  "Extracting common fields from operation requests.")

(defn db-id
  [op]
  (get-in op [:request :DBInstanceIdentifier]))

(defn source-id
  [op]
  (get-in op [:request :SourceDBInstanceIdentifier]))

(defn new-id
  [op]
  (get-in op [:request :NewDBInstanceIdentifier]))
