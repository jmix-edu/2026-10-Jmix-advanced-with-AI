#!/bin/sh
# Sets the demo LDAP users' password from the "secrets" volume (spec 04/02). Safe to run on every start.
set -eu

for user in ivanov petrova sidorov; do
    ldappasswd -x -H ldap://ldap:389 -D cn=admin,dc=example,dc=org -y /secrets/ldap_admin_password \
        -T /secrets/ldap_users_password "cn=$user,ou=users,dc=example,dc=org"
done
echo "LDAP user passwords set"
