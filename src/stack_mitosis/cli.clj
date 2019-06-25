(ns stack-mitosis.cli
  (:require [clojure.tools.cli :as cli]
            [stack-mitosis.interpreter :as interpreter]
            [stack-mitosis.planner :as plan]))

;; maybe allow specifying mfa token / role arn for sudo?
(def cli-options
  [["-s" "--source SRC" "Root identifier of database tree to copy from"]
   ["-t" "--target DST" "Root identifier of database tree to copy over"]
   [nil "--restart CMD", "Blocking script to restart application."]
   ["-p" "--plan", "Display expected flightplan for operation."]
   ["-h" "--help"]])

(defn parse-args [args]
  (let [{:keys [options errors summary]}
        (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      {:exit-msg summary :ok true}
      errors
      {:exit-msg [summary errors] :ok false}
      :else
      options)))

(defn process [options]
  (let [rds interpreter/rds
        plan (plan/replace-tree (interpreter/databases rds)
                                (:source options) (:target options))]
    (cond (:plan options)
          (do
            (println "Flight plan:")
            (doseq [{:keys [op request]} plan]
              (if-let [db-id (:DBInstanceIdentifier request)]
                (printf "%-30s %s\n\t%s\n" op db-id (dissoc request :DBInstanceIdentifier))
                (printf "%s\n\t%s\n" op request))))
          :else
          (interpreter/evaluate-plan rds plan))))

(defn -main [& args]
  (let [{:keys [ok exit-msg] :as options} (parse-args args)]
    (when exit-msg
      (println exit-msg)
      (System/exit (if ok 0 1)))
    (process options)
    (System/exit 0)
    ))

(comment
  (println (parse-args ["--source" "production" "--target" "staging"
                        "--restart" "./restart.sh" "production"]))
  (process (parse-args ["--source" "mitosis-root" "--target" "mitosis-alpha" "--plan"])))
