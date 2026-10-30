package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.ProjectRoleType;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TaskStatus;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import com.company.timesheets.security.EmployeeRole;
import com.company.timesheets.security.OwnTimeEntriesRowLevelRole;
import com.company.timesheets.security.ProjectParticipationRowLevelRole;
import com.company.timesheets.service.WeekTimesheet.DayCell;
import com.company.timesheets.service.WeekTimesheetService.CellEdit;
import com.company.timesheets.service.WeekTimesheetService.SaveResult;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сохранение недельной таблицы: specs/02_ui_development/05-week-sheet.spec.adoc, «Модель недели и правила ячеек».
 * Всё выполняется от имени сотрудника с настоящими ролями, поэтому row-level security и правила списаний действуют
 * так же, как в приложении.
 */
@SpringBootTest
@ActiveProfiles("test")
class WeekTimesheetServiceTest {

    /** Понедельник в далёком прошлом: туда не пишет списания ни один другой тест. */
    private static final LocalDate MONDAY = LocalDate.of(2001, 1, 1);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);

    @Autowired
    private WeekTimesheetService service;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private DataSource dataSource;

    private final String prefix = "ws-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    private String employee;
    private String colleague;
    private User colleagueUser;
    private User employeeUser;
    private Task task;
    private Task secondTask;
    private Task inactiveTask;
    private Task foreignTask;
    private UUID clientId;
    private UUID projectRoleId;

    @BeforeEach
    void createData() {
        employee = prefix + "emp";
        colleague = prefix + "col";
        systemAuthenticator.runWithSystem(() -> {
            employeeUser = user(employee);
            colleagueUser = user(colleague);
            ProjectRole member = dataManager.create(ProjectRole.class);
            member.setName(prefix + "member");
            member.setType(ProjectRoleType.MEMBER);
            projectRoleId = dataManager.save(member).getId();

            Client client = dataManager.create(Client.class);
            client.setName(prefix + "client");
            client.setContactInformation(dataManager.create(ContactInformation.class));
            clientId = dataManager.save(client).getId();

            Project project = project("own", client);
            participant(project, employeeUser, member);
            participant(project, colleagueUser, member);
            task = task(project, "a");
            secondTask = task(project, "b");
            inactiveTask = task(project, "inactive");

            Project foreign = project("foreign", client);
            participant(foreign, colleagueUser, member);
            foreignTask = task(foreign, "foreign");
        });
    }

    @AfterEach
    void removeData() {
        // Физическое удаление: удалённые списания удаляются мягко и остались бы в таблице.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String projects = "select ID from TS_PROJECT where CLIENT_ID = ?";
        String tasks = "select ID from TS_TASK where PROJECT_ID in (" + projects + ")";
        jdbc.update("delete from TS_TIME_ENTRY where TASK_ID in (" + tasks + ")", clientId);
        jdbc.update("delete from TS_TASK where PROJECT_ID in (" + projects + ")", clientId);
        jdbc.update("delete from TS_PROJECT_PARTICIPANT where PROJECT_ID in (" + projects + ")", clientId);
        jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
        jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        jdbc.update("delete from TS_PROJECT_ROLE where ID = ?", projectRoleId);
        for (String username : List.of(employee, colleague)) {
            jdbc.update("delete from SEC_ROLE_ASSIGNMENT where USERNAME = ?", username);
            jdbc.update("delete from TS_USER where USERNAME = ?", username);
        }
    }

    @Test
    void aCellCreatesChangesAndRemovesItsEntry() {
        SaveResult created = asEmployee(() -> service.apply(MONDAY, List.of(edit(task, MONDAY, "1:30"))));
        assertThat(created.saved()).isTrue();
        assertThat(created.changed()).isEqualTo(1);
        assertThat(minutes(task, MONDAY)).containsExactly(90L);

        SaveResult changed = asEmployee(() -> service.apply(MONDAY, List.of(edit(task, MONDAY, "2"))));
        assertThat(changed.changed()).isEqualTo(1);
        assertThat(minutes(task, MONDAY)).containsExactly(120L);

        SaveResult cleared = asEmployee(() -> service.apply(MONDAY, List.of(edit(task, MONDAY, ""))));
        assertThat(cleared.changed()).isEqualTo(1);
        assertThat(minutes(task, MONDAY)).isEmpty();
    }

    @Test
    void zeroMeansNoTimeAndTheSameTimeIsNoChange() {
        asEmployee(() -> service.apply(MONDAY, List.of(edit(task, MONDAY, "1:00"))));

        SaveResult same = asEmployee(() -> service.apply(MONDAY, List.of(edit(task, MONDAY, "1:00"))));
        assertThat(same.saved()).isTrue();
        assertThat(same.changed()).isZero();

        asEmployee(() -> service.apply(MONDAY, List.of(edit(task, MONDAY, "0:00"))));
        assertThat(minutes(task, MONDAY)).isEmpty();
    }

    @Test
    void lockedCellsAreRefusedAndNothingIsSaved() {
        systemAuthenticator.runWithSystem(() -> {
            entry(task, TUESDAY, 60, TimeEntryStatus.APPROVED);
            entry(task, WEDNESDAY, 30, TimeEntryStatus.NEW);
            entry(task, WEDNESDAY, 45, TimeEntryStatus.NEW);
        });

        SaveResult result = asEmployee(() -> service.apply(MONDAY, List.of(
                edit(task, MONDAY, "1:00"),
                edit(task, TUESDAY, "2:00"),
                edit(task, WEDNESDAY, "3:00"),
                edit(inactiveTask, MONDAY, "1:00"))));

        assertThat(result.saved()).isFalse();
        assertThat(result.problems()).extracting(p -> p.taskId().toString() + p.day())
                .containsExactlyInAnyOrder(task.getId().toString() + TUESDAY,
                        task.getId().toString() + WEDNESDAY,
                        inactiveTask.getId().toString() + MONDAY);
        // С понедельником всё было в порядке, но одно сохранение проходит целиком или никак.
        assertThat(minutes(task, MONDAY)).isEmpty();
        assertThat(minutes(task, TUESDAY)).containsExactly(60L);
        assertThat(minutes(task, WEDNESDAY)).containsExactlyInAnyOrder(30L, 45L);
    }

    @Test
    void aTextThatIsNotATimeMarksOnlyItsCell() {
        SaveResult result = asEmployee(() -> service.apply(MONDAY, List.of(
                edit(task, MONDAY, "1:00"),
                edit(secondTask, MONDAY, "1:75"))));

        assertThat(result.saved()).isFalse();
        assertThat(result.problems()).singleElement()
                .satisfies(p -> {
                    assertThat(p.taskId()).isEqualTo(secondTask.getId());
                    assertThat(p.message()).contains("1:75");
                });
        assertThat(minutes(task, MONDAY)).isEmpty();
    }

    @Test
    void moreThan24HoursADayRollsBackTheWholeSave() {
        SaveResult result = asEmployee(() -> service.apply(MONDAY, List.of(
                edit(task, MONDAY, "20:00"),
                edit(secondTask, MONDAY, "4:01"),
                edit(task, TUESDAY, "1:00"))));

        assertThat(result.saved()).isFalse();
        assertThat(result.ruleViolation()).isNotBlank();
        assertThat(minutes(task, MONDAY)).isEmpty();
        assertThat(minutes(task, TUESDAY)).isEmpty();
    }

    @Test
    void theEmployeeSeesOnlyOwnEntriesAndOwnProjects() {
        systemAuthenticator.runWithSystem(() -> entry(task, MONDAY, 120, TimeEntryStatus.NEW, colleagueUser));

        WeekTimesheet week = asEmployee(() -> service.load(MONDAY));

        assertThat(week.rows()).extracting(row -> row.task().getId())
                .contains(task.getId(), secondTask.getId())
                .doesNotContain(inactiveTask.getId(), foreignTask.getId());
        DayCell monday = week.cell(task.getId(), MONDAY);
        assertThat(monday).isNotNull();
        assertThat(monday.entries()).isEmpty();
        assertThat(monday.editable()).isTrue();

        // Ячейки проекта, в котором сотрудник не участвует, для него не существует: отказ, а не создание.
        SaveResult foreign = asEmployee(() -> service.apply(MONDAY, List.of(edit(foreignTask, MONDAY, "1:00"))));
        assertThat(foreign.saved()).isFalse();
    }

    @Test
    void aClosedTaskWithEntriesThisWeekStaysAsALockedRow() {
        systemAuthenticator.runWithSystem(() -> {
            entry(secondTask, MONDAY, 60, TimeEntryStatus.NEW);
            Task reloaded = dataManager.load(Task.class).id(secondTask.getId()).one();
            reloaded.setStatus(TaskStatus.INACTIVE);
            dataManager.save(reloaded);
        });

        WeekTimesheet week = asEmployee(() -> service.load(MONDAY));

        DayCell cell = week.cell(secondTask.getId(), MONDAY);
        assertThat(cell).isNotNull();
        assertThat(cell.minutes()).isEqualTo(60);
        assertThat(cell.editable()).isFalse();
    }

    private <T> T asEmployee(java.util.function.Supplier<T> action) {
        return systemAuthenticator.withUser(employee, action::get);
    }

    private CellEdit edit(Task task, LocalDate day, String text) {
        return new CellEdit(task.getId(), day, text);
    }

    /**
     * Минуты неудалённых списаний сотрудника по задаче за день, как они хранятся.
     */
    private List<Long> minutes(Task task, LocalDate day) {
        return systemAuthenticator.withSystem(() -> dataManager.load(TimeEntry.class)
                .query("select e from ts_TimeEntry e where e.task.id = :task and e.date = :day and e.user.id = :user")
                .parameter("task", task.getId())
                .parameter("day", day)
                .parameter("user", employeeUser.getId())
                .list()
                .stream().map(e -> e.getSpentTime().minutes()).toList());
    }

    private void entry(Task task, LocalDate day, long minutes, TimeEntryStatus status) {
        entry(task, day, minutes, status, employeeUser);
    }

    private void entry(Task task, LocalDate day, long minutes, TimeEntryStatus status, User user) {
        TimeEntry entry = dataManager.create(TimeEntry.class);
        entry.setTask(task);
        entry.setUser(user);
        entry.setDate(day);
        entry.setSpentTime(new SpentTime(minutes));
        entry = dataManager.save(entry);
        if (status != TimeEntryStatus.NEW) {
            // NEW -> APPROVED — разрешённый переход (правило П3).
            entry.setStatus(status);
            dataManager.save(entry);
        }
    }

    private User user(String username) {
        User user = dataManager.create(User.class);
        user.setUsername(username);
        user.setFirstName(username);
        user.setActive(true);
        user = dataManager.save(user);
        assign(username, EmployeeRole.CODE, RoleAssignmentRoleType.RESOURCE);
        assign(username, ProjectParticipationRowLevelRole.CODE, RoleAssignmentRoleType.ROW_LEVEL);
        assign(username, OwnTimeEntriesRowLevelRole.CODE, RoleAssignmentRoleType.ROW_LEVEL);
        return user;
    }

    private void assign(String username, String roleCode, String roleType) {
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(username);
        assignment.setRoleCode(roleCode);
        assignment.setRoleType(roleType);
        dataManager.saveWithoutReload(assignment);
    }

    private Project project(String name, Client client) {
        Project project = dataManager.create(Project.class);
        project.setName(prefix + name);
        project.setClient(client);
        return dataManager.save(project);
    }

    private void participant(Project project, User user, ProjectRole role) {
        ProjectParticipant participant = dataManager.create(ProjectParticipant.class);
        participant.setProject(project);
        participant.setUser(user);
        participant.setProjectRole(role);
        dataManager.saveWithoutReload(participant);
    }

    private Task task(Project project, String name) {
        Task task = dataManager.create(Task.class);
        task.setName(prefix + name);
        task.setProject(project);
        if (name.equals("inactive")) {
            task.setStatus(TaskStatus.INACTIVE);
        }
        return dataManager.save(task);
    }
}
