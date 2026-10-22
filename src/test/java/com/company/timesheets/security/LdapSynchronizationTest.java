package com.company.timesheets.security;

import com.company.timesheets.entity.User;
import io.jmix.core.CoreProperties;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.core.security.UserRepository;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.company.timesheets.test_support.AnonymousAuthentication.runWithAnonymous;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * LDAP sign-in synchronization without a live server: specs/03_access_control/06-ldap.spec.adoc.
 * The user's LDAP entry is a {@link DirContextAdapter}; groups come as authorities, as the add-on passes them.
 * Synchronization runs as the anonymous user, like on the sign-in request; setup and checks run as system.
 */
@SpringBootTest
@ActiveProfiles("test")
class LdapSynchronizationTest {

    @Autowired
    private TimesheetsLdapUserSynchronizationStrategy strategy;
    @Autowired
    private EmployeeTypeRoleProvider employeeTypeRoleProvider;
    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CoreProperties coreProperties;

    private final List<String> usernames = new ArrayList<>();

    @Test
    void aNewUserIsCreatedFromTheEntryWithoutAPassword() {
        String username = newUsername();

        synchronize(entry(username, "Ivan", "Ivanov"), List.of("employees"));

        User user = user(username);
        assertThat(user.getFirstName()).isEqualTo("Ivan");
        assertThat(user.getLastName()).isEqualTo("Ivanov");
        assertThat(user.getEmail()).isEqualTo(username + "@example.org");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getActive()).isTrue();
        assertThat(user.getNeedsActivation()).isFalse();
    }

    @Test
    void anExistingUserGetsNewNamesAndKeepsTheRest() {
        String username = newUsername();
        synchronize(entry(username, "Ivan", "Ivanov"), List.of("employees"));
        User user = user(username);
        user.setTimeZoneId("Europe/Berlin");
        user.setContractEndDate(LocalDate.of(2030, 1, 31));
        systemAuthenticator.runWithSystem(() -> dataManager.save(user));

        synchronize(entry(username, "Vanya", "Ivanov-Petrov"), List.of("employees"));

        User updated = user(username);
        assertThat(updated.getFirstName()).isEqualTo("Vanya");
        assertThat(updated.getLastName()).isEqualTo("Ivanov-Petrov");
        assertThat(updated.getTimeZoneId()).isEqualTo("Europe/Berlin");
        assertThat(updated.getContractEndDate()).isEqualTo(LocalDate.of(2030, 1, 31));
    }

    @Test
    void groupsBecomeRoles() {
        String accountant = newUsername();
        String admin = newUsername();

        synchronize(entry(accountant, "Olga", "Petrova"), List.of("employees", "accounting", "division"));
        synchronize(entry(admin, "Pavel", "Sidorov"), List.of("admins", "division"));

        assertThat(roles(accountant)).containsExactlyInAnyOrder(
                "employee:" + RoleAssignmentRoleType.RESOURCE,
                "month-closing:" + RoleAssignmentRoleType.RESOURCE,
                "project-participation:" + RoleAssignmentRoleType.ROW_LEVEL,
                "own-time-entries:" + RoleAssignmentRoleType.ROW_LEVEL);
        assertThat(roles(admin)).containsExactly("system-full-access:" + RoleAssignmentRoleType.RESOURCE);
    }

    @Test
    void aRoleGivenByHandIsTakenAwayOnTheNextSignIn() {
        String username = newUsername();
        synchronize(entry(username, "Ivan", "Ivanov"), List.of("employees"));
        RoleAssignmentEntity manual = dataManager.create(RoleAssignmentEntity.class);
        manual.setUsername(username);
        manual.setRoleCode(ProjectManagerRole.CODE);
        manual.setRoleType(RoleAssignmentRoleType.RESOURCE);
        systemAuthenticator.runWithSystem(() -> dataManager.save(manual));

        synchronize(entry(username, "Ivan", "Ivanov"), List.of("employees"));

        assertThat(roles(username)).doesNotContain("project-manager:" + RoleAssignmentRoleType.RESOURCE)
                .contains("employee:" + RoleAssignmentRoleType.RESOURCE);
    }

    @Test
    void employeeTypeAddsKnownRowLevelRolesAndSkipsUnknownCodes() {
        String username = newUsername();
        DirContextAdapter entry = entry(username, "Ivan", "Ivanov");
        entry.setAttributeValues("employeeType", new String[]{"managed-time-entries", "no-such-role"});

        synchronize(entry, List.of("division"));

        assertThat(roles(username)).containsExactly("managed-time-entries:" + RoleAssignmentRoleType.ROW_LEVEL);
    }

    @AfterEach
    void cleanUp() {
        systemAuthenticator.runWithSystem(() -> usernames.forEach(this::remove));
    }

    private void remove(String username) {
            dataManager.load(RoleAssignmentEntity.class)
                    .query("select a from sec_RoleAssignmentEntity a where a.username = :username")
                    .parameter("username", username)
                    .list().forEach(dataManager::remove);
            dataManager.load(User.class)
                    .query("select u from ts_User u where u.username = :username")
                    .parameter("username", username)
                    .optional().ifPresent(dataManager::remove);
    }

    /**
     * What the add-on does on sign-in: groups plus the extra roles from the entry, then synchronization.
     */
    private void synchronize(DirContextAdapter entry, List<String> groups) {
        String username = entry.getStringAttribute("uid");
        List<GrantedAuthority> authorities = Stream.concat(
                groups.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new),
                employeeTypeRoleProvider.getAdditionalRoles(entry, username).stream()).toList();
        runWithAnonymous(userRepository, coreProperties,
                () -> strategy.synchronizeUserDetails(entry, username, authorities));
    }

    private DirContextAdapter entry(String username, String givenName, String sn) {
        DirContextAdapter entry = new DirContextAdapter();
        entry.setAttributeValue("uid", username);
        entry.setAttributeValue("givenName", givenName);
        entry.setAttributeValue("sn", sn);
        entry.setAttributeValue("mail", username + "@example.org");
        return entry;
    }

    private String newUsername() {
        String username = "ldap-" + UUID.randomUUID().toString().substring(0, 8);
        usernames.add(username);
        return username;
    }

    private User user(String username) {
        return systemAuthenticator.withSystem(() -> dataManager.load(User.class)
                .query("select u from ts_User u where u.username = :username")
                .parameter("username", username)
                .one());
    }

    private List<String> roles(String username) {
        return systemAuthenticator.withSystem(() -> dataManager.load(RoleAssignmentEntity.class)
                .query("select a from sec_RoleAssignmentEntity a where a.username = :username")
                .parameter("username", username)
                .list().stream().map(a -> a.getRoleCode() + ":" + a.getRoleType()).toList());
    }
}
