package com.company.timesheets.security;

import com.company.timesheets.entity.TimeEntry;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;

/**
 * Сотрудник и подрядчик: свои списания и списания проектов, где он согласующий.
 * Политики разных ролей уровня строк на одну сущность объединяются через AND, поэтому пользователь получает либо
 * эту роль, либо managed-time-entries, но не обе.
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
