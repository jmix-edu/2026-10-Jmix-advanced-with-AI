package com.company.timesheets.security;

import io.jmix.ldap.userdetails.LdapAuthorityToJmixRoleCodesMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * LDAP groups to application roles (spec 03/06). Any other authority passes as a role code, so the row-level
 * codes from {@link EmployeeTypeRoleProvider} work; codes that name no role are dropped by the add-on.
 */
@Component("ts_LdapRoleMapper")
public class TimesheetsLdapRoleMapper implements LdapAuthorityToJmixRoleCodesMapper {

    private static final Map<String, List<String>> GROUP_ROLES = Map.of(
            "employees", List.of(EmployeeRole.CODE,
                    ProjectParticipationRowLevelRole.CODE, OwnTimeEntriesRowLevelRole.CODE),
            "accounting", List.of(MonthClosingRole.CODE),
            "admins", List.of(FullAccessRole.CODE),
            "division", List.of());

    @Override
    public Collection<String> mapAuthorityToJmixRoleCodes(String authority) {
        return GROUP_ROLES.getOrDefault(authority, List.of(authority));
    }
}
