package com.company.timesheets.service;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectRoleType;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.User;
import com.company.timesheets.security.TimesheetsPolicies;
import io.jmix.core.AccessManager;
import io.jmix.core.EntityStates;
import io.jmix.core.Metadata;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.usersubstitution.CurrentUserSubstitution;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the current user may do in a project, by their participation in it.
 * Reads participation without row-level restrictions: the answer must not depend on what the user can see.
 */
@Component("ts_ProjectAccessSupport")
public class ProjectAccessSupport {

    private static final String PROJECT = "project";

    private final UnconstrainedDataManager dataManager;
    private final CurrentUserSubstitution currentUserSubstitution;
    private final AccessManager accessManager;
    private final EntityStates entityStates;
    private final Metadata metadata;

    public ProjectAccessSupport(UnconstrainedDataManager dataManager, CurrentUserSubstitution currentUserSubstitution,
                                AccessManager accessManager, EntityStates entityStates, Metadata metadata) {
        this.dataManager = dataManager;
        this.currentUserSubstitution = currentUserSubstitution;
        this.accessManager = accessManager;
        this.entityStates = entityStates;
        this.metadata = metadata;
    }

    /**
     * The current user takes part in the project as its manager.
     */
    public boolean isManager(@Nullable Project project) {
        return project != null && hasProjectRole(project.getId(), List.of(ProjectRoleType.MANAGER));
    }

    /**
     * The current user manages the project of a task or a participant. The {@code project} reference may be
     * missing from the fetch plan the grid used (a delete from a grid), so then it is read from the database.
     */
    public boolean isManagerOfProjectOf(Object entity) {
        return hasProjectRole(projectIdOf(entity), List.of(ProjectRoleType.MANAGER));
    }

    /**
     * The current user may change the status and the rejection reason of the entry: they are a manager or an
     * approver of its project and it is not their own entry, or they hold the approve-any policy (admin).
     */
    public boolean canApprove(TimeEntry entry) {
        if (isPermitted(TimesheetsPolicies.APPROVE_ANY_TIME_ENTRY)) {
            return true;
        }
        UUID userId = currentUserId();
        if (userId == null || entry.getUser() == null || userId.equals(entry.getUser().getId())) {
            return false;
        }
        Project project = entry.getTask() == null ? null : entry.getTask().getProject();
        return project != null
                && hasProjectRole(project.getId(), List.of(ProjectRoleType.MANAGER, ProjectRoleType.APPROVER));
    }

    public boolean isPermitted(String specificPolicy) {
        SpecificOperationAccessContext context = new SpecificOperationAccessContext(specificPolicy);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    @Nullable
    private UUID projectIdOf(Object entity) {
        if (entityStates.isNew(entity) || entityStates.isLoaded(entity, PROJECT)) {
            Project project = EntityValues.getValue(entity, PROJECT);
            return project == null ? null : project.getId();
        }
        String entityName = metadata.getClass(entity).getName();
        return dataManager.loadValue("select e.project.id from " + entityName + " e where e.id = :id", UUID.class)
                .parameter("id", EntityValues.getId(entity))
                .optional()
                .orElse(null);
    }

    private boolean hasProjectRole(@Nullable UUID projectId, Collection<ProjectRoleType> types) {
        UUID userId = currentUserId();
        if (projectId == null || userId == null) {
            return false;
        }
        Long count = dataManager.loadValue("""
                        select count(p) from ts_ProjectParticipant p
                        where p.project.id = :projectId and p.user.id = :userId and p.projectRole.type in :types""",
                        Long.class)
                .parameter("projectId", projectId)
                .parameter("userId", userId)
                .parameter("types", types.stream().map(ProjectRoleType::getId).toList())
                .one();
        return Objects.requireNonNullElse(count, 0L) > 0;
    }

    @Nullable
    private UUID currentUserId() {
        UserDetails user = currentUserSubstitution.getEffectiveUser();
        return user instanceof User appUser ? appUser.getId() : null;
    }
}
