package com.company.timesheets.service;

import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.ContractorInvitation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.ProjectRoleType;
import com.company.timesheets.entity.User;
import com.company.timesheets.security.EmployeeRole;
import com.company.timesheets.security.ProjectManagerRole;
import com.company.timesheets.service.ContractorInvitationService.InvitationResult;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.email.EmailInfo;
import io.jmix.email.Emailer;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

/**
 * Inviting a contractor by email: specs/03_access_control/05-contractor-invitation.spec.adoc.
 * The Emailer is a mock that keeps the emails; the link token is read from the email body.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContractorInvitationTest {

    private static final Pattern TOKEN = Pattern.compile("/activate\\?token=([A-Za-z0-9_-]+)");
    private static final String PASSWORD = "contractor-pass";

    @MockitoBean
    private Emailer emailer;
    @Autowired
    private ContractorInvitationService invitationService;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private DataSource dataSource;

    private final String prefix = "inv-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    private final List<String> usernames = new ArrayList<>();
    private final List<UUID> projectRoleIds = new ArrayList<>();
    private UUID clientId;

    private String pmA;
    private String pmB;
    private String employee;
    private Project projectA;
    private Project projectB;
    private ProjectRole member;

    @BeforeEach
    void setUp() {
        systemAuthenticator.runWithSystem(() -> {
            ProjectRole manager = projectRole(ProjectRoleType.MANAGER);
            member = projectRole(ProjectRoleType.MEMBER);
            Client client = dataManager.create(Client.class);
            client.setName(prefix + "client");
            client.setContactInformation(dataManager.create(ContactInformation.class));
            client = dataManager.save(client);
            clientId = client.getId();
            projectA = project("A", client);
            projectB = project("B", client);

            pmA = prefix + "pma@example.com";
            pmB = prefix + "pmb@example.com";
            employee = prefix + "emp@example.com";
            participant(projectA, activeUser(pmA, ProjectManagerRole.CODE), manager);
            participant(projectB, activeUser(pmB, ProjectManagerRole.CODE), manager);
            participant(projectA, activeUser(employee, EmployeeRole.CODE), member);
        });
    }

    @Test
    void theManagerInvitesAndAnInactiveContractorGetsALinkForSevenDays() {
        String email = contractorEmail();
        LocalDate end = LocalDate.now().plusMonths(3);

        InvitationResult result = invite(pmA, projectA, email, end);

        assertThat(result).isEqualTo(InvitationResult.LINK_SENT);
        User user = user(email);
        assertThat(user.getActive()).isFalse();
        assertThat(user.getNeedsActivation()).isTrue();
        assertThat(user.getContractEndDate()).isEqualTo(end);
        assertThat(user.getActivationTokenExpiresAt())
                .isBetween(OffsetDateTime.now().plusDays(7).minusMinutes(1), OffsetDateTime.now().plusDays(7).plusMinutes(1));
        assertThat(invitations(email)).extracting(i -> i.getProject().getId()).containsExactly(projectA.getId());
        assertThat(lastToken(email)).isNotBlank();
    }

    @Test
    void onlyTheProjectsManagerInvites() {
        String email = contractorEmail();

        assertThat(catchThrowable(() -> invite(pmB, projectA, email, LocalDate.now().plusDays(30))))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(catchThrowable(() -> invite(employee, projectA, email, LocalDate.now().plusDays(30))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invitingTheSameEmailAgainKeepsOneUserAndReplacesTheLink() {
        String email = contractorEmail();
        invite(pmA, projectA, email, LocalDate.now().plusDays(30));
        String first = lastToken(email);
        clearInvocations(emailer);

        invite(pmA, projectA, "  " + email.toUpperCase() + " ", LocalDate.now().plusDays(60));
        String second = lastToken(email);

        assertThat(countUsers(email)).isEqualTo(1);
        assertThat(user(email).getContractEndDate()).isEqualTo(LocalDate.now().plusDays(60));
        assertThat(invitations(email)).hasSize(1);
        assertThat(findByToken(first)).isEmpty();
        assertThat(findByToken(second)).isPresent();
    }

    @Test
    void activationGivesTheContractorRolesAndEveryInvitedProject() {
        String email = contractorEmail();
        invite(pmA, projectA, email, LocalDate.now().plusDays(30));
        invite(pmB, projectB, email, LocalDate.now().plusDays(30));
        String token = lastToken(email);

        activate(token, PASSWORD);

        User user = user(email);
        assertThat(user.getActive()).isTrue();
        assertThat(user.getNeedsActivation()).isFalse();
        assertThat(roleCodes(email)).containsExactlyInAnyOrder("contractor", "project-participation", "own-time-entries");
        assertThat(participations(email)).extracting(p -> p.getProject().getId())
                .containsExactlyInAnyOrder(projectA.getId(), projectB.getId());
        assertThat(participations(email)).allMatch(p -> p.getProjectRole().getId().equals(member.getId()));
        assertThat(invitations(email)).isEmpty();
    }

    @Test
    void theLinkWorksOnceAndNotAfterItExpires() {
        String used = contractorEmail();
        invite(pmA, projectA, used, LocalDate.now().plusDays(30));
        String token = lastToken(used);
        activate(token, PASSWORD);
        assertThatThrownBy(() -> activate(token, PASSWORD)).isInstanceOf(ActivationException.class);

        String expired = contractorEmail();
        clearInvocations(emailer);
        invite(pmA, projectA, expired, LocalDate.now().plusDays(30));
        String expiredToken = lastToken(expired);
        jdbc().update("update TS_USER set ACTIVATION_TOKEN_EXPIRES_AT = ? where USERNAME = ?",
                OffsetDateTime.now().minusMinutes(1), expired);
        assertThatThrownBy(() -> activate(expiredToken, PASSWORD))
                .isInstanceOf(ActivationException.class);
        assertThat(user(expired).getActive()).isFalse();
    }

    @Test
    void signingInStopsTheDayAfterTheContractEnds() {
        String email = contractorEmail();
        invite(pmA, projectA, email, LocalDate.now().plusDays(30));
        activate(lastToken(email), PASSWORD);

        setContractEnd(email, LocalDate.now());
        assertThat(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, PASSWORD)).isAuthenticated())
                .as("the end date itself is still a working day").isTrue();

        setContractEnd(email, LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, PASSWORD)))
                .isInstanceOf(AccountExpiredException.class);
    }

    @Test
    void anActiveContractorJoinsAtOnceAndAnActiveEmployeeIsRefused() {
        String email = contractorEmail();
        invite(pmA, projectA, email, LocalDate.now().plusDays(30));
        activate(lastToken(email), PASSWORD);

        assertThat(invite(pmB, projectB, email, LocalDate.now().plusDays(30))).isEqualTo(InvitationResult.ADDED);
        assertThat(participations(email)).extracting(p -> p.getProject().getId())
                .containsExactlyInAnyOrder(projectA.getId(), projectB.getId());

        assertThatThrownBy(() -> invite(pmA, projectA, employee, LocalDate.now().plusDays(30)))
                .isInstanceOf(InvitationException.class);
    }

    @Test
    void cleanupRemovesAnExpiredInvitedContractorWithTheInvitation() {
        String email = contractorEmail();
        invite(pmA, projectA, email, LocalDate.now().plusDays(30));
        jdbc().update("update TS_USER set ACTIVATION_TOKEN_EXPIRES_AT = ? where USERNAME = ?",
                OffsetDateTime.now().minusMinutes(1), email);

        registrationService.removeExpiredRegistrations();

        assertThat(countUsers(email)).isZero();
        assertThat(jdbc().queryForObject("select count(*) from TS_CONTRACTOR_INVITATION i"
                + " join TS_PROJECT p on p.ID = i.PROJECT_ID where p.CLIENT_ID = ?", Long.class, clientId)).isZero();
    }

    // --- helpers ---

    // The activation view calls these as the anonymous visitor; here as the system user.
    private String activate(String token, String password) {
        return systemAuthenticator.withSystem(() -> registrationService.activate(token, password));
    }

    private java.util.Optional<User> findByToken(String token) {
        return systemAuthenticator.withSystem(() -> registrationService.findByToken(token));
    }

    private InvitationResult invite(String asUser, Project project, String email, LocalDate end) {
        return systemAuthenticator.withUser(asUser,
                () -> invitationService.invite(project, "Con", "Tractor", email, member, end));
    }

    private String lastToken(String email) {
        ArgumentCaptor<EmailInfo> captor = ArgumentCaptor.forClass(EmailInfo.class);
        try {
            verify(emailer, atLeastOnce()).sendEmail(captor.capture());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        EmailInfo info = captor.getValue();
        assertThat(info.getAddresses()).isEqualTo(RegistrationService.normalize(email));
        Matcher matcher = TOKEN.matcher(info.getBody());
        assertThat(matcher.find()).as("link in the invitation").isTrue();
        return matcher.group(1);
    }

    private String contractorEmail() {
        String email = prefix + "con" + usernames.size() + "@example.com";
        usernames.add(email);
        return email;
    }

    private User user(String email) {
        return dataManager.load(User.class).query("select u from ts_User u where u.username = :u")
                .parameter("u", email).one();
    }

    private long countUsers(String email) {
        return dataManager.load(User.class).query("select u from ts_User u where u.username = :u")
                .parameter("u", email).list().size();
    }

    private List<ContractorInvitation> invitations(String email) {
        return dataManager.load(ContractorInvitation.class)
                .query("select i from ts_ContractorInvitation i where i.user.username = :u")
                .parameter("u", email)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("project", "_base"))
                .list();
    }

    private List<ProjectParticipant> participations(String email) {
        return dataManager.load(ProjectParticipant.class)
                .query("select p from ts_ProjectParticipant p where p.user.username = :u")
                .parameter("u", email)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("project", "_base").add("projectRole", "_base"))
                .list();
    }

    private List<String> roleCodes(String username) {
        return dataManager.load(RoleAssignmentEntity.class)
                .query("select a from sec_RoleAssignmentEntity a where a.username = :u")
                .parameter("u", username)
                .list().stream().map(RoleAssignmentEntity::getRoleCode).toList();
    }

    private void setContractEnd(String email, LocalDate end) {
        jdbc().update("update TS_USER set CONTRACT_END_DATE = ? where USERNAME = ?", end, email);
    }

    private User activeUser(String username, String roleCode) {
        User user = dataManager.create(User.class);
        user.setUsername(username);
        user.setEmail(username);
        user.setFirstName("Pat");
        user.setActive(true);
        user = dataManager.save(user);
        usernames.add(username);
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(username);
        assignment.setRoleCode(roleCode);
        assignment.setRoleType(RoleAssignmentRoleType.RESOURCE);
        dataManager.saveWithoutReload(assignment);
        return user;
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

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        JdbcTemplate jdbc = jdbc();
        if (clientId != null) {
            String projects = "select ID from TS_PROJECT where CLIENT_ID = ?";
            jdbc.update("delete from TS_CONTRACTOR_INVITATION where PROJECT_ID in (" + projects + ")", clientId);
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
