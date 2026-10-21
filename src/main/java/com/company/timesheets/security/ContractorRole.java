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
 * Contractor: as an employee, but without the project's description and client.
 * Policies only add up, so this role does not extend Employee: it lists the project attributes it may see.
 */
@ResourceRole(name = "Contractor", code = ContractorRole.CODE, scope = SecurityScope.UI)
public interface ContractorRole extends TimesheetsBaseRole {

    String CODE = "contractor";

    @EntityAttributePolicy(entityClass = Project.class,
            attributes = {"version", "name", "status", "tasks", "participants",
                    "createdBy", "createdDate", "lastModifiedBy", "lastModifiedDate", "deletedBy", "deletedDate"},
            action = EntityAttributePolicyAction.VIEW)
    void projectAttributes();

    // The project form loads the client list for its combo box; with no attributes nothing of a client is shown.
    @EntityPolicy(entityClass = Client.class, actions = EntityPolicyAction.READ)
    void client();
}
