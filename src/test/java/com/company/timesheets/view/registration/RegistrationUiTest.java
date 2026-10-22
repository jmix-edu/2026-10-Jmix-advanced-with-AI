package com.company.timesheets.view.registration;

import com.company.timesheets.TimesheetsApplication;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.QueryParameters;
import io.jmix.core.AccessManager;
import io.jmix.core.CoreProperties;
import io.jmix.core.security.UserRepository;
import io.jmix.email.Emailer;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.accesscontext.UiShowViewContext;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.View;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.company.timesheets.test_support.AnonymousAuthentication.runWithAnonymous;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sign-up and activation screens for an anonymous visitor: specs/03_access_control/03-registration.spec.adoc.
 */
@UiTest
@SpringBootTest(classes = {TimesheetsApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class RegistrationUiTest {

    @MockitoBean
    private Emailer emailer;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CoreProperties coreProperties;
    @Autowired
    private AccessManager accessManager;

    @Test
    void anonymousOpensOnlyTheDashboardSignUpAndActivation() {
        runWithAnonymous(userRepository, coreProperties, () -> {
            assertThat(viewPermitted("ts_RegistrationView")).isTrue();
            assertThat(viewPermitted("ts_ActivationView")).isTrue();
            assertThat(viewPermitted("ts_PublicDashboardView")).isTrue();
            assertThat(viewPermitted("ts_MainView")).isFalse();
            assertThat(viewPermitted("ts_User.list")).isFalse();
        });
    }

    @Test
    void anUnknownLinkShowsAMessageAndNoForm() {
        runWithAnonymous(userRepository, coreProperties, () -> {
            viewNavigators.view(UiTestUtils.getCurrentView(), ActivationView.class)
                    .withQueryParameters(QueryParameters.of(ActivationView.TOKEN_PARAMETER, "no-such-token"))
                    .navigate();
            ActivationView view = UiTestUtils.getCurrentView();

            assertThat(UiTestUtils.<VerticalLayout>getComponent(view, "invalidLinkBox").isVisible()).isTrue();
            assertThat(UiTestUtils.<VerticalLayout>getComponent(view, "activationForm").isVisible()).isFalse();
        });
    }

    @Test
    void aSignedInUserIsSentToTheMainView() {
        // @UiTest runs as the system user, which is signed in.
        viewNavigators.view(UiTestUtils.getCurrentView(), RegistrationView.class).navigate();
        View<?> current = UiTestUtils.getCurrentView();

        assertThat(current).isInstanceOf(MainView.class);
    }

    private boolean viewPermitted(String viewId) {
        UiShowViewContext context = new UiShowViewContext(viewId);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }
}
