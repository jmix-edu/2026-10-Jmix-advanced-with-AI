package com.company.timesheets.security;

import io.jmix.ldap.userdetails.LdapUserAdditionalRoleProvider;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Each {@code employeeType} value of the LDAP user is one more role code, on top of the groups (spec 03/06).
 */
@Component("ts_EmployeeTypeRoleProvider")
public class EmployeeTypeRoleProvider implements LdapUserAdditionalRoleProvider {

    @Override
    public Set<GrantedAuthority> getAdditionalRoles(DirContextOperations user, String username) {
        String[] types = user.getStringAttributes("employeeType");
        if (types == null) {
            return Set.of();
        }
        return Arrays.stream(types)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }
}
