(ns stack-mitosis.wait
  (:require [clojure.core.async :as a]))

(defn block [time]
  (Thread/sleep time)
  (println "waited" time)
  (let [state (rand-nth (concat (repeat 3 :done)
                                (repeat 6 :in-progress)
                                (repeat 1 :failed)))]
    (some #{:done :failed} [state])))

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
  (a/<!! (waiter #(block 10) 100 5)))

;; (defn poll-until [predicate {:timeout 1000 :delay 100}])
