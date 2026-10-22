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
 * Replaces the add-on's LDAP security configuration to change one rule (spec 03/06): whoever has a password in the
 * database signs in with it. LDAP users have none and go on to the LDAP check.
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
