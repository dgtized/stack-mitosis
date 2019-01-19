(ns stack-mitosis.monad
  (:require [clojure.algo.monads :as m]))

(m/domonad m/sequence-m
           [a (range 5)
            b (range a)]
           (* a b))

(m/with-monad m/sequence-m
  (defn ntuples [n xs]
    (m/m-seq (repeat n xs))))

(comment
  (ntuples 2 [1 3]))
