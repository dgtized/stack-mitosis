(ns stack-mitosis.helpers)

(defn update-if
  "update-in, but only run `f` if key at `ks` exists and is not nil"
  [m ks f & args]
  (if (get-in m ks)
    (apply update-in m ks f args)
    m))
