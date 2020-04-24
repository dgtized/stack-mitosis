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

(defn clone-replica-attributes
  "Creates a list of additional attributes to clone from original instance into
  the newly created replica instance."
  [original]
  (let [attributes-to-clone
        [:CopyTagsToSnapshot
         :MonitoringRoleArn
         :MonitoringInterval
         :PubliclyAccessible
         :AutoMinorVersionUpgrade
         :DBInstanceClass
         :PerformanceInsightsKMSKeyId
         :KmsKeyId
         :PerformanceInsightsRetentionPeriod
         :EnableCloudwatchLogsExports
         :ProcessorFeatures
         :Iops
         :StorageType
         :MultiAZ]]
    (-> original
        ;; copy as-is with no translation
        (select-keys attributes-to-clone)
        ;; Attributes requiring custom rules to extract from original and
        ;; translate to key for clone-replica request
        (merge (into {}
                     (remove (fn [[k v]] (or (nil? v) (and (seq? v) (empty? v))))
                             {:VpcSecurityGroupIds (map :VpcSecurityGroupId (:VpcSecurityGroups original))
                              :EnablePerformanceInsights (:PerformanceInsightsEnabled original)
                              :EnableIAMDatabaseAuthentication (:IAMDatabaseAuthenticationEnabled original)
                              ;; TODO map for names on original
                              ;; :DBParameterGroupName
                              ;; :OptionGroupName
                              ;; :DBSubnetGroupName
                              }))))))
