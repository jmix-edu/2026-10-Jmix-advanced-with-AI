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
 * Подрядчик: как сотрудник, но без описания и клиента проекта.
 * Политики только складываются, поэтому роль не расширяет Employee: она перечисляет атрибуты проекта, которые видит.
 */
@ResourceRole(name = "Contractor", code = ContractorRole.CODE, scope = SecurityScope.UI)
public interface ContractorRole extends TimesheetsBaseRole {

    String CODE = "contractor";

    @EntityAttributePolicy(entityClass = Project.class,
            attributes = {"version", "name", "status", "tasks", "participants",
                    "createdBy", "createdDate", "lastModifiedBy", "lastModifiedDate", "deletedBy", "deletedDate"},
            action = EntityAttributePolicyAction.VIEW)
    void projectAttributes();

    // Форма проекта загружает список клиентов для комбобокса; без атрибутов от клиента ничего не видно.
    @EntityPolicy(entityClass = Client.class, actions = EntityPolicyAction.READ)
    void client();
}
