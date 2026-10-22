package com.company.timesheets.view.login;

import com.company.timesheets.TimesheetsApplication;
import com.company.timesheets.entity.User;
import io.jmix.core.CoreProperties;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.UserRepository;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.loginform.JmixLoginForm;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.testassist.notification.NotificationInfo;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.login.AbstractLogin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.UUID;

import static com.company.timesheets.test_support.AnonymousAuthentication.runWithAnonymous;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A contractor whose contract ended sees why sign-in fails: specs/03_access_control/05-contractor-invitation.
 */
@UiTest
@SpringBootTest(classes = {TimesheetsApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class LoginExpiredContractUiTest {

    private static final String PASSWORD = "contractor-pass";

    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CoreProperties coreProperties;
    @Autowired
    private DataSource dataSource;

    private final String username = "expired-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    @Test
    void theLoginViewNamesTheContractEndDate() {
        User user = dataManager.create(User.class);
        user.setUsername(username);
        user.setEmail(username);
        user.setActive(true);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setContractEndDate(LocalDate.now().minusDays(1));
        dataManager.save(user);

        runWithAnonymous(userRepository, coreProperties, () -> {
            viewNavigators.view(UiTestUtils.getCurrentView(), LoginView.class).navigate();
            LoginView view = UiTestUtils.getCurrentView();
            JmixLoginForm login = UiTestUtils.getComponent(view, "login");

            ComponentUtil.fireEvent(login, new AbstractLogin.LoginEvent(login, true, username, PASSWORD));

            NotificationInfo notification = UiTestUtils.getLastOpenedNotification();
            assertThat(notification).as("a message, not the generic login error").isNotNull();
            assertThat(notification.getText()).contains("contract ended");
        });
    }

    @AfterEach
    void tearDown() {
        new JdbcTemplate(dataSource).update("delete from TS_USER where USERNAME = ?", username);
    }
}
