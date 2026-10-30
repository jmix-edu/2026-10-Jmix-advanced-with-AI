package com.company.timesheets.security;

import com.company.timesheets.entity.User;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Не оставляет admin с паролем 'admin', который задаёт начальный changelog (спека 04/01). Срабатывает после Liquibase
 * и до того, как веб-сервер начнёт принимать запросы: заменяет пароль по умолчанию на
 * {@code timesheets.admin.initial-password} или останавливает запуск, если его нет.
 * Включается через {@code timesheets.admin.require-initial-password=true}; так делает только профиль prod.
 */
@Component("ts_AdminPasswordInitializer")
@ConditionalOnProperty(name = "timesheets.admin.require-initial-password", havingValue = "true")
public class AdminPasswordInitializer implements SmartInitializingSingleton {

    static final String ADMIN = "admin";
    static final String DEFAULT_PASSWORD = "admin";
    static final int MIN_LENGTH = 12;
    static final String INITIAL_PASSWORD_KEY = "timesheets.admin.initial-password";

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordInitializer.class);

    private final UnconstrainedDataManager dataManager;
    private final PasswordEncoder passwordEncoder;
    private final SystemAuthenticator systemAuthenticator;
    private final String initialPassword;

    public AdminPasswordInitializer(UnconstrainedDataManager dataManager,
                                    PasswordEncoder passwordEncoder,
                                    SystemAuthenticator systemAuthenticator,
                                    @Value("${" + INITIAL_PASSWORD_KEY + ":}") String initialPassword) {
        this.dataManager = dataManager;
        this.passwordEncoder = passwordEncoder;
        this.systemAuthenticator = systemAuthenticator;
        this.initialPassword = initialPassword;
    }

    // Во время запуска ни один пользователь не вошёл
    @Override
    public void afterSingletonsInstantiated() {
        systemAuthenticator.runWithSystem(this::replaceDefaultPassword);
    }

    private void replaceDefaultPassword() {
        User admin = dataManager.load(User.class)
                .query("select u from ts_User u where u.username = :username")
                .parameter("username", ADMIN)
                .optional()
                .orElse(null);
        if (admin == null || admin.getPassword() == null
                || !passwordEncoder.matches(DEFAULT_PASSWORD, admin.getPassword())) {
            return;
        }
        if (initialPassword.isBlank()) {
            throw new IllegalStateException("User admin still has the default password. Put "
                    + INITIAL_PASSWORD_KEY + " into Vault (secret/timesheets) and start again");
        }
        if (initialPassword.length() < MIN_LENGTH || DEFAULT_PASSWORD.equals(initialPassword)) {
            throw new IllegalStateException(INITIAL_PASSWORD_KEY + " must be at least " + MIN_LENGTH
                    + " characters long");
        }
        admin.setPassword(passwordEncoder.encode(initialPassword));
        dataManager.saveWithoutReload(admin);
        log.info("admin password set from {}", INITIAL_PASSWORD_KEY);
    }
}
