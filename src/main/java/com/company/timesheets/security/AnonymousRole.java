package com.company.timesheets.security;

import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * Встроенный анонимный пользователь (до входа): публичный дашборд, регистрация и активация, больше ничего.
 * Политик на сущности нет: PublicDashboardService и RegistrationService читают и пишут без ограничений.
 * См. specs/03_access_control/02-public-dashboard.spec.adoc.
 */
@ResourceRole(name = "Anonymous", code = AnonymousRole.CODE, scope = SecurityScope.UI)
public interface AnonymousRole {

    String CODE = "anonymous";

    // Дашборд (спека 03/02), регистрация и активация (спека 03/03).
    @ViewPolicy(viewIds = {"ts_PublicDashboardView", "ts_RegistrationView", "ts_ActivationView"})
    void screens();
}
