# stack-mitosis

Copy and redeploy an AWS RDS instance for propagating production to downstream
environments.

# Usage

    clj -m stack-mitosis.cli \
        --source mitosis-root --target mitosis-alpha \
        --restart "echo restarts"
        --credentials resources/role.edn
