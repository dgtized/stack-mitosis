(ns stack-mitosis.sudo
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cognitect.aws.client.api :as aws]
            [cognitect.aws.credentials :as credentials]))

;; not eligible for auto-refresh as it prompts for mfa token from stdin
(defn assume-mfa-role [session-name target-role duration]
  (aws/invoke (aws/client {:api :sts})
              {:op      :AssumeRole
               :request {:RoleArn (:role_arn target-role)
                         :RoleSessionName session-name
                         :DurationSeconds duration
                         :SerialNumber (:mfa_serial target-role)
                         :TokenCode (str (edn/read-string (read-line)))}}))

(defn credential-provider [token]
  (when-let [error (:ErrorResponse token)]
    (throw (ex-info "Invalid token" token)))
  (reify credentials/CredentialsProvider
    (fetch [_]
      (let [creds (:Credentials token)]
        {:aws/access-key-id     (:AccessKeyId creds)
         :aws/secret-access-key (:SecretAccessKey creds)
         :aws/session-token     (:SessionToken creds)}))))

(comment
  ;; resources/role.edn contains :mfa_serial & :role_arn
  (def target-role (edn/read-string (slurp (io/resource "role.edn"))))
  (def iam (aws/client {:api :iam}))
  (def sts (aws/client {:api :sts}))
  (keys (aws/ops iam))
  (aws/doc iam :GetRole)
  (->> (aws/invoke iam {:op :ListRoles}) :Roles (map :RoleName))
  (def me (:User (aws/invoke iam {:op :GetUser})))
  (keys (aws/ops sts))
  (aws/doc sts :AssumeRole)

  (def token (assume-mfa-role "sudo" target-role (* 4 60 60)))
  (def provider (credential-provider token))

  ;; make a client using the assumed role credentials provider
  (def iam-with-assumed-role (aws/client {:api :iam :credentials-provider provider}))
  (def sts-with-assumed-role (aws/client {:api :sts :credentials-provider provider}))

  ;; use it!
  (aws/invoke iam-with-assumed-role {:op :GetUser :request {:UserName (:UserName me)}})
  (aws/invoke sts-with-assumed-role {:op :GetCallerIdentity})
  )
