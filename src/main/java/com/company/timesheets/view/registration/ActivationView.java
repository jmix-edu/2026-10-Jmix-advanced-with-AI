package com.company.timesheets.view.registration;

import com.company.timesheets.entity.ContractorInvitation;
import com.company.timesheets.entity.User;
import com.company.timesheets.service.ActivationException;
import com.company.timesheets.service.RegistrationService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.textfield.JmixPasswordField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.securityflowui.authentication.AuthDetails;
import io.jmix.securityflowui.authentication.LoginViewSupport;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Activation by the emailed link: the user sets a password, gets the employee roles and is signed in.
 * See specs/03_access_control/03-registration.spec.adoc.
 */
@Route("activate")
@ViewController(id = "ts_ActivationView")
@ViewDescriptor(path = "activation-view.xml")
public class ActivationView extends StandardView {

    static final String TOKEN_PARAMETER = "token";

    @ViewComponent
    private VerticalLayout activationForm;
    @ViewComponent
    private VerticalLayout invalidLinkBox;
    @ViewComponent
    private Span welcomeText;
    @ViewComponent
    private Span invitedText;
    @ViewComponent
    private JmixPasswordField passwordField;
    @ViewComponent
    private JmixPasswordField confirmPasswordField;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private LoginViewSupport loginViewSupport;
    @Autowired
    private Notifications notifications;
    @Autowired
    private CurrentAuthentication currentAuthentication;

    private String token;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!SignedInRedirect.forwardIfSignedIn(event, currentAuthentication)) {
            super.beforeEnter(event);
        }
    }

    @Subscribe
    public void onQueryParametersChange(final QueryParametersChangeEvent event) {
        token = event.getQueryParameters().getSingleParameter(TOKEN_PARAMETER).orElse(null);
        User user = registrationService.findByToken(token).orElse(null);
        activationForm.setVisible(user != null);
        invalidLinkBox.setVisible(user == null);
        if (user != null) {
            welcomeText.setText(messageBundle.formatMessage("welcome", user.getEmail()));
            List<ContractorInvitation> invitations = registrationService.invitationsOf(user);
            invitedText.setVisible(!invitations.isEmpty());
            invitedText.setText(messageBundle.formatMessage("invitedTo", invitations.stream()
                    .map(invitation -> invitation.getProject().getName())
                    .collect(Collectors.joining(", "))));
        }
    }

    @Subscribe("activateButton")
    public void onActivateButtonClick(final ClickEvent<JmixButton> event) {
        String password = passwordField.getValue();
        if (!Objects.equals(password, confirmPasswordField.getValue())) {
            confirmPasswordField.setErrorMessage(messageBundle.getMessage("passwordsDoNotMatch"));
            confirmPasswordField.setInvalid(true);
            return;
        }
        String username;
        try {
            username = registrationService.activate(token, password);
        } catch (ActivationException e) {
            notifications.create(e.getMessage()).withType(Notifications.Type.WARNING).show();
            return;
        }
        // Signs in like the login view; on success LoginViewSupport opens the main view.
        loginViewSupport.authenticate(AuthDetails.of(username, password));
    }
}
