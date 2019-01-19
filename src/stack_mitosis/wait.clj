(ns stack-mitosis.wait
  (:require [clojure.core.async :as a]))

(defn block [time]
  (Thread/sleep time)
  (println "waited" time)
  (rand-nth (concat (repeat 3 :done)
                    (repeat 6 :in-progress)
                    (repeat 1 :failed))))

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

(defn poll-until [pred-fn options]
  (let [{:keys [delay max-attempts]} options]
    (a/<!! (waiter #(some #{:done :failed} [(pred-fn)])
                   delay max-attempts))))

(comment
  (poll-until #(block 10) {:delay 100 :max-attempts 5})
  )