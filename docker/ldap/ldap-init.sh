#!/bin/sh
# Ставит пароль демо-пользователям LDAP из тома "secrets" (spec 04/02). Можно запускать при каждом старте.
set -eu

for user in ivanov petrova sidorov; do
    ldappasswd -x -H ldap://ldap:389 -D cn=admin,dc=example,dc=org -y /secrets/ldap_admin_password \
        -T /secrets/ldap_users_password "cn=$user,ou=users,dc=example,dc=org"
done
echo "LDAP user passwords set"
