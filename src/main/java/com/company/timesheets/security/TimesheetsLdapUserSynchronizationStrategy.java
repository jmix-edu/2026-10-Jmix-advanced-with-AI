package com.company.timesheets.security;

import com.company.timesheets.entity.User;
import io.jmix.ldap.userdetails.AbstractLdapUserDetailsSynchronizationStrategy;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.stereotype.Component;

/**
 * Creates or updates the application user from LDAP on every LDAP sign-in (spec 03/06).
 * Role assignments are replaced by the add-on according to {@link TimesheetsLdapRoleMapper}.
 */
@Component("ts_LdapUserSynchronizationStrategy")
public class TimesheetsLdapUserSynchronizationStrategy extends AbstractLdapUserDetailsSynchronizationStrategy<User> {

    @Override
    protected Class<User> getUserClass() {
        return User.class;
    }

    // The password stays empty: LDAP checks it, and an empty password keeps the user off the database sign-in.
    @Override
    protected User createUserDetails(String username, DirContextOperations ctx) {
        User user = super.createUserDetails(username, ctx);
        user.setActive(true);
        user.setNeedsActivation(false);
        return user;
    }

    @Override
    protected void mapUserDetailsAttributes(User user, DirContextOperations ctx) {
        user.setFirstName(ctx.getStringAttribute("givenName"));
        user.setLastName(ctx.getStringAttribute("sn"));
        user.setEmail(ctx.getStringAttribute("mail"));
    }
}
