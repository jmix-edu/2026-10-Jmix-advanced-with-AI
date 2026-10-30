package com.company.timesheets.security;

import io.jmix.core.JmixOrder;
import io.jmix.core.security.event.PreAuthenticationCheckEvent;
import io.jmix.ldap.LdapSecurityConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.StringUtils;

/**
 * Заменяет конфигурацию безопасности LDAP из дополнения, чтобы изменить одно правило (спека 03/06): у кого есть пароль
 * в базе, входит с ним. У пользователей LDAP его нет, и они переходят к проверке через LDAP.
 */
@Configuration
public class TimesheetsLdapSecurityConfiguration extends LdapSecurityConfiguration {

    @EventListener
    @Order(JmixOrder.LOWEST_PRECEDENCE - 10)
    @Override
    public void onPreAuthenticationCheckEvent(PreAuthenticationCheckEvent event) {
        String username = event.getUser().getUsername();
        if (ldapProperties.getStandardAuthenticationUsers().contains(username)) {
            return;
        }
        if (!StringUtils.hasText(event.getUser().getPassword())) {
            throw new BadCredentialsException("User " + username + " signs in via LDAP");
        }
    }
}
