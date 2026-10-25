#!/bin/sh
# Starts the application (spec 04/02). With VAULT_APPROLE_DIR the AppRole credentials that vault-init wrote
# are read from files, so they never appear in the compose file or in the image.
set -eu

if [ -n "${VAULT_APPROLE_DIR:-}" ]; then
    VAULT_ROLE_ID="$(cat "$VAULT_APPROLE_DIR/role_id")"
    VAULT_SECRET_ID="$(cat "$VAULT_APPROLE_DIR/secret_id")"
    export VAULT_ROLE_ID VAULT_SECRET_ID
fi

exec java org.springframework.boot.loader.launch.JarLauncher "$@"
