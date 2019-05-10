(ns stack-mitosis.sudo
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cognitect.aws.client.api :as aws]
            [cognitect.aws.credentials :as credentials]))

;; borrowing liberally from https://github.com/cognitect-labs/aws-api/blob/fbf89760913ee3fbc836ec8befa9b17af33c5a64/examples/assume_role_example.clj
(defn assumed-role-credentials-provider [role-arn session-name refresh-every-n-seconds]
  (let [sts (aws/client {:api :sts})]
    (credentials/auto-refreshing-credentials
     (reify credentials/CredentialsProvider
       (fetch [_]
         (when-let [creds (:Credentials
                           (aws/invoke sts
                                       {:op      :AssumeRole
                                        :request {:RoleArn         role-arn
                                                  :RoleSessionName session-name}}))]
           {:aws/access-key-id     (:AccessKeyId creds)
            :aws/secret-access-key (:SecretAccessKey creds)
            :aws/session-token     (:SessionToken creds)
            ::credentials/ttl      refresh-every-n-seconds}))))))

(defn lookup-role [role]
  (->> {:op :GetRole :request {:RoleName role}}
       (aws/invoke iam)
       :Role :Arn))

(comment
  ;; resources/role.edn contains :mfa_serial & :role_arn
  (def target-role (edn/read-string (slurp (io/resource "role.edn"))))
  (def iam (aws/client {:api :iam}))
  (def sts (aws/client {:api :sts}))
  (keys (aws/ops iam))
  (aws/doc iam :GetRole)
  (->> (aws/invoke iam {:op :ListRoles}) :Roles (map :RoleName))
  (:User (aws/invoke iam {:op :GetUser}))
  (keys (aws/ops sts))
  (aws/doc sts :AssumeRole)

  (def provider (assumed-role-credentials-provider (:Arn new-role) "example-session" 600))

  ;; make a client using the assumed role credentials provider
  (def iam-with-assumed-role (aws/client {:api :iam :credentials-provider provider}))

  ;; use it!
  (aws/invoke iam-with-assumed-role {:op :GetUser :request {:UserName (:UserName me)}}))
