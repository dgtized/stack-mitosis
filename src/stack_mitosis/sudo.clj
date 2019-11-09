(ns stack-mitosis.sudo
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cognitect.aws.client.api :as aws]
            [cognitect.aws.credentials :as credentials]))

(defn token-code
  []
  (print "Enter MFA Token: ")
  (flush)
  (let [line (str/trim (read-line))]
    (println)
    (if-let [token (re-find #"^\d{6}$" line)]
      token
      (throw (ex-info "Invalid MFA Token" {:token line})))))

(defn load-role
  [filename]
  {:post [(every? (set (keys %)) [:mfa-serial :role-arn :region])]}
  (->> (or filename (io/resource "role.edn"))
       slurp
       edn/read-string))

;; not eligible for auto-refresh as it prompts for mfa token from stdin
(defn assume-mfa-role
  [{:keys [session-name role-arn mfa-serial region duration token]
    :or {session-name "sudo"
         duration (* 4 60 60)
         token (token-code)}}]
  {:pre [(some? mfa-serial) (some? role-arn) (some? region)]}
  (aws/invoke (aws/client {:api :sts :region region})
              {:op :AssumeRole
               :request {:RoleArn role-arn
                         :RoleSessionName session-name
                         :DurationSeconds duration
                         :SerialNumber mfa-serial
                         :TokenCode token}}))

(defn credential-provider [token]
  (when (:ErrorResponse token)
    (throw (ex-info "Invalid token" token)))
  (reify credentials/CredentialsProvider
    (fetch [_]
      (let [creds (:Credentials token)]
        {:aws/access-key-id     (:AccessKeyId creds)
         :aws/secret-access-key (:SecretAccessKey creds)
         :aws/session-token     (:SessionToken creds)}))))

(defonce current-provider
  (atom (credentials/default-credentials-provider (aws/default-http-client))))

(defn provider []
  (deref current-provider))

(defn sudo-provider [role]
  (let [token (assume-mfa-role role)
        provider (credential-provider token)]
    (reset! current-provider provider)
    provider))

(comment
  ;; Make sure AWS_CONFIG_FILE, AWS_CREDENTIAL_PROFILES_FILE are both set in
  ;; environment or the appropriate aws key and secret are present.
  (def iam (aws/client {:api :iam}))
  (def sts (aws/client {:api :sts}))
  (keys (aws/ops iam))
  (aws/doc iam :GetRole)
  (->> (aws/invoke iam {:op :ListRoles}) :Roles (map :RoleName))
  (def me (:User (aws/invoke iam {:op :GetUser})))
  (keys (aws/ops sts))
  (aws/doc sts :AssumeRole)

  (def sudo (sudo-provider (load-role "resources/role.edn")))
  ;; make a client using the assumed role credentials provider
  (def iam-with-assumed-role (aws/client {:api :iam :credentials-provider sudo}))
  (def sts-with-assumed-role (aws/client {:api :sts :credentials-provider sudo}))

  (aws/validate-requests sts-with-assumed-role)

  ;; use it!
  (aws/invoke iam-with-assumed-role {:op :GetUser :request {:UserName (:UserName me)}})
  (aws/invoke sts-with-assumed-role {:op :GetCallerIdentity})
  )
