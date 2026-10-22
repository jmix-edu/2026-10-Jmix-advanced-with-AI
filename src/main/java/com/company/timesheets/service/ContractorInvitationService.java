package com.company.timesheets.service;

import com.company.timesheets.entity.ContractorInvitation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.User;
import com.company.timesheets.security.ContractorRole;
import com.company.timesheets.security.TimesheetsPolicies;
import io.jmix.core.Messages;
import io.jmix.core.SaveContext;
import io.jmix.core.TimeSource;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.metamodel.datatype.DatatypeFormatter;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.usersubstitution.CurrentUserSubstitution;
import io.jmix.email.EmailException;
import io.jmix.email.EmailInfoBuilder;
import io.jmix.email.Emailer;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A project manager invites a contractor by email: specs/03_access_control/05-contractor-invitation.spec.adoc.
 * <p>
 * Managers have no rights on users and role assignments, so this service writes without access constraints
 * after checking that the caller manages the project (or holds the invite-anywhere policy).
 * The activation link and the activation itself are those of self-registration (RegistrationService).
 */
@Service
public class ContractorInvitationService {

    private static final Logger log = LoggerFactory.getLogger(ContractorInvitationService.class);

    public enum InvitationResult {
        /**
         * A new or still waiting contractor got an activation link.
         */
        LINK_SENT,
        /**
         * An active contractor was added to the project right away and notified.
         */
        ADDED,
        /**
         * Saved, but the email did not go out; inviting again sends a new link.
         */
        MAIL_FAILED
    }

    private final UnconstrainedDataManager dataManager;
    private final RegistrationService registrationService;
    private final ProjectAccessSupport projectAccessSupport;
    private final CurrentUserSubstitution currentUserSubstitution;
    private final Emailer emailer;
    private final Messages messages;
    private final DatatypeFormatter datatypeFormatter;
    private final TimeSource timeSource;
    private final int activationDays;

    public ContractorInvitationService(UnconstrainedDataManager dataManager, RegistrationService registrationService,
                                       ProjectAccessSupport projectAccessSupport,
                                       CurrentUserSubstitution currentUserSubstitution, Emailer emailer,
                                       Messages messages, DatatypeFormatter datatypeFormatter, TimeSource timeSource,
                                       @Value("${timesheets.invitation.activation-days:7}") int activationDays) {
        this.dataManager = dataManager;
        this.registrationService = registrationService;
        this.projectAccessSupport = projectAccessSupport;
        this.currentUserSubstitution = currentUserSubstitution;
        this.emailer = emailer;
        this.messages = messages;
        this.datatypeFormatter = datatypeFormatter;
        this.timeSource = timeSource;
        this.activationDays = activationDays;
    }

    /**
     * Whether the current user may invite contractors to the project.
     */
    public boolean canInvite(@Nullable Project project) {
        return projectAccessSupport.isPermitted(TimesheetsPolicies.INVITE_CONTRACTOR_ANYWHERE)
                || projectAccessSupport.isManager(project);
    }

    /**
     * Invites a contractor. A new address gets an inactive account; an address still waiting for activation gets
     * a new link (the old one stops working) and the new contract end date; an active contractor joins the project
     * at once. An active employee is refused: they are added as a participant, not invited.
     */
    public InvitationResult invite(Project project, @Nullable String firstName, @Nullable String lastName,
                                   String email, ProjectRole projectRole, LocalDate contractEndDate) {
        if (!canInvite(project)) {
            throw new AccessDeniedException("specific", TimesheetsPolicies.INVITE_CONTRACTOR_ANYWHERE);
        }
        if (contractEndDate == null || !contractEndDate.isAfter(today())) {
            throw new InvitationException(messages.getMessage(getClass(), "invitation.contractEndInPast"));
        }
        String username = RegistrationService.normalize(email);
        User user = findByUsername(username).orElse(null);
        if (user != null && !Boolean.TRUE.equals(user.getNeedsActivation())) {
            return addActiveContractor(user, project, projectRole);
        }

        boolean newUser = user == null;
        if (newUser) {
            user = dataManager.create(User.class);
            user.setUsername(username);
            user.setEmail(username);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setActive(false);
            user.setNeedsActivation(true);
            user.setPassword(registrationService.unusablePassword());
        }
        user.setContractEndDate(contractEndDate);
        String token = registrationService.issueToken(user, Duration.ofDays(activationDays));

        SaveContext saveContext = new SaveContext().saving(user);
        saveContext.saving(invitation(user, newUser, project, projectRole));
        dataManager.save(saveContext);

        try {
            emailer.sendEmail(EmailInfoBuilder.create()
                    .setAddresses(username)
                    .setSubject(messages.formatMessage(getClass(), "invitationEmail.subject", project.getName()))
                    .setBody(messages.formatMessage(getClass(), "invitationEmail.body", inviterName(),
                            project.getName(), registrationService.activationLink(token), activationDays,
                            datatypeFormatter.formatLocalDate(contractEndDate)))
                    .setBodyContentType("text/plain; charset=UTF-8")
                    .build());
            return InvitationResult.LINK_SENT;
        } catch (EmailException | RuntimeException e) {
            log.error("Invitation email to '{}' was not sent", username, e);
            return InvitationResult.MAIL_FAILED;
        }
    }

    private InvitationResult addActiveContractor(User user, Project project, ProjectRole projectRole) {
        if (!hasResourceRole(user, ContractorRole.CODE)) {
            throw new InvitationException(messages.getMessage(getClass(), "invitation.notAContractor"));
        }
        Optional<ProjectParticipant> existing = dataManager.load(ProjectParticipant.class)
                .query("select p from ts_ProjectParticipant p where p.user = :user and p.project = :project")
                .parameter("user", user)
                .parameter("project", project)
                .optional();
        if (existing.isEmpty()) {
            ProjectParticipant participant = dataManager.create(ProjectParticipant.class);
            participant.setUser(user);
            participant.setProject(project);
            participant.setProjectRole(projectRole);
            dataManager.save(participant);
        }
        try {
            emailer.sendEmail(EmailInfoBuilder.create()
                    .setAddresses(user.getEmail())
                    .setSubject(messages.formatMessage(getClass(), "addedEmail.subject", project.getName()))
                    .setBody(messages.formatMessage(getClass(), "addedEmail.body", inviterName(), project.getName()))
                    .setBodyContentType("text/plain; charset=UTF-8")
                    .build());
        } catch (EmailException | RuntimeException e) {
            log.error("Notification to '{}' was not sent", user.getUsername(), e);
        }
        return InvitationResult.ADDED;
    }

    /**
     * The invitation of the user to the project; an existing one only gets the new role.
     */
    private ContractorInvitation invitation(User user, boolean newUser, Project project, ProjectRole projectRole) {
        Optional<ContractorInvitation> existing = newUser
                ? Optional.empty()
                : dataManager.load(ContractorInvitation.class)
                        .query("select i from ts_ContractorInvitation i where i.user = :user and i.project = :project")
                        .parameter("user", user)
                        .parameter("project", project)
                        .optional();
        ContractorInvitation invitation = existing
                .orElseGet(() -> {
                    ContractorInvitation created = dataManager.create(ContractorInvitation.class);
                    created.setUser(user);
                    created.setProject(project);
                    return created;
                });
        invitation.setProjectRole(projectRole);
        invitation.setInvitedBy(currentUser());
        return invitation;
    }

    private boolean hasResourceRole(User user, String roleCode) {
        return dataManager.load(RoleAssignmentEntity.class)
                .query("select a from sec_RoleAssignmentEntity a"
                        + " where a.username = :username and a.roleCode = :roleCode and a.roleType = :roleType")
                .parameter("username", user.getUsername())
                .parameter("roleCode", roleCode)
                .parameter("roleType", RoleAssignmentRoleType.RESOURCE)
                .maxResults(1)
                .optional().isPresent();
    }

    private Optional<User> findByUsername(String username) {
        return dataManager.load(User.class)
                .query("select u from ts_User u where u.username = :username")
                .parameter("username", username)
                .optional();
    }

    @Nullable
    private User currentUser() {
        return currentUserSubstitution.getEffectiveUser() instanceof User user ? user : null;
    }

    private String inviterName() {
        User inviter = currentUser();
        if (inviter == null) {
            return "Timesheets";
        }
        String name = ((inviter.getFirstName() == null ? "" : inviter.getFirstName()) + " "
                + (inviter.getLastName() == null ? "" : inviter.getLastName())).trim();
        return name.isEmpty() ? inviter.getUsername() : name;
    }

    private LocalDate today() {
        return timeSource.now().toLocalDate();
    }
}
