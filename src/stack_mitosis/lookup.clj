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

(defn nil-or-empty? [v]
  (or (nil? v)
      (and (or (seq? v) (vector? v))
           (empty? v))))

(defn clone-replica-attributes
  "Creates a list of additional attributes to clone from original instance into
  the newly created replica instance."
  [original tags]
  (let [attributes-to-clone
        [:CopyTagsToSnapshot
         :MonitoringRoleArn
         :MonitoringInterval
         :PubliclyAccessible
         :AutoMinorVersionUpgrade
         :DBInstanceClass
         :PerformanceInsightsKMSKeyId
         ;; :DeletionProtection ; must be false for repeated invocation
         :KmsKeyId
         :PerformanceInsightsRetentionPeriod
         ;; :SourceRegion ; not applicable?
         :ProcessorFeatures
         ;; :UseDefaultProcessorFeatures ; just copy features directly?
         :Iops
         :StorageType
         :MultiAZ
         ;; :PreSignedUrl ; not sure where this is in describe status?
         ]

        translated-attributes
        {:Tags tags
         :EnablePerformanceInsights (:PerformanceInsightsEnabled original)
         :EnableIAMDatabaseAuthentication (:IAMDatabaseAuthenticationEnabled original)
         :EnableCloudwatchLogsExports (:EnabledCloudwatchLogsExports original)
         :Port (:Port (:Endpoint original))

         ;; all active security groups ids
         :VpcSecurityGroupIds
         (->> original
              :VpcSecurityGroups
              (filter (fn [group] (= (:Status group) "active")))
              (map :VpcSecurityGroupId))

         ;; first synchronized option group name
         :OptionGroupName
         (->> original
              :OptionGroupMemberships
              (some (fn [group]
                      (and (= (:Status group) "in-sync")
                           (:OptionGroupName group)))))
         ;; TODO map for names on original
         ;; :DBSubnetGroupName
         ;; :DomainMemberships -> :Domain, :DomainIAMRoleName
         }]
    (-> original
        ;; copy as-is with no translation
        (select-keys attributes-to-clone)
        ;; Attributes requiring custom rules to extract from original and
        ;; translate to key for clone-replica request
        (merge (into {} (remove (fn [[_ v]] (nil-or-empty? v))
                                translated-attributes))))))

(defn created-replica-attributes
  "List of additional attributes to apply after creation.

  Some parameters are not available or applicable at time of creation, so they
  need to be applied after."
  [original]
  (let [translated-attributes
        {;; first synchronized db parameter group name
         :DBParameterGroupName
         (->> original
              :DBParameterGroups
              (some (fn [group]
                      (and (= (:ParameterApplyStatus group) "in-sync")
                           (:DBParameterGroupName group)))))
         }]
    (-> original
        (select-keys [:PreferredMaintenanceWindow
                      :PreferredBackupWindow
                      ;; :AllocatedStorage
                      ;; :MaxAllocatedStorage
                      ])
        ;; Attributes requiring custom rules to extract from original and
        ;; translate to key for modify-db request
        (merge (into {} (remove (fn [[_ v]] (nil-or-empty? v))
                                translated-attributes))))))
