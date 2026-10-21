package com.company.timesheets.security;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.ProjectRoleType;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.User;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Two projects and users with the timesheet roles, from specs/03_access_control/01-roles.spec.adoc.
 * <ul>
 *     <li>A: pm is the manager, emp and con are members, apr is the approver, mgrEmp is a manager by participation
 *     but has the Employee role;</li>
 *     <li>B: pm and other are members.</li>
 * </ul>
 * Created as the system user, removed with plain SQL so that no soft-deleted rows stay.
 */
class RolesFixture {

    final String pm;
    final String emp;
    final String apr;
    final String con;
    final String mgrEmp;
    final String other;
    final String closer;

    Project projectA;
    Project projectB;
    Task taskA;
    Task taskB;
    TimeEntry empEntryA;
    TimeEntry pmEntryB;
    TimeEntry otherEntryB;
    TimeEntry pmEntryA;

    private final DataManager dataManager;
    private final SystemAuthenticator systemAuthenticator;
    private final JdbcTemplate jdbc;
    private final String prefix = "roles-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    private final List<String> usernames = new ArrayList<>();
    private UUID clientId;
    private final List<UUID> projectRoleIds = new ArrayList<>();

    RolesFixture(DataManager dataManager, SystemAuthenticator systemAuthenticator, DataSource dataSource) {
        this.dataManager = dataManager;
        this.systemAuthenticator = systemAuthenticator;
        this.jdbc = new JdbcTemplate(dataSource);
        pm = prefix + "pm";
        emp = prefix + "emp";
        apr = prefix + "apr";
        con = prefix + "con";
        mgrEmp = prefix + "mgremp";
        other = prefix + "other";
        closer = prefix + "closer";
    }

    void create() {
        systemAuthenticator.runWithSystem(() -> {
            List<String> employeeRows = List.of(ProjectParticipationRowLevelRole.CODE, OwnTimeEntriesRowLevelRole.CODE);
            User pmUser = user(pm, List.of(ProjectManagerRole.CODE), List.of(ProjectParticipationRowLevelRole.CODE,
                    ManagedTimeEntriesRowLevelRole.CODE, ProjectManagerRowLevelRole.CODE));
            User empUser = user(emp, List.of(EmployeeRole.CODE), employeeRows);
            User aprUser = user(apr, List.of(EmployeeRole.CODE), employeeRows);
            User conUser = user(con, List.of(ContractorRole.CODE), employeeRows);
            User mgrEmpUser = user(mgrEmp, List.of(EmployeeRole.CODE), employeeRows);
            User otherUser = user(other, List.of(EmployeeRole.CODE), employeeRows);
            user(closer, List.of(EmployeeRole.CODE, MonthClosingRole.CODE), employeeRows);

            ProjectRole manager = projectRole(ProjectRoleType.MANAGER);
            ProjectRole approver = projectRole(ProjectRoleType.APPROVER);
            ProjectRole member = projectRole(ProjectRoleType.MEMBER);

            Client client = dataManager.create(Client.class);
            client.setName(prefix + "client");
            client.setContactInformation(dataManager.create(ContactInformation.class));
            client = dataManager.save(client);
            clientId = client.getId();

            projectA = project("A", client);
            projectB = project("B", client);
            participant(projectA, pmUser, manager);
            participant(projectA, empUser, member);
            participant(projectA, aprUser, approver);
            participant(projectA, conUser, member);
            participant(projectA, mgrEmpUser, manager);
            participant(projectB, pmUser, member);
            participant(projectB, otherUser, member);

            taskA = task(projectA);
            taskB = task(projectB);
            empEntryA = entry(taskA, empUser);
            pmEntryA = entry(taskA, pmUser);
            pmEntryB = entry(taskB, pmUser);
            otherEntryB = entry(taskB, otherUser);
        });
    }

    List<UUID> ids(List<?> entities) {
        return entities.stream().map(e -> (UUID) io.jmix.core.entity.EntityValues.getId(e)).toList();
    }

    private User user(String username, List<String> resourceRoles, List<String> rowLevelRoles) {
        User user = dataManager.create(User.class);
        user.setUsername(username);
        user.setFirstName(username);
        user.setActive(true);
        user = dataManager.save(user);
        usernames.add(username);
        resourceRoles.forEach(code -> assign(username, code, RoleAssignmentRoleType.RESOURCE));
        rowLevelRoles.forEach(code -> assign(username, code, RoleAssignmentRoleType.ROW_LEVEL));
        return user;
    }

    private void assign(String username, String roleCode, String roleType) {
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(username);
        assignment.setRoleCode(roleCode);
        assignment.setRoleType(roleType);
        dataManager.saveWithoutReload(assignment);
    }

    private ProjectRole projectRole(ProjectRoleType type) {
        ProjectRole role = dataManager.create(ProjectRole.class);
        role.setName(prefix + type.getId());
        role.setType(type);
        role = dataManager.save(role);
        projectRoleIds.add(role.getId());
        return role;
    }

    private Project project(String name, Client client) {
        Project project = dataManager.create(Project.class);
        project.setName(prefix + name);
        project.setDescription("Secret description of " + name);
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

    private Task task(Project project) {
        Task task = dataManager.create(Task.class);
        task.setName(prefix + "task");
        task.setProject(project);
        return dataManager.save(task);
    }

    private TimeEntry entry(Task task, User user) {
        TimeEntry entry = dataManager.create(TimeEntry.class);
        entry.setTask(task);
        entry.setUser(user);
        entry.setDate(LocalDate.now());
        entry.setSpentTime(new SpentTime(30));
        return dataManager.save(entry);
    }

    void remove() {
        if (clientId != null) {
            String projects = "select ID from TS_PROJECT where CLIENT_ID = ?";
            String tasks = "select ID from TS_TASK where PROJECT_ID in (" + projects + ")";
            jdbc.update("delete from TS_TIME_ENTRY where TASK_ID in (" + tasks + ")", clientId);
            jdbc.update("delete from TS_TASK where PROJECT_ID in (" + projects + ")", clientId);
            jdbc.update("delete from TS_PROJECT_PARTICIPANT where PROJECT_ID in (" + projects + ")", clientId);
            jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
            jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        }
        projectRoleIds.forEach(id -> jdbc.update("delete from TS_PROJECT_ROLE where ID = ?", id));
        usernames.forEach(username -> {
            jdbc.update("delete from SEC_ROLE_ASSIGNMENT where USERNAME = ?", username);
            jdbc.update("delete from TS_USER where USERNAME = ?", username);
        });
    }
}
