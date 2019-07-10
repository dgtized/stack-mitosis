# stack-mitosis

Clone and redeploy an AWS RDS instance to propagate production dataset to
downstream environments like staging. This allows staging to maintain data
parity with production in a throw-away environment.

## Example

Given an environment like:

Production Application <-> `mitosis-production` -> `mitosis-production-replica`
Staging Application <-> `mitosis-staging` -> `mitosis-staging-replica`

Stack mitosis will clone `mitosis-production` into a new tree
`temp-mitosis-staging` -> `temp-mitosis-staging-replica`. It will then rename
the existing staging db to prefix with `old-` and then rename the `temp-`
prefixed clones back to `mitosis-staging` and `mitosis-staging-replica`.

stack-mitosis will restart the staging application using a provided script, and
then delete the `old-` prefixed tree.

# Usage

    clj -m stack-mitosis.cli \
        --source mitosis-production --target mitosis-staging \
        --restart "./restart-service.sh"
        --credentials resources/role.edn
        [--plan]

