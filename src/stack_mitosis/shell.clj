(ns stack-mitosis.shell
  (:require [clojure.java.shell :as shell]))

(defn bash
  "Execute a shell command in bash, printing output to STDOUT.

  Returns ErrorResponse on failure to mimic aws/invoke failures."
  [cmd]
  (let [{:keys [exit err out]} (shell/sh "bash" "-c" cmd)]
    (println out err
             (format "exit: %d" exit))
    (if (= exit 0)
      {:ok (format "Executing [%s] succeeded" cmd)}
      {:ErrorResponse (format "Executing [%s] failed with status %d" cmd exit)})))

(comment
  (bash "(>&2 echo 5) && echo 6")
  (bash ":; echo foo")
  (bash "'echo quoted'")
  (bash "false")
  (bash "true"))
