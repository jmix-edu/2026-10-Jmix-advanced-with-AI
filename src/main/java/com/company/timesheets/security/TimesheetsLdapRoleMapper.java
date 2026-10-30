package com.company.timesheets.security;

import io.jmix.ldap.userdetails.LdapAuthorityToJmixRoleCodesMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Сопоставляет группы LDAP ролям приложения (спека 03/06). Любой другой authority проходит как код роли, поэтому
 * коды ролей уровня строк из {@link EmployeeTypeRoleProvider} работают; коды без роли дополнение отбрасывает.
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
