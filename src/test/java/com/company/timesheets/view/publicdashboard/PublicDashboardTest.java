package com.company.timesheets.view.publicdashboard;

import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.DashboardValue;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.User;
import com.company.timesheets.service.PublicDashboardService;
import io.jmix.core.AccessManager;
import io.jmix.core.CoreProperties;
import io.jmix.core.DataManager;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.core.security.UserRepository;
import io.jmix.flowui.accesscontext.UiShowViewContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static com.company.timesheets.test_support.AnonymousAuthentication.runWithAnonymous;
import static com.company.timesheets.test_support.AnonymousAuthentication.withAnonymous;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The public dashboard from the anonymous user's side: specs/03_access_control/02-public-dashboard.spec.adoc.
 */
@SpringBootTest
@ActiveProfiles("test")
class PublicDashboardTest {

    @Autowired
    private PublicDashboardService dashboardService;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CoreProperties coreProperties;
    @Autowired
    private AccessManager accessManager;

    private DashboardFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new DashboardFixture(dataManager, systemAuthenticator, dataSource);
        fixture.create();
    }

    @AfterEach
    void tearDown() {
        fixture.remove();
    }

    @Test
    void anonymousGetsHoursOfApprovedAndClosedEntriesOnly() {
        YearMonth month = YearMonth.now();
        List<DashboardValue> byProject = withAnonymous(userRepository, coreProperties,
                () -> dashboardService.hoursByProject(month));
        List<DashboardValue> byTaskType = withAnonymous(userRepository, coreProperties,
                () -> dashboardService.hoursByTaskType(month));

        // 1:30 approved + 0:30 closed; the new 2:00 and the rejected 1:00 do not count.
        assertThat(hoursOf(byProject, fixture.prefix + "project")).isEqualByComparingTo("2.00");
        assertThat(hoursOf(byTaskType, fixture.prefix + "type")).isEqualByComparingTo("2.00");
    }

    @Test
    void monthsComeInARowWithZerosForEmptyOnes() {
        List<DashboardValue> byMonth = withAnonymous(userRepository, coreProperties,
                () -> dashboardService.hoursByMonth(12));

        assertThat(byMonth).hasSize(12);
        assertThat(byMonth.getLast().getLabel()).isEqualTo(YearMonth.now().toString());
        assertThat(byMonth.getFirst().getLabel()).isEqualTo(YearMonth.now().minusMonths(11).toString());
        assertThat(byMonth).allSatisfy(value -> assertThat(value.getHours()).isNotNull());
        assertThat(byMonth.get(10).getHours()).as("last month has at least our 3:00")
                .isGreaterThanOrEqualTo(new BigDecimal("3.00"));
    }

    @Test
    void anonymousReadsNoEntitiesDirectly() {
        runWithAnonymous(userRepository, coreProperties, () -> {
            // The fixture has a project, entries, a client and the admin user, so "nothing" means "closed".
            for (Class<?> entityClass : List.of(Project.class, TimeEntry.class, Client.class, User.class)) {
                List<?>[] loaded = {List.of()};
                Throwable thrown = catchThrowable(() -> loaded[0] = dataManager.load(entityClass).all().list());
                if (thrown == null) {
                    assertThat(loaded[0]).as(entityClass.getSimpleName()).isEmpty();
                } else {
                    assertThat(thrown).as(entityClass.getSimpleName()).isInstanceOf(AccessDeniedException.class);
                }
            }
        });
    }

    @Test
    void anonymousOpensOnlyTheDashboard() {
        runWithAnonymous(userRepository, coreProperties, () -> {
            assertThat(viewPermitted("ts_PublicDashboardView")).isTrue();
            assertThat(viewPermitted("ts_MainView")).isFalse();
            assertThat(viewPermitted("ts_Project.list")).isFalse();
            assertThat(viewPermitted("ts_Task.list-simple")).isFalse();
        });
    }

    private boolean viewPermitted(String viewId) {
        UiShowViewContext context = new UiShowViewContext(viewId);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    private static BigDecimal hoursOf(List<DashboardValue> values, String label) {
        return values.stream().filter(value -> label.equals(value.getLabel()))
                .map(DashboardValue::getHours).findFirst().orElseThrow();
    }
}
