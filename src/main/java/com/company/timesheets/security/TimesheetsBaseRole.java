package com.company.timesheets.security;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TaskType;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.User;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * То, что есть у каждого пользователя табеля: свои списания и экраны вокруг них, проекты на чтение.
 * Отдельно не назначается: её расширяют Employee, Contractor и Project manager.
 * Строки ограничивают роли уровня строк, см. specs/03_access_control/01-roles.spec.adoc.
 */
@ResourceRole(name = "Timesheets: base", code = TimesheetsBaseRole.CODE, scope = SecurityScope.UI)
public interface TimesheetsBaseRole extends UiMinimalRole {

    String CODE = "ts-base";

    // TimeEntries.weekSheet: недельная таблица на RevoGrid (спека 02/05); она сохраняет списания по политикам ниже.
    @MenuPolicy(menuIds = {"TimeEntries.my", "TimeEntries.myWeek", "TimeEntries.weekSheet", "Tasks.my",
            "ts_Task.list-simple", "ts_Project.list", "ts_PublicDashboardView"})
    @ViewPolicy(viewIds = {"TimeEntries.my", "TimeEntries.myWeek", "TimeEntries.weekSheet", "Tasks.my",
            "ts_Task.list-simple", "ts_TimeEntry.detail", "ts_Task.lookup", "ts_Project.list", "ts_Project.detail",
            "ts_PublicDashboardView"})
    void screens();

    // Статус и причину отклонения здесь можно менять; форма разрешает это только руководителю
    // или согласующему проекта (ProjectAccessSupport).
    @EntityAttributePolicy(entityClass = TimeEntry.class,
            attributes = {"version", "date", "spentTime", "status", "description", "rejectionReason", "task", "user",
                    "createdBy", "createdDate", "lastModifiedBy", "lastModifiedDate", "deletedBy", "deletedDate"},
            action = EntityAttributePolicyAction.MODIFY)
    @EntityAttributePolicy(entityClass = TimeEntry.class, attributes = "billingRecordId",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = TimeEntry.class, actions = EntityPolicyAction.ALL)
    void timeEntry();

    @EntityAttributePolicy(entityClass = Task.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Task.class, actions = EntityPolicyAction.READ)
    void task();

    @EntityAttributePolicy(entityClass = TaskType.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = TaskType.class, actions = EntityPolicyAction.READ)
    void taskType();

    @EntityAttributePolicy(entityClass = ProjectRole.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ProjectRole.class, actions = EntityPolicyAction.READ)
    void projectRole();

    @EntityAttributePolicy(entityClass = ProjectParticipant.class, attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ProjectParticipant.class, actions = EntityPolicyAction.READ)
    void projectParticipant();

    // Атрибуты Project дают роли, которые расширяют эту.
    @EntityPolicy(entityClass = Project.class, actions = EntityPolicyAction.READ)
    void project();

    // Только имена: без пароля и других полей учётной записи.
    @EntityAttributePolicy(entityClass = User.class, attributes = {"username", "firstName", "lastName", "email"},
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.READ)
    void user();
}
