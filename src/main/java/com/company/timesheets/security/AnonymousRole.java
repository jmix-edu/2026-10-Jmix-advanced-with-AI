package com.company.timesheets.security;

import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * The built-in anonymous user (before signing in): the public dashboard and nothing else.
 * No policies on entities: the dashboard gets its totals from PublicDashboardService.
 * See specs/03_access_control/02-public-dashboard.spec.adoc.
 */
@ResourceRole(name = "Anonymous", code = AnonymousRole.CODE, scope = SecurityScope.UI)
public interface AnonymousRole {

    String CODE = "anonymous";

    @ViewPolicy(viewIds = "ts_PublicDashboardView")
    void screens();
}
