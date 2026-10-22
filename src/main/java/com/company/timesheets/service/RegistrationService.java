package com.company.timesheets.service;

import com.company.timesheets.entity.ContractorInvitation;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.User;
import com.company.timesheets.security.ContractorRole;
import com.company.timesheets.security.EmployeeRole;
import com.company.timesheets.security.OwnTimeEntriesRowLevelRole;
import com.company.timesheets.security.ProjectParticipationRowLevelRole;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.SaveContext;
import io.jmix.core.TimeSource;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.Authenticated;
import io.jmix.data.PersistenceHints;
import io.jmix.email.EmailException;
import io.jmix.email.EmailInfo;
import io.jmix.email.EmailInfoBuilder;
import io.jmix.email.Emailer;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Self-registration by email: specs/03_access_control/03-registration.spec.adoc.
 * <p>
 * Called by anonymous visitors, who have no rights on entities, so it reads and writes without access constraints.
 * The database keeps only the SHA-256 of an activation token; the token itself travels in the email link.
 */
@Service
public class RegistrationService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * Roles of a user who activated their account: an employee with their own time entries.
     */
    static final List<String> RESOURCE_ROLES = List.of(EmployeeRole.CODE);
    static final List<String> ROW_LEVEL_ROLES =
            List.of(ProjectParticipationRowLevelRole.CODE, OwnTimeEntriesRowLevelRole.CODE);

    /**
     * Roles of an invited contractor, as erik has in the demo data (spec 03/05).
     */
    static final List<String> CONTRACTOR_RESOURCE_ROLES = List.of(ContractorRole.CODE);
    static final List<String> CONTRACTOR_ROW_LEVEL_ROLES = ROW_LEVEL_ROLES;

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final int TOKEN_BYTES = 32;

    /**
     * What registration tells the visitor. The same for a new, a waiting and an already used address,
     * so the answer does not reveal who is registered.
     */
    public enum RegistrationResult {
        LINK_SENT,
        MAIL_FAILED
    }

    private final UnconstrainedDataManager dataManager;
    private final PasswordEncoder passwordEncoder;
    private final Emailer emailer;
    private final Messages messages;
    private final TimeSource timeSource;
    private final SecureRandom random = new SecureRandom();
    private final String publicUrl;
    private final int activationHours;

    public RegistrationService(UnconstrainedDataManager dataManager, PasswordEncoder passwordEncoder, Emailer emailer,
                               Messages messages, TimeSource timeSource,
                               @Value("${timesheets.public-url}") String publicUrl,
                               @Value("${timesheets.registration.activation-hours:24}") int activationHours) {
        this.dataManager = dataManager;
        this.passwordEncoder = passwordEncoder;
        this.emailer = emailer;
        this.messages = messages;
        this.timeSource = timeSource;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.activationHours = activationHours;
    }

    /**
     * Creates an inactive user for a new address, or gives a waiting one a new link, and emails the link.
     * An active user, or one switched off by an administrator, is left alone and gets no email.
     */
    public RegistrationResult register(String email, @Nullable String firstName, @Nullable String lastName) {
        String username = normalize(email);
        User user = findByUsername(username).orElse(null);
        if (user != null && !Boolean.TRUE.equals(user.getNeedsActivation())) {
            log.info("Registration for an existing account '{}', nothing sent", username);
            return RegistrationResult.LINK_SENT;
        }
        if (user == null) {
            user = dataManager.create(User.class);
            user.setUsername(username);
            user.setEmail(username);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setActive(false);
            user.setNeedsActivation(true);
            // Nobody knows this password, so the account cannot be used before activation.
            user.setPassword(passwordEncoder.encode(newToken()));
        }
        String token = issueToken(user, Duration.ofHours(activationHours));
        user = dataManager.save(user);

        try {
            emailer.sendEmail(activationEmail(user, token));
            return RegistrationResult.LINK_SENT;
        } catch (EmailException | RuntimeException e) {
            log.error("Activation email to '{}' was not sent", username, e);
            return RegistrationResult.MAIL_FAILED;
        }
    }

    /**
     * Puts a new activation token on the user (the previous link stops working) and returns it for the email.
     * The user is not saved here.
     */
    public String issueToken(User user, Duration validity) {
        String token = newToken();
        user.setActivationTokenHash(hash(token));
        user.setActivationTokenExpiresAt(now().plus(validity));
        return token;
    }

    /**
     * A password nobody knows, for an account that must not be usable before activation.
     */
    public String unusablePassword() {
        return passwordEncoder.encode(newToken());
    }

    /**
     * Link to the activation view for the token.
     */
    public String activationLink(String token) {
        return publicUrl + "/activate?token=" + token;
    }

    /**
     * Projects the user is invited to as a contractor (spec 03/05); empty for a self-registered user.
     */
    public List<ContractorInvitation> invitationsOf(User user) {
        return dataManager.load(ContractorInvitation.class)
                .query("select i from ts_ContractorInvitation i where i.user = :user order by i.project.name")
                .parameter("user", user)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("project", FetchPlan.INSTANCE_NAME)
                        .add("projectRole", FetchPlan.INSTANCE_NAME))
                .list();
    }

    /**
     * The user waiting for activation with this link, if the link is still valid.
     */
    public Optional<User> findByToken(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return dataManager.load(User.class)
                .query("select u from ts_User u where u.activationTokenHash = :hash"
                        + " and u.needsActivation = true and u.activationTokenExpiresAt > :now")
                .parameter("hash", hash(token))
                .parameter("now", now())
                .optional();
    }

    /**
     * Sets the password and activates the user, in one save. A self-registered user gets the employee roles;
     * an invited contractor (spec 03/05) gets the contractor roles and joins the projects of their invitations,
     * which are then removed.
     *
     * @return the activated user's username, to sign in with
     * @throws ActivationException if the link is not valid any more or the password is too short
     */
    public String activate(String token, String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ActivationException(messages.formatMessage(getClass(), "activation.passwordTooShort",
                    MIN_PASSWORD_LENGTH));
        }
        // Checked again: the link may have been used in another tab meanwhile.
        User user = findByToken(token).orElseThrow(() ->
                new ActivationException(messages.getMessage(getClass(), "activation.linkInvalid")));
        user.setPassword(passwordEncoder.encode(password));
        user.setActive(true);
        user.setNeedsActivation(false);
        user.setActivationTokenHash(null);
        user.setActivationTokenExpiresAt(null);

        List<ContractorInvitation> invitations = invitationsOf(user);
        List<String> resourceRoles = invitations.isEmpty() ? RESOURCE_ROLES : CONTRACTOR_RESOURCE_ROLES;
        List<String> rowLevelRoles = invitations.isEmpty() ? ROW_LEVEL_ROLES : CONTRACTOR_ROW_LEVEL_ROLES;

        SaveContext saveContext = new SaveContext().saving(user);
        resourceRoles.forEach(code -> saveContext.saving(assignment(user, code, RoleAssignmentRoleType.RESOURCE)));
        rowLevelRoles.forEach(code -> saveContext.saving(assignment(user, code, RoleAssignmentRoleType.ROW_LEVEL)));
        for (ContractorInvitation invitation : invitations) {
            ProjectParticipant participant = dataManager.create(ProjectParticipant.class);
            participant.setUser(user);
            participant.setProject(invitation.getProject());
            participant.setProjectRole(invitation.getProjectRole());
            saveContext.saving(participant);
            saveContext.removing(invitation);
        }
        dataManager.save(saveContext);
        log.info("User '{}' activated", user.getUsername());
        return user.getUsername();
    }

    /**
     * Removes accounts that were registered but not activated before their link expired
     * (specs/03_access_control/04-registration-cleanup.spec.adoc). Called by the Quartz job, on a thread with no
     * user, hence {@code @Authenticated}. An account someone already referenced (a project participant, time
     * entries) is kept and logged. Each account goes in its own save, so one failure does not stop the rest.
     *
     * @return how many accounts were removed
     */
    @Authenticated
    public int removeExpiredRegistrations() {
        List<User> expired = dataManager.load(User.class)
                .query("select u from ts_User u where u.needsActivation = true and u.activationTokenExpiresAt < :now")
                .parameter("now", now())
                .list();
        int removed = 0;
        for (User user : expired) {
            if (isReferenced(user)) {
                log.warn("Expired registration '{}' is referenced by projects or time entries, kept",
                        user.getUsername());
                continue;
            }
            try {
                // First what refers to the account: pending invitations (spec 03/05) are not a use of it.
                // In one save with the user, EclipseLink deletes the user first and the foreign key fails.
                SaveContext dependents = new SaveContext();
                invitationsOf(user).forEach(dependents::removing);
                roleAssignments(user.getUsername()).forEach(dependents::removing);
                dataManager.save(dependents);
                dataManager.remove(user);
                removed++;
            } catch (RuntimeException e) {
                log.error("Expired registration '{}' was not removed", user.getUsername(), e);
            }
        }
        if (!expired.isEmpty()) {
            log.info("Expired registrations: {} found, {} removed", expired.size(), removed);
        }
        return removed;
    }

    /**
     * Counts soft-deleted rows too: they still hold a foreign key to the user.
     */
    private boolean isReferenced(User user) {
        return count("select count(p) from ts_ProjectParticipant p where p.user.id = :userId", user) > 0
                || count("select count(e) from ts_TimeEntry e where e.user.id = :userId", user) > 0;
    }

    private long count(String query, User user) {
        Long count = dataManager.loadValue(query, Long.class)
                .parameter("userId", user.getId())
                .hint(PersistenceHints.SOFT_DELETION, false)
                .one();
        return count == null ? 0 : count;
    }

    private List<RoleAssignmentEntity> roleAssignments(String username) {
        return dataManager.load(RoleAssignmentEntity.class)
                .query("select a from sec_RoleAssignmentEntity a where a.username = :username")
                .parameter("username", username)
                .list();
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private EmailInfo activationEmail(User user, String token) {
        String link = activationLink(token);
        return EmailInfoBuilder.create()
                .setAddresses(user.getEmail())
                .setSubject(messages.getMessage(getClass(), "activationEmail.subject"))
                .setBody(messages.formatMessage(getClass(), "activationEmail.body", link, activationHours))
                .setBodyContentType("text/plain; charset=UTF-8")
                .build();
    }

    private RoleAssignmentEntity assignment(User user, String roleCode, String roleType) {
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(user.getUsername());
        assignment.setRoleCode(roleCode);
        assignment.setRoleType(roleType);
        return assignment;
    }

    private Optional<User> findByUsername(String username) {
        return dataManager.load(User.class)
                .query("select u from ts_User u where u.username = :username")
                .parameter("username", username)
                .optional();
    }

    private OffsetDateTime now() {
        return timeSource.now().toOffsetDateTime();
    }
}
