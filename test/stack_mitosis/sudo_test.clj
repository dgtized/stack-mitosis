(ns stack-mitosis.sudo-test
  (:require [stack-mitosis.sudo :as sut]
            [clojure.test :refer :all])
  (:import clojure.lang.ExceptionInfo))

(deftest read-token-code
  (is (= "012345" (with-in-str "012345" (sut/token-code))))
  (is (thrown-with-msg? ExceptionInfo #"Invalid MFA Token"
                        (with-in-str "0123456" (sut/token-code))))
  (is (thrown-with-msg? ExceptionInfo #"Invalid MFA Token"
                        (with-in-str "a012345" (sut/token-code)))))
