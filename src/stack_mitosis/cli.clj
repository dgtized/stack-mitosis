(ns stack-mitosis.cli
  (:require [clojure.tools.cli :as cli]
            [stack-mitosis.interpreter :as interpreter]
            [stack-mitosis.planner :as plan]
            [stack-mitosis.request :as r]
            [clojure.string :as str]
            [stack-mitosis.sudo :as sudo]
            [clojure.tools.logging :as log]))

;; TODO: add max-timeout for actions
;; TODO: show attempt info like skipped steps in flight plan?
;; TODO: options to copy-tree instead of replace
(def cli-options
  [["-s" "--source SRC" "Root identifier of database tree to copy from"]
   ["-t" "--target DST" "Root identifier of database tree to copy over"]
   [nil "--restart CMD" "Blocking script to restart application."]
   ["-c" "--credentials FILENAME" "Credentials file in edn for iam assume-role"]
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

(defn flight-plan
  [plan]
  (concat ["Flight plan:"] (map r/explain plan)))

(defn process [options]
  (when-let [creds (:credentials options)]
    (let [role (sudo/load-role creds)]
      (log/infof "Assuming role %s" (:role-arn role))
      (sudo/sudo-provider role)))
  (let [rds (interpreter/client)
        plan (plan/replace-tree (interpreter/databases rds)
                                (:source options) (:target options)
                                :restart (:restart options))]
    (cond (:plan options)
          (println (str/join "\n" (flight-plan plan)))
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
  (process (parse-args ["--source" "mitosis-root" "--target" "mitosis-alpha"
                        "--plan" "--restart" "'./service-restart.sh'"]))
  (process (parse-args ["--source" "mitosis-root" "--target" "mitosis-alpha"
                        "--plan" "--credentials" "resources/role.edn"]))
  (process (parse-args ["--source" "mitosis-root" "--target" "mitosis-alpha"])))
