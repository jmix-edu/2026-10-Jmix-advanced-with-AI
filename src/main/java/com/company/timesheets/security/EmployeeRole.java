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
 * Сотрудник: свои списания, проекты, в которых он участвует, с их описанием и клиентом.
 */
@ResourceRole(name = "Employee", code = EmployeeRole.CODE, scope = SecurityScope.UI)
public interface EmployeeRole extends TimesheetsBaseRole {

    String CODE = "employee";

    @EntityAttributePolicy(entityClass = Project.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void projectAttributes();

    // Имя клиента в проекте; экранов клиентов у сотрудника нет.
    @EntityAttributePolicy(entityClass = Client.class, attributes = "name", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Client.class, actions = EntityPolicyAction.READ)
    void client();
}
