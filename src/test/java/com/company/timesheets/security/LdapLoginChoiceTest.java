package com.company.timesheets.security;

import com.company.timesheets.entity.User;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Who signs in with the database password: specs/03_access_control/06-ldap.spec.adoc.
 * The test LDAP URL points to a closed port, so nobody gets in through LDAP here.
 */
@SpringBootTest
@ActiveProfiles("test")
class LdapLoginChoiceTest {

    private static final String PASSWORD = "secret-pass";

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SystemAuthenticator systemAuthenticator;

    private final List<User> users = new ArrayList<>();

    @Test
    void aLocalUserOutsideTheStandardListSignsInWithTheDatabasePassword() {
        User user = user(passwordEncoder.encode(PASSWORD));

        assertThat(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), PASSWORD)).isAuthenticated()).isTrue();
    }

    @Test
    void aUserWithoutADatabasePasswordDoesNotSignInByTheDatabase() {
        User user = user(null);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), "")))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), PASSWORD)))
                .isInstanceOf(AuthenticationException.class);
    }

    @AfterEach
    void cleanUp() {
        systemAuthenticator.runWithSystem(() -> users.forEach(dataManager::remove));
    }

    private User user(String encodedPassword) {
        User user = dataManager.create(User.class);
        user.setUsername("local-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPassword(encodedPassword);
        User saved = systemAuthenticator.withSystem(() -> dataManager.save(user));
        users.add(saved);
        return saved;
    }
}
