package com.company.timesheets.test_support;

import io.jmix.core.CoreProperties;
import io.jmix.core.security.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.function.Supplier;

/**
 * Выполняет код от встроенного анонимного пользователя с тем же токеном, что приложение получает до входа.
 */
public final class AnonymousAuthentication {

    private AnonymousAuthentication() {
    }

    public static <T> T withAnonymous(UserRepository userRepository, CoreProperties coreProperties,
                                      Supplier<T> action) {
        UserDetails anonymous = userRepository.getAnonymousUser();
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AnonymousAuthenticationToken(coreProperties.getAnonymousAuthenticationTokenKey(),
                anonymous, anonymous.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    public static void runWithAnonymous(UserRepository userRepository, CoreProperties coreProperties,
                                        Runnable action) {
        withAnonymous(userRepository, coreProperties, () -> {
            action.run();
            return null;
        });
    }
}
