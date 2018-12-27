(ns stack-mitosis.helpers
  (:require [clojure.set :as set]))

(defn update-if
  "update-in, but only run `f` if key at `ks` exists and is not nil"
  [m ks f & args]
  (if (get-in m ks)
    (apply update-in m ks f args)
    m))

(defn topological-sort
  [graph]
  (letfn [(nodes [g] (apply set/union (keys g) (vals g)))]
    (loop [ret [] graph graph]
      (if (empty? (nodes graph))
        ret
        (let [no-deps (sort (set (filter #(empty? (get graph % (set nil))) (nodes graph))))
              graph' (reduce-kv #(assoc %1 %2 (apply disj %3 no-deps)) {}
                                (apply dissoc graph no-deps))]
          (if (empty? no-deps)
            nil ;; cyclic dependencies
            (recur (concat ret no-deps) graph')))))))
