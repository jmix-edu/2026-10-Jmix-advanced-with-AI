#!/bin/sh
# Prepares Vault on every start of the Docker stack (spec 04/02):
# initializes it once, unseals it, puts the passwords from the "secrets" volume into secret/timesheets
# and gives the application an AppRole that can only read them.
set -eu

KEYS=/vault-keys
APPROLE=/vault-approle
SECRETS=/secrets

if vault status -format=json | grep -q '"initialized": false'; then
    vault operator init -key-shares=1 -key-threshold=1 > "$KEYS/init.txt"
    awk '/^Unseal Key 1:/ {print $4}' "$KEYS/init.txt" > "$KEYS/unseal_key"
    awk '/^Initial Root Token:/ {print $4}' "$KEYS/init.txt" > "$KEYS/root_token"
    rm "$KEYS/init.txt"
    chmod 0400 "$KEYS/unseal_key" "$KEYS/root_token"
    echo "Vault initialized"
fi

if [ ! -s "$KEYS/unseal_key" ]; then
    echo "Vault is initialized, but the vault-keys volume has no unseal key" >&2
    exit 1
fi

# Exit code 2: sealed
if ! vault status > /dev/null; then
    vault operator unseal "$(cat "$KEYS/unseal_key")" > /dev/null
    echo "Vault unsealed"
fi

VAULT_TOKEN="$(cat "$KEYS/root_token")"
export VAULT_TOKEN

vault secrets list | grep -q '^secret/' || vault secrets enable -path=secret kv-v2

vault policy write timesheets - <<'POLICY' > /dev/null
path "secret/data/timesheets*" {
  capabilities = ["read"]
}
path "secret/data/application*" {
  capabilities = ["read"]
}
POLICY

vault auth list | grep -q '^approle/' || vault auth enable approle
vault write auth/approle/role/timesheets token_policies=timesheets token_ttl=1h token_max_ttl=24h \
    secret_id_ttl=0 secret_id_num_uses=0 > /dev/null

vault kv put secret/timesheets \
    main.datasource.password="$(cat "$SECRETS/main_db_password")" \
    reports.datasource.password="$(cat "$SECRETS/reports_db_password")" \
    timesheets.admin.initial-password="$(cat "$SECRETS/admin_initial_password")" \
    jmix.ldap.manager-password="$(cat "$SECRETS/ldap_admin_password")" > /dev/null

vault read -field=role_id auth/approle/role/timesheets/role-id > "$APPROLE/role_id"
if [ ! -s "$APPROLE/secret_id" ]; then
    vault write -f -field=secret_id auth/approle/role/timesheets/secret-id > "$APPROLE/secret_id"
fi
# The application runs under its own user
chmod 0444 "$APPROLE/role_id" "$APPROLE/secret_id"

echo "Vault ready: secret/timesheets, AppRole timesheets"
