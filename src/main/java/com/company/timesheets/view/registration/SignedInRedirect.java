package com.company.timesheets.view.registration;

import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.BeforeEnterEvent;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

/**
 * Registration and activation are for visitors: a signed-in user goes to the main view instead.
 */
final class SignedInRedirect {

    private SignedInRedirect() {
    }

    /**
     * @return true when the navigation was forwarded and the view must not go on
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
