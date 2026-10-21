package com.company.timesheets.view.publicdashboard;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TaskType;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * One open project with a typed task: this month 1:30 approved + 0:30 closed counts, 2:00 new and 1:00 rejected
 * do not; last month 3:00 approved. Created as admin, removed with plain SQL.
 */
class DashboardFixture {

    final String prefix = "dash-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    Project project;

    private final DataManager dataManager;
    private final SystemAuthenticator systemAuthenticator;
    private final JdbcTemplate jdbc;
    private UUID clientId;
    private UUID taskTypeId;

    DashboardFixture(DataManager dataManager, SystemAuthenticator systemAuthenticator, DataSource dataSource) {
        this.dataManager = dataManager;
        this.systemAuthenticator = systemAuthenticator;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    void create() {
        systemAuthenticator.runWithUser("admin", () -> {
            User admin = dataManager.load(User.class).query("select u from ts_User u where u.username = 'admin'").one();
            Client client = dataManager.create(Client.class);
            client.setName(prefix + "client");
            client.setContactInformation(dataManager.create(ContactInformation.class));
            client = dataManager.save(client);
            clientId = client.getId();

            TaskType taskType = dataManager.create(TaskType.class);
            taskType.setName(prefix + "type");
            taskType = dataManager.save(taskType);
            taskTypeId = taskType.getId();

            Project newProject = dataManager.create(Project.class);
            newProject.setName(prefix + "project");
            newProject.setDescription("Not for the public");
            newProject.setClient(client);
            project = dataManager.save(newProject);

            Task task = dataManager.create(Task.class);
            task.setName(prefix + "task");
            task.setProject(project);
            task.setTaskType(taskType);
            task = dataManager.save(task);

            LocalDate today = LocalDate.now();
            entry(task, admin, today, 90, TimeEntryStatus.APPROVED);
            TimeEntry closedLater = entry(task, admin, today, 30, TimeEntryStatus.APPROVED);
            entry(task, admin, today, 120, null);
            entry(task, admin, today, 60, TimeEntryStatus.REJECTED);
            entry(task, admin, YearMonth.now().minusMonths(1).atDay(1), 180, TimeEntryStatus.APPROVED);
            // CLOSED is set only by month closing; plain SQL puts one entry there for the count.
            jdbc.update("update TS_TIME_ENTRY set STATUS = 'closed' where ID = ?", closedLater.getId());
        });
    }

    private TimeEntry entry(Task task, User user, LocalDate date, long minutes, TimeEntryStatus status) {
        TimeEntry entry = dataManager.create(TimeEntry.class);
        entry.setTask(task);
        entry.setUser(user);
        entry.setDate(date);
        entry.setSpentTime(new SpentTime(minutes));
        entry = dataManager.save(entry);
        if (status != null) {
            entry.setStatus(status);
            if (status == TimeEntryStatus.REJECTED) {
                entry.setRejectionReason("Wrong task");
            }
            entry = dataManager.save(entry);
        }
        return entry;
    }

    void remove() {
        if (clientId != null) {
            String projects = "select ID from TS_PROJECT where CLIENT_ID = ?";
            String tasks = "select ID from TS_TASK where PROJECT_ID in (" + projects + ")";
            jdbc.update("delete from TS_TIME_ENTRY where TASK_ID in (" + tasks + ")", clientId);
            jdbc.update("delete from TS_TASK where PROJECT_ID in (" + projects + ")", clientId);
            jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
            jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        }
        if (taskTypeId != null) {
            jdbc.update("delete from TS_TASK_TYPE where ID = ?", taskTypeId);
        }
    }
}
