package com.company.timesheets.view.publicdashboard;

import com.company.timesheets.TimesheetsApplication;
import com.company.timesheets.entity.DashboardValue;
import com.vaadin.flow.component.html.Span;
import io.jmix.core.CoreProperties;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.core.security.UserRepository;
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
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static com.company.timesheets.test_support.AnonymousAuthentication.runWithAnonymous;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The public dashboard opens for the anonymous user and fills its figures and charts' containers.
 */
@UiTest
@SpringBootTest(classes = {TimesheetsApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class PublicDashboardUiTest {

    @Autowired
    private ViewNavigators viewNavigators;
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
    void anonymousOpensTheDashboardWithData() {
        runWithAnonymous(userRepository, coreProperties, () -> {
            viewNavigators.view(UiTestUtils.getCurrentView(), PublicDashboardView.class).navigate();
            PublicDashboardView view = UiTestUtils.getCurrentView();

            assertThat(UiTestUtils.<Span>getComponent(view, "openProjectsValue").getText()).isNotBlank();
            assertThat(UiTestUtils.<Span>getComponent(view, "currentMonthValue").getText()).contains(":");
            for (String containerId : new String[]{"hoursByProjectDc", "hoursByMonthDc", "hoursByTaskTypeDc"}) {
                CollectionContainer<DashboardValue> container =
                        ViewControllerUtils.getViewData(view).getContainer(containerId);
                assertThat(container.getItems()).as(containerId).isNotEmpty();
            }
        });
    }
}
