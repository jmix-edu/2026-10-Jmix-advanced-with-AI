#!/bin/sh
# First start of the Docker stack (spec 04/02): random passwords into the "secrets" volume.
# An existing file is never overwritten, so a later start changes nothing.
set -eu

DIR=/secrets

generate() {
    name="$1"
    if [ ! -s "$DIR/$name" ]; then
        tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32 > "$DIR/$name"
        # Readable by the database and LDAP processes that run under their own users
        chmod 0444 "$DIR/$name"
        echo "generated $name"
        return 0
    fi
    return 1
}

generate main_db_password || true
generate reports_db_password || true
generate ldap_admin_password || true
generate ldap_config_password || true

# The two passwords people sign in with are shown once, when they are created
if generate admin_initial_password; then
    echo "admin password (application): $(cat "$DIR/admin_initial_password")"
fi
if generate ldap_users_password; then
    echo "password of LDAP users ivanov, petrova, sidorov: $(cat "$DIR/ldap_users_password")"
fi
