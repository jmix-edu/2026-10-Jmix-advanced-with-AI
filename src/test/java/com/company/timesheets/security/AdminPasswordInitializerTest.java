package com.company.timesheets.security;

import com.company.timesheets.entity.User;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Пароль admin при первом запуске в production: specs/04_deployment_operations/01-production-profile.spec.adoc.
 * В тестах инициализатор выключен; каждый тест создаёт свой со своим начальным паролем и запускает его, как при старте.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminPasswordInitializerTest {

    private static final String INITIAL = "first-deploy-secret";

    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SystemAuthenticator systemAuthenticator;

    private String storedPassword;

    @BeforeEach
    void rememberAdminPassword() {
        storedPassword = admin().getPassword();
        setAdminPassword("{noop}admin");
    }

    @AfterEach
    void restoreAdminPassword() {
        setAdminPassword(storedPassword);
    }

    @Test
    void theDefaultPasswordIsReplacedWithTheInitialOne() {
        initializer(INITIAL).afterSingletonsInstantiated();

        String password = admin().getPassword();
        assertThat(passwordEncoder.matches(INITIAL, password)).isTrue();
        assertThat(passwordEncoder.matches("admin", password)).isFalse();
    }

    @Test
    void withoutAnInitialPasswordTheStartupStops() {
        assertThatThrownBy(() -> initializer("").afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timesheets.admin.initial-password");
        assertThat(passwordEncoder.matches("admin", admin().getPassword())).isTrue();
    }

    @Test
    void aShortInitialPasswordStopsTheStartup() {
        assertThatThrownBy(() -> initializer("short-pass").afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12");
    }

    @Test
    void aChangedPasswordIsLeftAlone() {
        setAdminPassword(passwordEncoder.encode("changed-by-admin"));

        initializer(INITIAL).afterSingletonsInstantiated();
        initializer("").afterSingletonsInstantiated();

        assertThat(passwordEncoder.matches("changed-by-admin", admin().getPassword())).isTrue();
    }

    private AdminPasswordInitializer initializer(String initialPassword) {
        return new AdminPasswordInitializer(dataManager, passwordEncoder, systemAuthenticator, initialPassword);
    }

    private User admin() {
        return systemAuthenticator.withSystem(() -> dataManager.load(User.class)
                .query("select u from ts_User u where u.username = :username")
                .parameter("username", AdminPasswordInitializer.ADMIN)
                .one());
    }

    private void setAdminPassword(String password) {
        User admin = admin();
        admin.setPassword(password);
        systemAuthenticator.runWithSystem(() -> dataManager.saveWithoutReload(admin));
    }
}
