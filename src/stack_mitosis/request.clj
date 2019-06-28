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

(defn explain
  [{:keys [op request] :as action}]
  (if-let [db-id (db-id action)]
    (format "%-30s %s\n\t%s" op db-id (dissoc request :DBInstanceIdentifier))
    (format "%s\n\t%s" op request)))

