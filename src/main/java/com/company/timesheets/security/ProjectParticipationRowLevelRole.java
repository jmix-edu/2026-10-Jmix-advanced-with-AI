package com.company.timesheets.security;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.Task;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;

/**
 * Projects, their tasks and participants only where the current user takes part. For every timesheet role.
 */
@RowLevelRole(name = "Project participation", code = ProjectParticipationRowLevelRole.CODE)
public interface ProjectParticipationRowLevelRole {

    String CODE = "project-participation";

    @JpqlRowLevelPolicy(entityClass = Project.class,
            where = "exists (select pp from ts_ProjectParticipant pp"
                    + " where pp.project.id = {E}.id and pp.user.id = :current_user_id)")
    void projects();

    @JpqlRowLevelPolicy(entityClass = Task.class,
            where = "exists (select pp from ts_ProjectParticipant pp"
                    + " where pp.project.id = {E}.project.id and pp.user.id = :current_user_id)")
    void tasks();

    @JpqlRowLevelPolicy(entityClass = ProjectParticipant.class,
            where = "exists (select pp from ts_ProjectParticipant pp"
                    + " where pp.project.id = {E}.project.id and pp.user.id = :current_user_id)")
    void participants();
}
