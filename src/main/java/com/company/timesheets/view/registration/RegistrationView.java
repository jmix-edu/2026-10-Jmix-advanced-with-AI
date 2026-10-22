package com.company.timesheets.view.registration;

import com.company.timesheets.service.RegistrationService;
import com.company.timesheets.service.RegistrationService.RegistrationResult;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.component.textfield.JmixEmailField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Public sign-up by email: specs/03_access_control/03-registration.spec.adoc. No main view, the visitor is anonymous.
 */
@Route("register")
@ViewController(id = "ts_RegistrationView")
@ViewDescriptor(path = "registration-view.xml")
public class RegistrationView extends StandardView {

    @ViewComponent
    private JmixEmailField emailField;
    @ViewComponent
    private TypedTextField<String> firstNameField;
    @ViewComponent
    private TypedTextField<String> lastNameField;
    @ViewComponent
    private JmixButton registerButton;
    @ViewComponent
    private Span resultText;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private ViewValidation viewValidation;
    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!SignedInRedirect.forwardIfSignedIn(event, currentAuthentication)) {
            super.beforeEnter(event);
        }
    }

    @Subscribe("registerButton")
    public void onRegisterButtonClick(final ClickEvent<JmixButton> event) {
        ValidationErrors errors = viewValidation.validateUiComponents(List.<Component>of(emailField));
        if (!errors.isEmpty()) {
            viewValidation.showValidationErrors(errors);
            viewValidation.focusProblemComponent(errors);
            return;
        }
        RegistrationResult result = registrationService.register(emailField.getValue(),
                firstNameField.getTypedValue(), lastNameField.getTypedValue());
        resultText.setVisible(true);
        if (result == RegistrationResult.LINK_SENT) {
            // The same answer for any address: it does not tell whether the address is registered.
            resultText.setText(messageBundle.getMessage("linkSent"));
            List.of(emailField, firstNameField, lastNameField, registerButton).forEach(c -> c.setVisible(false));
        } else {
            resultText.setText(messageBundle.getMessage("mailFailed"));
        }
    }
}
