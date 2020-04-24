(ns stack-mitosis.example-environment
  (:require [stack-mitosis.helpers :as helpers]
            [stack-mitosis.operations :as op]
            [stack-mitosis.planner :as plan]
            [stack-mitosis.predict :as predict]))

(def template
  {:DBInstanceClass "db.t3.micro"
   :Engine "postgres" ;;"mysql"
   :StorageType "gp2"
   :AllocatedStorage 5
   :PubliclyAccessible false
   :MasterUsername "root"})

(defn create [template]
  ;; mysql allows replicas of replicas, postgres does not
  ;; create & create replica of a fresh instance take ~6 minutes
  [(op/create (merge {:DBInstanceIdentifier "mitosis-prod"
                      :MasterUserPassword (helpers/generate-password)}
                     template))
   (op/create-replica "mitosis-prod" "mitosis-prod-replica")
   (op/create (merge {:DBInstanceIdentifier "mitosis-demo"
                      :MasterUserPassword (helpers/generate-password)}
                     template))
   (op/create-replica "mitosis-demo" "mitosis-demo-replica")
   #_(op/create-replica "mitosis-demo-replica" "mitosis-demo-alternate")
   ])

(defn destroy []
  (let [state (predict/state [] (create template))]
    (conj (plan/delete-tree state "mitosis-demo")
          (plan/delete-tree state "mitosis-prod"))))


