package com.company.timesheets.security;

import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.Project;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;

/**
 * Employee: own time entries, projects they take part in with their description and client.
 */
@ResourceRole(name = "Employee", code = EmployeeRole.CODE, scope = SecurityScope.UI)
public interface EmployeeRole extends TimesheetsBaseRole {

    String CODE = "employee";

    @EntityAttributePolicy(entityClass = Project.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void projectAttributes();

    // The client's name in a project; there are no client screens for an employee.
    @EntityAttributePolicy(entityClass = Client.class, attributes = "name", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Client.class, actions = EntityPolicyAction.READ)
    void client();
}
