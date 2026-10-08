package com.company.timesheets.demo;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.ProjectRoleType;
import com.company.timesheets.entity.ProjectStatus;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TaskStatus;
import com.company.timesheets.entity.TaskType;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import com.company.timesheets.service.MonthClosingService;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.data.PersistenceHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an empty database with demo data on startup: when the only user is admin and there are no projects.
 * Disabled with {@code timesheets.demo-data.enabled=false} (the test profile does so).
 * <p>
 * Everything is saved through DataManager, so the time entry rules apply: entries are created as NEW
 * and then moved through the allowed status transitions.
 */
@Component
@ConditionalOnProperty(name = "timesheets.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String DEMO_PASSWORD = "password";

    private final DataManager dataManager;
    private final SystemAuthenticator systemAuthenticator;
    private final PasswordEncoder passwordEncoder;
    private final MonthClosingService monthClosingService;

    public DemoDataSeeder(DataManager dataManager, SystemAuthenticator systemAuthenticator,
                          PasswordEncoder passwordEncoder, MonthClosingService monthClosingService) {
        this.dataManager = dataManager;
        this.systemAuthenticator = systemAuthenticator;
        this.passwordEncoder = passwordEncoder;
        this.monthClosingService = monthClosingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Startup has no logged-in user; DataManager needs an authentication.
        systemAuthenticator.runWithSystem(() -> {
            if (!isEmpty()) {
                return;
            }
            log.info("Database has only admin, creating demo data");
            try {
                seed();
                log.info("Demo data created");
            } catch (RuntimeException e) {
                // Demo data must not prevent the application from starting.
                log.error("Failed to create demo data", e);
            }
        });
    }

    private boolean isEmpty() {
        boolean onlyAdmin = dataManager.load(User.class).all().maxResults(2).list().size() <= 1;
        boolean noProjects = dataManager.load(Project.class).all()
                .hint(PersistenceHints.SOFT_DELETION, false)
                .maxResults(1).list().isEmpty();
        return onlyAdmin && noProjects;
    }

    private void seed() {
        User alice = user("alice", "Alice", "Morgan");
        User boris = user("boris", "Boris", "Ivanov");
        User chen = user("chen", "Chen", "Li");
        User dana = user("dana", "Dana", "Kowalski");

        TaskType development = taskType("Development", "Writing and reviewing code");
        TaskType testing = taskType("Testing", "Manual and automated testing");
        TaskType management = taskType("Management", "Planning, meetings, reporting");

        ProjectRole manager = projectRole("Manager", ProjectRoleType.MANAGER);
        ProjectRole approver = projectRole("Approver", ProjectRoleType.APPROVER);
        ProjectRole developer = projectRole("Developer", ProjectRoleType.MEMBER);
        ProjectRole observer = projectRole("Observer", ProjectRoleType.OBSERVER);

        Client acme = client("Acme Corp", "office@acme.example", "+1 555 0100", "https://acme.example");
        Client globex = client("Globex", "hello@globex.example", "+1 555 0200", "https://globex.example");
        Client initech = client("Initech", "contact@initech.example", "+1 555 0300", "https://initech.example");

        Project website = project("Website Redesign", acme, "New corporate website on the current design system");
        Project mobile = project("Mobile App", acme, "Customer app for iOS and Android");
        Project crm = project("CRM Integration", globex, "Sync orders and contacts with the client's CRM");
        Project migration = project("Data Migration", initech, "Move archives from the legacy database");
        Project support = project("Legacy Support", globex, "Maintenance of the old order portal");

        participants(website, alice, manager, boris, developer, chen, developer);
        participants(mobile, dana, manager, chen, developer, alice, approver);
        participants(crm, boris, manager, dana, developer, alice, observer);
        participants(migration, chen, manager, dana, developer);
        participants(support, alice, approver, boris, developer);

        Task websiteLayout = task(website, "Page layouts", development);
        Task websiteQa = task(website, "Cross-browser testing", testing);
        Task mobileApi = task(mobile, "Backend API", development);
        Task mobilePlanning = task(mobile, "Sprint planning", management);
        Task crmMapping = task(crm, "Field mapping", development);
        Task crmTests = task(crm, "Integration tests", testing);
        Task migrationScripts = task(migration, "Migration scripts", development);
        Task migrationCheck = task(migration, "Data reconciliation", testing);
        Task supportFixes = task(support, "Bug fixes", development);
        Task supportReports = task(support, "Monthly reports", management);

        LocalDate today = LocalDate.now();
        List<TimeEntry> entries = new ArrayList<>();
        entries.add(entry(websiteLayout, alice, today.minusDays(1), 180, "Header and footer"));
        entries.add(entry(websiteLayout, boris, today.minusDays(1), 360, "Product pages"));
        entries.add(entry(websiteQa, chen, today.minusDays(2), 240, "Safari and Firefox"));
        entries.add(entry(websiteLayout, boris, today.minusDays(2), 300, "Landing page"));
        entries.add(entry(mobileApi, chen, today.minusDays(3), 420, "Auth endpoints"));
        entries.add(entry(mobilePlanning, dana, today.minusDays(3), 90, "Sprint 12 planning"));
        entries.add(entry(mobileApi, chen, today.minusDays(4), 330, "Push notifications"));
        entries.add(entry(mobilePlanning, alice, today.minusDays(4), 60, "Backlog grooming"));
        entries.add(entry(crmMapping, dana, today.minusDays(5), 270, "Contacts mapping"));
        entries.add(entry(crmTests, boris, today.minusDays(5), 150, "Order sync tests"));
        entries.add(entry(crmMapping, dana, today.minusDays(6), 390, "Orders mapping"));
        entries.add(entry(crmTests, alice, today.minusDays(6), 120, "Test review"));
        entries.add(entry(migrationScripts, chen, today.minusDays(7), 450, "Customers table"));
        entries.add(entry(migrationCheck, dana, today.minusDays(7), 210, "Row counts"));
        entries.add(entry(migrationScripts, chen, today.minusDays(8), 300, "Invoices table"));
        // Legacy Support worked last month only, so that month can be closed before the project is.
        LocalDate lastMonthEnd = today.withDayOfMonth(1).minusDays(1);
        entries.add(entry(supportFixes, boris, lastMonthEnd, 240, "Checkout error"));
        entries.add(entry(supportReports, alice, lastMonthEnd, 120, "Monthly report"));
        entries.add(entry(supportFixes, boris, lastMonthEnd.minusDays(1), 180, "Slow search"));
        entries.add(entry(supportFixes, boris, lastMonthEnd.minusDays(2), 150, "Login timeout"));
        entries.add(entry(supportReports, alice, lastMonthEnd.minusDays(2), 90, "Usage statistics"));
        entries.add(entry(websiteQa, chen, today, 120, "Mobile layouts"));
        entries.add(entry(mobileApi, chen, today, 240, "Offline mode"));
        entries.add(entry(crmMapping, dana, today, 180, "Error handling"));
        entries.add(entry(websiteLayout, alice, today, 60, "Review"));

        // Status mix: approved and rejected entries across projects.
        for (int i : new int[]{0, 1, 2, 4, 6, 8, 9, 12}) {
            approve(entries.get(i));
        }
        reject(entries.get(3), "Duplicates the entry for the product pages");
        reject(entries.get(10), "Wrong task: orders belong to a separate ticket");

        // Legacy Support: approve everything but one entry, bill last month, then close the project.
        // Closing the month moves the approved entries to CLOSED; the last one stays NEW.
        for (int i = 15; i <= 18; i++) {
            approve(entries.get(i));
        }
        monthClosingService.closeMonth(support.getId(), YearMonth.from(lastMonthEnd));
        support.setStatus(ProjectStatus.CLOSED);
        dataManager.saveWithoutReload(support);

        // A finished task: its entries stay, new time cannot be entered.
        migrationCheck.setStatus(TaskStatus.INACTIVE);
        dataManager.saveWithoutReload(migrationCheck);
    }

    private User user(String username, String firstName, String lastName) {
        User user = dataManager.create(User.class);
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(username + "@timesheets.example");
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        user.setActive(true);
        return dataManager.save(user);
    }

    private TaskType taskType(String name, String description) {
        TaskType taskType = dataManager.create(TaskType.class);
        taskType.setName(name);
        taskType.setDescription(description);
        return dataManager.save(taskType);
    }

    private ProjectRole projectRole(String name, ProjectRoleType type) {
        ProjectRole role = dataManager.create(ProjectRole.class);
        role.setName(name);
        role.setType(type);
        return dataManager.save(role);
    }

    private Client client(String name, String email, String phone, String url) {
        ContactInformation contacts = dataManager.create(ContactInformation.class);
        contacts.setEmail(email);
        contacts.setPhone(phone);
        contacts.setUrl(url);
        Client client = dataManager.create(Client.class);
        client.setName(name);
        client.setContactInformation(contacts);
        return dataManager.save(client);
    }

    private Project project(String name, Client client, String description) {
        Project project = dataManager.create(Project.class);
        project.setName(name);
        project.setClient(client);
        project.setDescription(description);
        return dataManager.save(project);
    }

    /**
     * @param usersAndRoles pairs of {@link User} and {@link ProjectRole}
     */
    private void participants(Project project, Object... usersAndRoles) {
        SaveContext saveContext = new SaveContext();
        for (int i = 0; i < usersAndRoles.length; i += 2) {
            ProjectParticipant participant = dataManager.create(ProjectParticipant.class);
            participant.setProject(project);
            participant.setUser((User) usersAndRoles[i]);
            participant.setProjectRole((ProjectRole) usersAndRoles[i + 1]);
            saveContext.saving(participant);
        }
        dataManager.save(saveContext);
    }

    private Task task(Project project, String name, TaskType taskType) {
        Task task = dataManager.create(Task.class);
        task.setProject(project);
        task.setName(name);
        task.setTaskType(taskType);
        return dataManager.save(task);
    }

    private TimeEntry entry(Task task, User user, LocalDate date, long minutes, String description) {
        TimeEntry entry = dataManager.create(TimeEntry.class);
        entry.setTask(task);
        entry.setUser(user);
        entry.setDate(date);
        entry.setSpentTime(new SpentTime(minutes));
        entry.setDescription(description);
        return dataManager.save(entry);
    }

    private void approve(TimeEntry entry) {
        entry.setStatus(TimeEntryStatus.APPROVED);
        dataManager.saveWithoutReload(entry);
    }

    private void reject(TimeEntry entry, String reason) {
        entry.setStatus(TimeEntryStatus.REJECTED);
        entry.setRejectionReason(reason);
        dataManager.saveWithoutReload(entry);
    }
}
