package com.company.timesheets.view.timeentry;

import com.company.timesheets.TimesheetsApplication;
import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.genericfilter.GenericFilter;
import io.jmix.flowui.component.propertyfilter.PropertyFilter;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.ViewData;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saved filter configurations of the time entry list from specs/01_data_manipulation/08-queries-locking.spec.adoc.
 * Runs the real view descriptor, so the JPQL macros are the ones users get.
 */
@UiTest
@SpringBootTest(classes = {TimesheetsApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class TimeEntryFiltersUiTest {

    private static final String ADMIN = "admin";

    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private DataSource dataSource;

    private final LocalDate today = LocalDate.now();
    private final YearMonth lastMonth = YearMonth.now().minusMonths(1);
    private final List<TimeEntry> created = new ArrayList<>();
    private final List<UUID> clientIds = new ArrayList<>();
    private UUID otherUserId;

    private TimeEntry mineToday;
    private TimeEntry mineSixDaysAgo;
    private TimeEntry mineSevenDaysAgo;
    private TimeEntry othersToday;
    private TimeEntry approved;

    @BeforeEach
    void setUp() {
        systemAuthenticator.runWithUser(ADMIN, () -> {
            User admin = dataManager.load(User.class)
                    .query("select u from ts_User u where u.username = :username")
                    .parameter("username", ADMIN)
                    .one();
            User other = dataManager.create(User.class);
            other.setUsername("filter-test-" + UUID.randomUUID());
            other = dataManager.save(other);
            otherUserId = other.getId();
            Task task = task();

            mineToday = entry(task, admin, today);
            mineSixDaysAgo = entry(task, admin, today.minusDays(6));
            mineSevenDaysAgo = entry(task, admin, today.minusDays(7));
            othersToday = entry(task, other, today);
            entry(task, other, lastMonth.atDay(1));
            entry(task, other, lastMonth.atEndOfMonth());
            entry(task, other, YearMonth.now().atDay(1));
            approved = entry(task, other, lastMonth.atDay(15));
            approved.setStatus(TimeEntryStatus.APPROVED);
            approved = dataManager.save(approved);
        });
    }

    @Test
    void myLastWeekShowsOnlyMyEntriesOfTheLastSevenDays() {
        Set<UUID> shown = shownWith("myLastWeek", null);

        // Not mineSevenDaysAgo (outside the window), not othersToday (someone else's).
        assertThat(shown).containsExactlyInAnyOrder(mineToday.getId(), mineSixDaysAgo.getId());
    }

    @Test
    void lastMonthShowsEveryonesEntriesOfThePreviousCalendarMonth() {
        Set<UUID> shown = shownWith("lastMonth", null);

        assertThat(shown).isEqualTo(idsOf(e -> YearMonth.from(e.getDate()).equals(lastMonth)));
        assertThat(shown).isNotEmpty();
    }

    @Test
    void byStatusShowsOnlyTheChosenStatus() {
        Set<UUID> shown = shownWith("byStatus", TimeEntryStatus.APPROVED);

        assertThat(shown).containsExactly(approved.getId());
    }

    /**
     * Opens the list as admin, applies the configuration and returns the ids of this test's entries it shows.
     */
    @SuppressWarnings("unchecked")
    private Set<UUID> shownWith(String configurationId, Object propertyValue) {
        return systemAuthenticator.withUser(ADMIN, () -> {
            viewNavigators.view(UiTestUtils.getCurrentView(), TimeEntryListView.class).navigate();
            TimeEntryListView view = UiTestUtils.getCurrentView();
            GenericFilter filter = UiTestUtils.getComponent(view, "genericFilter");

            filter.setCurrentConfiguration(filter.getConfiguration(configurationId));
            if (propertyValue != null) {
                filter.getCurrentConfiguration().getRootLogicalFilterComponent().getFilterComponents().stream()
                        .filter(PropertyFilter.class::isInstance)
                        .map(component -> (PropertyFilter<Object>) component)
                        .findFirst().orElseThrow()
                        .setValue(propertyValue);
            }
            filter.apply();
            ViewData viewData = ViewControllerUtils.getViewData(view);
            CollectionLoader<TimeEntry> loader = viewData.getLoader("timeEntriesDl");
            loader.load();

            CollectionContainer<TimeEntry> container = viewData.getContainer("timeEntriesDc");
            Set<UUID> ours = idsOf(e -> true);
            return container.getItems().stream()
                    .map(TimeEntry::getId)
                    .filter(ours::contains)
                    .collect(Collectors.toSet());
        });
    }

    private Set<UUID> idsOf(Predicate<TimeEntry> predicate) {
        return created.stream().filter(predicate).map(TimeEntry::getId).collect(Collectors.toSet());
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

    private TimeEntry entry(Task task, User user, LocalDate date) {
        TimeEntry entry = dataManager.create(TimeEntry.class);
        entry.setTask(task);
        entry.setUser(user);
        entry.setDate(date);
        entry.setSpentTime(new SpentTime(30));
        TimeEntry saved = dataManager.save(entry);
        created.add(saved);
        return saved;
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
