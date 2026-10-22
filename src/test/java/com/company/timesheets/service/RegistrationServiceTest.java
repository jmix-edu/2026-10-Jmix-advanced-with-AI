package com.company.timesheets.service;

import com.company.timesheets.entity.User;
import com.company.timesheets.service.RegistrationService.RegistrationResult;
import io.jmix.core.CoreProperties;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.UserRepository;
import io.jmix.email.EmailException;
import io.jmix.email.EmailInfo;
import io.jmix.email.Emailer;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.company.timesheets.test_support.AnonymousAuthentication.withAnonymous;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Self-registration from the anonymous visitor's side: specs/03_access_control/03-registration.spec.adoc.
 * Emails are not sent: the Emailer is a mock that keeps what it was given.
 */
@SpringBootTest
@ActiveProfiles("test")
class RegistrationServiceTest {

    private static final Pattern TOKEN = Pattern.compile("/activate\\?token=([A-Za-z0-9_-]+)");
    private static final String PASSWORD = "secret-pass";

    @MockitoBean
    private Emailer emailer;
    @Autowired
    private RegistrationService registrationService;
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

    private final List<String> usernames = new ArrayList<>();

    @Test
    void registrationCreatesAnInactiveUserAndEmailsTheLink() throws Exception {
        String email = newEmail();

        RegistrationResult result = asAnonymous(() -> registrationService.register("  " + email.toUpperCase() + " ",
                "Ann", "Lee"));

        assertThat(result).isEqualTo(RegistrationResult.LINK_SENT);
        User user = user(email);
        assertThat(user.getUsername()).isEqualTo(email);
        assertThat(user.getActive()).isFalse();
        assertThat(user.getNeedsActivation()).isTrue();
        assertThat(user.getActivationTokenExpiresAt()).isNotNull();
        String token = sentToken(email);
        assertThat(user.getActivationTokenHash()).isEqualTo(RegistrationService.hash(token)).isNotEqualTo(token);
    }

    @Test
    void registeringAgainReplacesTheLink() throws Exception {
        String email = newEmail();
        asAnonymous(() -> registrationService.register(email, null, null));
        String first = sentToken(email);
        clearInvocations(emailer);

        asAnonymous(() -> registrationService.register(email, null, null));
        String second = sentToken(email);

        assertThat(asAnonymous(() -> registrationService.findByToken(first))).isEmpty();
        assertThat(asAnonymous(() -> registrationService.findByToken(second))).isPresent();
    }

    @Test
    void anActiveAccountIsLeftAloneAndGetsNoEmail() throws Exception {
        String email = newEmail();
        asAnonymous(() -> registrationService.register(email, null, null));
        String token = sentToken(email);
        asAnonymous(() -> registrationService.activate(token, PASSWORD));
        clearInvocations(emailer);

        RegistrationResult result = asAnonymous(() -> registrationService.register(email, "Changed", null));

        assertThat(result).as("same answer as for a new address").isEqualTo(RegistrationResult.LINK_SENT);
        verify(emailer, never()).sendEmail(any(EmailInfo.class));
        assertThat(user(email).getActive()).isTrue();
        assertThat(user(email).getFirstName()).isNull();
    }

    @Test
    void activationSetsThePasswordActivatesAndAssignsTheEmployeeRoles() throws Exception {
        String email = newEmail();
        asAnonymous(() -> registrationService.register(email, null, null));
        String token = sentToken(email);

        String username = asAnonymous(() -> registrationService.activate(token, PASSWORD));

        assertThat(username).isEqualTo(email);
        User user = user(email);
        assertThat(user.getActive()).isTrue();
        assertThat(user.getNeedsActivation()).isFalse();
        assertThat(user.getActivationTokenHash()).isNull();
        assertThat(user.getActivationTokenExpiresAt()).isNull();
        assertThat(passwordEncoder.matches(PASSWORD, user.getPassword())).isTrue();
        assertThat(roleCodes(email)).containsExactlyInAnyOrder("employee", "project-participation", "own-time-entries");
    }

    @Test
    void usedExpiredUnknownLinksAndShortPasswordsAreRefused() throws Exception {
        String email = newEmail();
        asAnonymous(() -> registrationService.register(email, null, null));
        String token = sentToken(email);

        assertThatThrownBy(() -> asAnonymous(() -> registrationService.activate(token, "short")))
                .isInstanceOf(ActivationException.class);
        assertThatThrownBy(() -> asAnonymous(() -> registrationService.activate("unknown-token", PASSWORD)))
                .isInstanceOf(ActivationException.class);
        assertThat(user(email).getActive()).isFalse();

        new JdbcTemplate(dataSource).update(
                "update TS_USER set ACTIVATION_TOKEN_EXPIRES_AT = ? where USERNAME = ?",
                java.time.OffsetDateTime.now().minusMinutes(1), email);
        assertThatThrownBy(() -> asAnonymous(() -> registrationService.activate(token, PASSWORD)))
                .as("expired").isInstanceOf(ActivationException.class);

        String fresh = registerAgain(email);
        asAnonymous(() -> registrationService.activate(fresh, PASSWORD));
        assertThatThrownBy(() -> asAnonymous(() -> registrationService.activate(fresh, PASSWORD)))
                .as("used").isInstanceOf(ActivationException.class);
    }

    @Test
    void aFailingMailServerLeavesTheUserInactive() throws Exception {
        String email = newEmail();
        doThrow(new EmailException("SMTP is down")).when(emailer).sendEmail(any(EmailInfo.class));

        RegistrationResult result = asAnonymous(() -> registrationService.register(email, null, null));

        assertThat(result).isEqualTo(RegistrationResult.MAIL_FAILED);
        assertThat(user(email).getActive()).isFalse();
        assertThat(user(email).getNeedsActivation()).isTrue();
    }

    private String registerAgain(String email) throws Exception {
        clearInvocations(emailer);
        asAnonymous(() -> registrationService.register(email, null, null));
        return sentToken(email);
    }

    private String sentToken(String email) throws Exception {
        ArgumentCaptor<EmailInfo> captor = ArgumentCaptor.forClass(EmailInfo.class);
        verify(emailer, times(1)).sendEmail(captor.capture());
        EmailInfo info = captor.getValue();
        assertThat(info.getAddresses()).isEqualTo(email);
        Matcher matcher = TOKEN.matcher(info.getBody());
        assertThat(matcher.find()).as("link in the email body").isTrue();
        return matcher.group(1);
    }

    private <T> T asAnonymous(java.util.function.Supplier<T> action) {
        return withAnonymous(userRepository, coreProperties, action);
    }

    private User user(String email) {
        return dataManager.load(User.class)
                .query("select u from ts_User u where u.username = :username")
                .parameter("username", email)
                .one();
    }

    private List<String> roleCodes(String username) {
        return dataManager.load(RoleAssignmentEntity.class)
                .query("select a from sec_RoleAssignmentEntity a where a.username = :username")
                .parameter("username", username)
                .list().stream().map(RoleAssignmentEntity::getRoleCode).toList();
    }

    private String newEmail() {
        String email = "reg-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        usernames.add(email);
        return email;
    }

    @AfterEach
    void tearDown() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        usernames.forEach(username -> {
            jdbc.update("delete from SEC_ROLE_ASSIGNMENT where USERNAME = ?", username);
            jdbc.update("delete from TS_USER where USERNAME = ?", username);
        });
    }
}
