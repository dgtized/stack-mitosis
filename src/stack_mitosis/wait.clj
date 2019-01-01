(ns stack-mitosis.wait
  (:require [clojure.core.async :as a]))

(defn block [time prob]
  (a/thread
    (a/timeout time)
    (println "waited" time)
    (< (rand) prob)))

(defn waiter [pred-fn delay max-attempts]
  (let [completed (a/chan)]
    (a/go-loop [attempt 0]
      (if-let [resp (a/<! (pred-fn))]
        (a/>! completed resp)
        (if (> attempt max-attempts)
          (a/>! completed :max-attempts)
          (do
            (a/<! (a/timeout delay))
            (recur (inc attempt))))))
    completed))

(comment
  (a/<!! (waiter #(block 100 0.2) 50 5)))

;; (defn poll-until [predicate {:timeout 1000 :delay 100}])
