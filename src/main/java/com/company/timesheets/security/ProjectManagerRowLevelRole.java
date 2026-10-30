package com.company.timesheets.security;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.Task;
import com.company.timesheets.service.ProjectAccessSupport;
import io.jmix.security.model.RowLevelBiPredicate;
import io.jmix.security.model.RowLevelPolicyAction;
import io.jmix.security.role.annotation.PredicateRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;
import org.springframework.context.ApplicationContext;

/**
 * Руководитель проекта: меняет проект, его задачи и участников только там, где он участвует как руководитель.
 * Проверки в памяти: в остальных проектах форма открывается только для чтения, а сохранение отклоняется.
 */
@RowLevelRole(name = "Project manager: own projects", code = ProjectManagerRowLevelRole.CODE)
public interface ProjectManagerRowLevelRole {

    String CODE = "project-manager-rows";

    @PredicateRowLevelPolicy(entityClass = Project.class, actions = RowLevelPolicyAction.UPDATE)
    default RowLevelBiPredicate<Project, ApplicationContext> managedProject() {
        return (project, context) -> context.getBean(ProjectAccessSupport.class).isManager(project);
    }

    @PredicateRowLevelPolicy(entityClass = Task.class,
            actions = {RowLevelPolicyAction.CREATE, RowLevelPolicyAction.UPDATE, RowLevelPolicyAction.DELETE})
    default RowLevelBiPredicate<Task, ApplicationContext> taskOfManagedProject() {
        return (task, context) -> context.getBean(ProjectAccessSupport.class).isManagerOfProjectOf(task);
    }

    @PredicateRowLevelPolicy(entityClass = ProjectParticipant.class,
            actions = {RowLevelPolicyAction.CREATE, RowLevelPolicyAction.UPDATE, RowLevelPolicyAction.DELETE})
    default RowLevelBiPredicate<ProjectParticipant, ApplicationContext> participantOfManagedProject() {
        return (participant, context) -> context.getBean(ProjectAccessSupport.class).isManagerOfProjectOf(participant);
    }
}
