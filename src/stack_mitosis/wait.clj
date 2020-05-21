(ns stack-mitosis.wait
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(defn block [time]
  (Thread/sleep time)
  (log/debug "waited" time)
  (contains? #{:done :failed}
             (rand-nth (concat (repeat 3 :done)
                               (repeat 6 :in-progress)
                               (repeat 1 :failed)))))

(defn waiter [pred-fn delay max-attempts]
  (let [completed (a/chan)]
    (a/go-loop [attempt 0]
      (a/<! (a/timeout delay))
      (if-let [resp (pred-fn)]
        (a/>! completed resp)
        (if (< attempt max-attempts)
          (do (log/debug "Polling Attempt " attempt)
              (recur (inc attempt)))
          (a/>! completed :max-attempts))))
    completed))

(defn poll-until [pred-fn options]
  (let [{:keys [delay max-attempts]} options]
    (a/<!! (waiter pred-fn delay max-attempts))))

(comment
  (poll-until #(block 10) {:delay 100 :max-attempts 5})
  )
