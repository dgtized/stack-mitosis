(ns stack-mitosis.helpers
  (:require [clojure.set :as set]))

(defn update-if
  "update-in, but only run `f` if key at `ks` exists and is not nil"
  [m ks f & args]
  (if (get-in m ks)
    (apply update-in m ks f args)
    m))

(defn topological-sort
  "Returns a topologically sorted list of nodes from a graph represented as a map of
  key to set of dependent nodes. Returns nil if graph contains a cyclic dependency."
  [graph]
  (letfn [(nodes [g] (apply set/union (keys g) (vals g)))
          (no-dependencies? [g n] (empty? (get g n (set nil))))]
    (loop [ret [] graph graph]
      (if (empty? (nodes graph))
        ret
        (let [no-deps
              (->> (nodes graph)
                   (filter (partial no-dependencies? graph))
                   sort set)
              graph'
              (reduce-kv (fn [g node edges]
                           (assoc g node (set/difference edges no-deps)))
                         {}
                         (apply dissoc graph no-deps))]
          (if (empty? no-deps)
            nil ;; cyclic dependencies
            (recur (concat ret no-deps) graph')))))))
