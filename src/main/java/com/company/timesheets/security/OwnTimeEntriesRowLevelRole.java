package com.company.timesheets.security;

import com.company.timesheets.entity.TimeEntry;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;

/**
 * Employee and contractor: own time entries, plus those of projects where they are an approver.
 * Policies of different row-level roles on one entity are joined with AND, so a user gets either this role or
 * managed-time-entries, never both.
 */
@RowLevelRole(name = "Own time entries", code = OwnTimeEntriesRowLevelRole.CODE)
public interface OwnTimeEntriesRowLevelRole {

    String CODE = "own-time-entries";

    @JpqlRowLevelPolicy(entityClass = TimeEntry.class,
            where = "({E}.user.id = :current_user_id or exists (select pp from ts_ProjectParticipant pp"
                    + " where pp.project.id = {E}.task.project.id and pp.user.id = :current_user_id"
                    + " and pp.projectRole.type = 'approver'))")
    void timeEntries();
}
