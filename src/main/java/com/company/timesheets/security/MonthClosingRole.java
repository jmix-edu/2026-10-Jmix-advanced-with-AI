package com.company.timesheets.security;

import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * Closing a month of a project into billing. Code checks the policy, not this role.
 */
@ResourceRole(name = "Month closing", code = MonthClosingRole.CODE, scope = SecurityScope.UI)
public interface MonthClosingRole {

    String CODE = "month-closing";

    @SpecificPolicy(resources = TimesheetsPolicies.MONTH_CLOSING)
    @MenuPolicy(menuIds = "ts_Project.list")
    @ViewPolicy(viewIds = "ts_Project.list")
    void monthClosing();
}
