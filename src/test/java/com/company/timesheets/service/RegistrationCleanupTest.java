package com.company.timesheets.service;

import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.ProjectRoleType;
import com.company.timesheets.entity.User;
import io.jmix.core.CoreProperties;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.core.security.UserRepository;
import io.jmix.email.Emailer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.company.timesheets.test_support.AnonymousAuthentication.withAnonymous;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Removing registrations not activated in time: specs/03_access_control/04-registration-cleanup.spec.adoc.
 * The cleanup is called with no authentication, as the Quartz scheduler thread calls it.
 */
@SpringBootTest
@ActiveProfiles("test")
class RegistrationCleanupTest {

    @MockitoBean
    private Emailer emailer;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CoreProperties coreProperties;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JobDetail expiredRegistrationsCleanupJob;
    @Autowired
    private Trigger expiredRegistrationsCleanupTrigger;
    @Value("${timesheets.registration.cleanup-cron}")
    private String cron;

    private final String prefix = "cleanup-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    private final List<String> usernames = new ArrayList<>();
    private UUID clientId;
    private UUID projectRoleId;

    @Test
    void removesOnlyExpiredUnactivatedAccountsNobodyReferences() {
        User expired = systemAuthenticator.withSystem(() -> user("expired", true, false, -1));
        User waiting = systemAuthenticator.withSystem(() -> user("waiting", true, false, +1));
        User active = systemAuthenticator.withSystem(() -> user("active", false, true, -1));
        User disabled = systemAuthenticator.withSystem(() -> user("disabled", false, false, -1));
        User referenced = systemAuthenticator.withSystem(() -> {
            User user = user("referenced", true, false, -1);
            participantOf(user);
            return user;
        });
        SecurityContextHolder.clearContext();

        // As the scheduler thread: no authentication at all.
        int removed = registrationService.removeExpiredRegistrations();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(exists(expired)).as("expired, not activated").isFalse();
        assertThat(exists(waiting)).as("link still valid").isTrue();
        assertThat(exists(active)).as("active").isTrue();
        assertThat(exists(disabled)).as("switched off by an administrator").isTrue();
        assertThat(exists(referenced)).as("referenced by a project").isTrue();
    }

    @Test
    void aRemovedAddressCanBeRegisteredAgain() {
        User expired = systemAuthenticator.withSystem(() -> user("again", true, false, -1));
        registrationService.removeExpiredRegistrations();

        withAnonymous(userRepository, coreProperties,
                () -> registrationService.register(expired.getUsername(), null, null));

        User registered = dataManager.load(User.class)
                .query("select u from ts_User u where u.username = :username")
                .parameter("username", expired.getUsername())
                .one();
        assertThat(registered.getId()).isNotEqualTo(expired.getId());
        assertThat(registered.getNeedsActivation()).isTrue();
    }

    @Test
    void theJobIsScheduledFromTheProperty() {
        assertThat(expiredRegistrationsCleanupJob.getKey().toString())
                .isEqualTo("timesheets.expiredRegistrationsCleanup");
        assertThat(expiredRegistrationsCleanupJob.isDurable()).isTrue();
        assertThat(expiredRegistrationsCleanupTrigger).isInstanceOf(CronTrigger.class);
        assertThat(((CronTrigger) expiredRegistrationsCleanupTrigger).getCronExpression()).isEqualTo(cron);
        assertThat(expiredRegistrationsCleanupTrigger.getJobKey()).isEqualTo(expiredRegistrationsCleanupJob.getKey());
    }

    private User user(String name, boolean needsActivation, boolean active, int expiresInHours) {
        User user = dataManager.create(User.class);
        user.setUsername(prefix + name + "@example.com");
        user.setEmail(user.getUsername());
        user.setActive(active);
        user.setNeedsActivation(needsActivation);
        user.setActivationTokenHash(UUID.randomUUID().toString().replace("-", ""));
        user.setActivationTokenExpiresAt(OffsetDateTime.now().plusHours(expiresInHours));
        usernames.add(user.getUsername());
        return dataManager.save(user);
    }

    private void participantOf(User user) {
        Client client = dataManager.create(Client.class);
        client.setName(prefix + "client");
        client.setContactInformation(dataManager.create(ContactInformation.class));
        client = dataManager.save(client);
        clientId = client.getId();

        ProjectRole role = dataManager.create(ProjectRole.class);
        role.setName(prefix + "member");
        role.setType(ProjectRoleType.MEMBER);
        role = dataManager.save(role);
        projectRoleId = role.getId();

        Project project = dataManager.create(Project.class);
        project.setName(prefix + "project");
        project.setClient(client);
        project = dataManager.save(project);

        ProjectParticipant participant = dataManager.create(ProjectParticipant.class);
        participant.setProject(project);
        participant.setUser(user);
        participant.setProjectRole(role);
        dataManager.saveWithoutReload(participant);
    }

    private boolean exists(User user) {
        return dataManager.load(User.class).id(user.getId()).optional().isPresent();
    }

    @AfterEach
    void tearDown() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        if (clientId != null) {
            String projects = "select ID from TS_PROJECT where CLIENT_ID = ?";
            jdbc.update("delete from TS_PROJECT_PARTICIPANT where PROJECT_ID in (" + projects + ")", clientId);
            jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
            jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        }
        if (projectRoleId != null) {
            jdbc.update("delete from TS_PROJECT_ROLE where ID = ?", projectRoleId);
        }
        usernames.forEach(username -> {
            jdbc.update("delete from SEC_ROLE_ASSIGNMENT where USERNAME = ?", username);
            jdbc.update("delete from TS_USER where USERNAME = ?", username);
        });
    }
}
