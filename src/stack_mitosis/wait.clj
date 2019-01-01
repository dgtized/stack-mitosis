(ns stack-mitosis.wait
  (:require [clojure.core.async :as a]))

(defn block [time prob]
  (Thread/sleep time)
  (println "waited" time)
  (< (rand) prob))

(defn waiter [pred-fn delay max-attempts]
  (let [completed (a/chan)]
    (a/go-loop [attempt 0]
      (a/<! (a/timeout delay))
      (if-let [resp (pred-fn)]
        (a/>! completed resp)
        (if (< attempt max-attempts)
          (recur (inc attempt))
          (a/>! completed :max-attempts))))
    completed))

(comment
  (a/<!! (waiter #(block 10 0.2) 100 5)))

;; (defn poll-until [predicate {:timeout 1000 :delay 100}])
