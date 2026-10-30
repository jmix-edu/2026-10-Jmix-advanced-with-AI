package com.company.timesheets.security;

import com.company.timesheets.entity.TimeEntry;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;

/**
 * Руководитель проекта: свои списания и списания проектов, где он руководитель или согласующий.
 * Вместо own-time-entries, а не вместе с ней: политики на одну сущность объединяются через AND.
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
