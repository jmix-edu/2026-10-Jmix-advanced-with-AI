#!/bin/sh
# Первый запуск Docker-стека (spec 04/02): случайные пароли в том "secrets".
# Существующий файл не перезаписывается, поэтому следующие запуски ничего не меняют.
set -eu

DIR=/secrets

generate() {
    name="$1"
    if [ ! -s "$DIR/$name" ]; then
        tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32 > "$DIR/$name"
        # Доступен на чтение процессам БД и LDAP, которые работают под своими пользователями
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

# Два пароля для входа людей показываются один раз, при создании
if generate admin_initial_password; then
    echo "admin password (application): $(cat "$DIR/admin_initial_password")"
fi
if generate ldap_users_password; then
    echo "password of LDAP users ivanov, petrova, sidorov: $(cat "$DIR/ldap_users_password")"
fi
