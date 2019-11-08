(ns stack-mitosis.example-environment
  (:require [stack-mitosis.helpers :as helpers]
            [stack-mitosis.operations :as op]
            [stack-mitosis.planner :as plan]
            [stack-mitosis.predict :as predict]))

(def template
  {:DBInstanceClass "db.t3.micro"
   :Engine "mysql" ;; "postgres"
   :StorageType "gp2"
   :AllocatedStorage 5
   :PubliclyAccessible false
   :MasterUsername "root"})

(defn create [template]
  ;; mysql allows replicas of replicas, postgres does not
  ;; create & create replica of a fresh instance take ~6 minutes
  [(op/create (merge {:DBInstanceIdentifier "mitosis-root"
                      :MasterUserPassword (helpers/generate-password)}
                     template))
   (op/create (merge {:DBInstanceIdentifier "mitosis-alpha"
                      :MasterUserPassword (helpers/generate-password)}
                     template))
   (op/create-replica "mitosis-alpha" "mitosis-beta")
   #_(op/create-replica "mitosis-beta" "mitosis-gamma")
   ])

(defn destroy []
  (conj (plan/delete-tree (predict/state [] (create template)) "mitosis-alpha")
        (op/delete "mitosis-root")))


