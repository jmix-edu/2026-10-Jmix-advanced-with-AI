#!/bin/sh
# Запускает приложение (spec 04/02). При VAULT_APPROLE_DIR учётные данные AppRole, записанные vault-init,
# читаются из файлов, поэтому их нет ни в compose-файле, ни в образе.
set -eu

if [ -n "${VAULT_APPROLE_DIR:-}" ]; then
    VAULT_ROLE_ID="$(cat "$VAULT_APPROLE_DIR/role_id")"
    VAULT_SECRET_ID="$(cat "$VAULT_APPROLE_DIR/secret_id")"
    export VAULT_ROLE_ID VAULT_SECRET_ID
fi

exec java org.springframework.boot.loader.launch.JarLauncher "$@"
