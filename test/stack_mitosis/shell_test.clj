(ns stack-mitosis.shell-test
  (:require [stack-mitosis.shell :as sut]
            [clojure.test :refer :all]))

(deftest bash
  (testing "exit status"
    (with-out-str
      (is (= {:ok "Executing [true] succeeded"}
             (sut/bash "true"))))
    (with-out-str
      (is (= {:ErrorResponse "Executing [false] failed with status 1"}
             (sut/bash "false")))))

  (testing "stderr and stdout output"
    (is (= "6\n 5\n exit: 0\n"
           (with-out-str (sut/bash "(>&2 echo 5) && echo 6"))))))

