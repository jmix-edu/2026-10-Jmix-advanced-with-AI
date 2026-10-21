package com.company.timesheets.security;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.Task;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * Project manager: an employee who edits tasks and participants of the projects they manage and sees the time
 * entries there. Which projects is decided by the row-level roles project-manager-rows and managed-time-entries.
 */
@ResourceRole(name = "Project manager", code = ProjectManagerRole.CODE, scope = SecurityScope.UI)
public interface ProjectManagerRole extends EmployeeRole {

    String CODE = "project-manager";

    @MenuPolicy(menuIds = {"ts_Task.list", "ts_TimeEntry.list"})
    @ViewPolicy(viewIds = {"ts_Task.list", "ts_Task.detail", "ts_ProjectParticipant.detail", "ts_TimeEntry.list"})
    void managerScreens();

    @EntityAttributePolicy(entityClass = Task.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Task.class, actions = EntityPolicyAction.ALL)
    void taskEditing();

    @EntityAttributePolicy(entityClass = ProjectParticipant.class, attributes = "*",
            action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ProjectParticipant.class, actions = EntityPolicyAction.ALL)
    void participantEditing();

    // Update, not its attributes: the project form must be editable to save its tasks and participants,
    // while the project's own fields stay read-only (VIEW from Employee).
    @EntityPolicy(entityClass = Project.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE})
    void projectForm();
}
