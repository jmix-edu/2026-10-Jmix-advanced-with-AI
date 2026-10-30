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
 * Самостоятельная регистрация по email: specs/03_access_control/03-registration.spec.adoc.
 * <p>
 * Вызывается анонимными посетителями, у которых нет прав на сущности, поэтому читает и пишет без ограничений доступа.
 * В БД хранится только SHA-256 токена активации; сам токен передаётся в ссылке из письма.
 */
@Service
public class RegistrationService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * Роли пользователя, активировавшего учётную запись: сотрудник со своими списаниями.
     */
    static final List<String> RESOURCE_ROLES = List.of(EmployeeRole.CODE);
    static final List<String> ROW_LEVEL_ROLES =
            List.of(ProjectParticipationRowLevelRole.CODE, OwnTimeEntriesRowLevelRole.CODE);

    /**
     * Роли приглашённого подрядчика, как у erik в демо-данных (spec 03/05).
     */
    static final List<String> CONTRACTOR_RESOURCE_ROLES = List.of(ContractorRole.CODE);
    static final List<String> CONTRACTOR_ROW_LEVEL_ROLES = ROW_LEVEL_ROLES;

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final int TOKEN_BYTES = 32;

    /**
     * Что регистрация сообщает посетителю. Одно и то же для нового, ждущего и уже занятого адреса,
     * чтобы ответ не выдавал, кто зарегистрирован.
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
     * Создаёт неактивного пользователя для нового адреса или выдаёт ждущему новую ссылку и отправляет её письмом.
     * Активного пользователя или отключённого администратором не трогает, письмо ему не уходит.
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
            // Этот пароль никто не знает, поэтому до активации учётной записью не воспользоваться.
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
     * Выдаёт пользователю новый токен активации (прежняя ссылка перестаёт работать) и возвращает его для письма.
     * Пользователь здесь не сохраняется.
     */
    public String issueToken(User user, Duration validity) {
        String token = newToken();
        user.setActivationTokenHash(hash(token));
        user.setActivationTokenExpiresAt(now().plus(validity));
        return token;
    }

    /**
     * Возвращает пароль, который никто не знает, для учётной записи, недоступной до активации.
     */
    public String unusablePassword() {
        return passwordEncoder.encode(newToken());
    }

    /**
     * Возвращает ссылку на экран активации для токена.
     */
    public String activationLink(String token) {
        return publicUrl + "/activate?token=" + token;
    }

    /**
     * Возвращает проекты, куда пользователь приглашён подрядчиком (spec 03/05); для самозарегистрированного — пусто.
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
     * Возвращает пользователя, ждущего активации по этой ссылке, если ссылка ещё действует.
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
     * Задаёт пароль и активирует пользователя одним сохранением. Самостоятельно зарегистрированный получает роли
     * сотрудника; приглашённый подрядчик (spec 03/05) — роли подрядчика и участие в проектах из своих приглашений,
     * которые затем удаляются.
     *
     * @return username активированного пользователя, для входа
     * @throws ActivationException если ссылка больше не действует или пароль слишком короткий
     */
    public String activate(String token, String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ActivationException(messages.formatMessage(getClass(), "activation.passwordTooShort",
                    MIN_PASSWORD_LENGTH));
        }
        // Проверяем ещё раз: ссылку могли тем временем использовать в другой вкладке.
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
     * Удаляет учётные записи, которые зарегистрировали, но не активировали до истечения ссылки
     * (specs/03_access_control/04-registration-cleanup.spec.adoc). Вызывается Quartz-джобом в потоке без
     * пользователя, отсюда {@code @Authenticated}. Учётную запись, на которую уже ссылаются (участник проекта,
     * списания), оставляет и пишет в лог. Каждая запись удаляется своим сохранением, чтобы один сбой
     * не остановил остальные.
     *
     * @return сколько учётных записей удалено
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
                // Сначала то, что ссылается на учётную запись: ожидающие приглашения (spec 03/05) её не используют.
                // В одном сохранении с пользователем EclipseLink удалит пользователя первым, и упадёт внешний ключ.
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
     * Учитывает и soft-deleted строки: они всё ещё держат внешний ключ на пользователя.
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
