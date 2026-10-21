package com.company.timesheets.security;

import com.company.timesheets.entity.TimeEntry;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;

/**
 * Project manager: own time entries, plus those of projects where they are a manager or an approver.
 * Instead of own-time-entries, not together with it: policies on one entity are joined with AND.
 */
@RowLevelRole(name = "Managed time entries", code = ManagedTimeEntriesRowLevelRole.CODE)
public interface ManagedTimeEntriesRowLevelRole {

    String CODE = "managed-time-entries";

    @JpqlRowLevelPolicy(entityClass = TimeEntry.class,
            where = "({E}.user.id = :current_user_id or exists (select pp from ts_ProjectParticipant pp"
                    + " where pp.project.id = {E}.task.project.id and pp.user.id = :current_user_id"
                    + " and pp.projectRole.type in ('manager', 'approver')))")
    void timeEntries();
}
