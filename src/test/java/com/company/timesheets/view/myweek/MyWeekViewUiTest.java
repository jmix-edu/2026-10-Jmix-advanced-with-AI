package com.company.timesheets.view.myweek;

import com.company.timesheets.TimesheetsApplication;
import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.QueryParameters;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.ViewControllerUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The «My week» view opened by a link to a week, from specs/02_ui_development/01-my-week.spec.adoc.
 */
@UiTest
@SpringBootTest(classes = {TimesheetsApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class MyWeekViewUiTest {

    private static final String ADMIN = "admin";

    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private EntityStates entityStates;

    private final WeekPeriod lastWeek = WeekPeriod.of(LocalDate.now()).previous();
    private final List<UUID> clientIds = new ArrayList<>();
    private UUID otherUserId;

    private TimeEntry mondayEntry;
    private TimeEntry sundayEntry;
    private TimeEntry rejectedEntry;

    @BeforeEach
    void setUp() {
        systemAuthenticator.runWithUser(ADMIN, () -> {
            User admin = dataManager.load(User.class)
                    .query("select u from ts_User u where u.username = :username")
                    .parameter("username", ADMIN)
                    .one();
            User other = dataManager.create(User.class);
            other.setUsername("week-test-" + UUID.randomUUID());
            other = dataManager.save(other);
            otherUserId = other.getId();
            Task task = task();

            mondayEntry = entry(task, admin, lastWeek.start(), 90);
            sundayEntry = entry(task, admin, lastWeek.end(), 30);
            rejectedEntry = entry(task, admin, lastWeek.start().plusDays(2), 60);
            rejectedEntry.setStatus(TimeEntryStatus.REJECTED);
            rejectedEntry.setRejectionReason("Wrong task");
            rejectedEntry = dataManager.save(rejectedEntry);
            entry(task, admin, lastWeek.start().minusDays(1), 60);
            entry(task, admin, lastWeek.end().plusDays(1), 60);
            entry(task, other, lastWeek.start().plusDays(1), 60);
        });
    }

    @Test
    void linkToAWeekShowsOnlyMyEntriesOfThatWeekAndTheirTotal() {
        systemAuthenticator.runWithUser(ADMIN, () -> {
            viewNavigators.view(UiTestUtils.getCurrentView(), MyWeekView.class)
                    .withQueryParameters(QueryParameters.of(MyWeekView.WEEK_PARAMETER,
                            lastWeek.start().plusDays(3).toString()))
                    .navigate();
            MyWeekView view = UiTestUtils.getCurrentView();

            assertThat(view.getWeek()).isEqualTo(lastWeek);
            CollectionContainer<TimeEntry> container =
                    ViewControllerUtils.getViewData(view).getContainer("timeEntriesDc");
            assertThat(container.getItems()).extracting(TimeEntry::getId)
                    .containsExactlyInAnyOrder(mondayEntry.getId(), sundayEntry.getId(), rejectedEntry.getId());
            // The project link reads task.project: it comes with the rows, not one query per row.
            assertThat(container.getItems()).allSatisfy(entry -> {
                assertThat(entityStates.isLoaded(entry, "task")).isTrue();
                assertThat(entityStates.isLoaded(entry.getTask(), "project")).isTrue();
                assertThat(entityStates.isLoaded(entry.getTask().getProject(), "name")).isTrue();
            });

            Span weekTotal = UiTestUtils.getComponent(view, "weekTotal");
            // 1:30 + 0:30; the rejected hour does not count.
            assertThat(weekTotal.getText()).endsWith("2:00");
        });
    }

    @Test
    void unreadableWeekOpensTheCurrentWeek() {
        systemAuthenticator.runWithUser(ADMIN, () -> {
            viewNavigators.view(UiTestUtils.getCurrentView(), MyWeekView.class)
                    .withQueryParameters(QueryParameters.of(MyWeekView.WEEK_PARAMETER, "abc"))
                    .navigate();
            MyWeekView view = UiTestUtils.getCurrentView();

            assertThat(view.getWeek()).isEqualTo(WeekPeriod.of(LocalDate.now()));
        });
    }

    private Task task() {
        Client client = dataManager.create(Client.class);
        client.setName("Client " + UUID.randomUUID());
        client.setContactInformation(dataManager.create(ContactInformation.class));
        client = dataManager.save(client);
        clientIds.add(client.getId());

        Project project = dataManager.create(Project.class);
        project.setName("Project " + UUID.randomUUID());
        project.setClient(client);
        project = dataManager.save(project);

        Task task = dataManager.create(Task.class);
        task.setName("Task " + UUID.randomUUID());
        task.setProject(project);
        return dataManager.save(task);
    }

    private TimeEntry entry(Task task, User user, LocalDate date, long minutes) {
        TimeEntry entry = dataManager.create(TimeEntry.class);
        entry.setTask(task);
        entry.setUser(user);
        entry.setDate(date);
        entry.setSpentTime(new SpentTime(minutes));
        return dataManager.save(entry);
    }

    @AfterEach
    void tearDown() {
        // Plain SQL: soft delete would leave rows in the file-based test database.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (UUID clientId : clientIds) {
            String projectsOfClient = "select ID from TS_PROJECT where CLIENT_ID = ?";
            String taskIds = "select ID from TS_TASK where PROJECT_ID in (" + projectsOfClient + ")";
            jdbc.update("delete from TS_TIME_ENTRY where TASK_ID in (" + taskIds + ")", clientId);
            jdbc.update("delete from TS_TASK where PROJECT_ID in (" + projectsOfClient + ")", clientId);
            jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
            jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        }
        if (otherUserId != null) {
            jdbc.update("delete from TS_USER where ID = ?", otherUserId);
        }
    }
}
