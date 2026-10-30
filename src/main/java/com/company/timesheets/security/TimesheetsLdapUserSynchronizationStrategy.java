package com.company.timesheets.security;

import com.company.timesheets.entity.User;
import io.jmix.ldap.userdetails.AbstractLdapUserDetailsSynchronizationStrategy;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.stereotype.Component;

/**
 * Создаёт или обновляет пользователя приложения из LDAP при каждом входе через LDAP (спека 03/06).
 * Назначения ролей дополнение заменяет по {@link TimesheetsLdapRoleMapper}.
 */
@Component("ts_LdapUserSynchronizationStrategy")
public class TimesheetsLdapUserSynchronizationStrategy extends AbstractLdapUserDetailsSynchronizationStrategy<User> {

    @Override
    protected Class<User> getUserClass() {
        return User.class;
    }

    // Пароль остаётся пустым: его проверяет LDAP, а пустой пароль не пускает пользователя во вход по базе.
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
