(ns stack-mitosis.cli
  (:require [clojure.tools.cli :as cli]))

;; maybe allow specifying mfa token / role arn for sudo?
(def cli-options
  [["-s" "--source SRC" "Root identifier of database tree to copy from"]
   ["-t" "--target DST" "Root identifier of database tree to copy over"]
   [nil "--restart CMD", "Blocking script to restart application."]
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

(defn -main [& args]
  (parse-args args))

(comment
  (parse-args ["a" "--source" "production" "--target" "staging"]))
