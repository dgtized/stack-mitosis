(ns stack-mitosis.shell
  (:require [clojure.java.shell :as shell]))

(defn bash [cmd]
  (let [{:keys [exit err out]} (shell/sh "bash" "-c" cmd)]
    (println out err
             (format "exit: %d" exit))
    (if (= exit 0)
      true
      false)))

(comment
  (bash "(>&2 echo 5) && echo 6")
  (bash "false")
  (bash "true"))
