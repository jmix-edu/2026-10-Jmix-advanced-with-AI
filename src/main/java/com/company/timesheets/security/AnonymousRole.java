package com.company.timesheets.security;

import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * The built-in anonymous user (before signing in): the public dashboard, sign-up and activation, nothing else.
 * No policies on entities: PublicDashboardService and RegistrationService read and write without constraints.
 * See specs/03_access_control/02-public-dashboard.spec.adoc.
 */
@ResourceRole(name = "Anonymous", code = AnonymousRole.CODE, scope = SecurityScope.UI)
public interface AnonymousRole {

    String CODE = "anonymous";

    // Dashboard (spec 03/02), sign-up and activation (spec 03/03).
    @ViewPolicy(viewIds = {"ts_PublicDashboardView", "ts_RegistrationView", "ts_ActivationView"})
    void screens();
}
