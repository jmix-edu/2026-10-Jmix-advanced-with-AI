package com.company.timesheets.view.registration;

import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.BeforeEnterEvent;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

/**
 * Регистрация и активация — для посетителей: вошедший пользователь вместо них попадает на главный экран.
 */
final class SignedInRedirect {

    private SignedInRedirect() {
    }

    /**
     * @return true, если навигация перенаправлена и экран не должен продолжать
     */
    static boolean forwardIfSignedIn(BeforeEnterEvent event, CurrentAuthentication currentAuthentication) {
        boolean signedIn = currentAuthentication.isSet()
                && !(currentAuthentication.getAuthentication() instanceof AnonymousAuthenticationToken);
        if (signedIn) {
            event.forwardTo(MainView.class);
        }
        return signedIn;
    }
}
