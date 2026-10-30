package com.company.timesheets.security;

/**
 * Специфические политики приложения. Код проверяет эти политики, а не имена ролей;
 * см. specs/03_access_control/01-roles.spec.adoc.
 */
public final class TimesheetsPolicies {

    /**
     * Закрыть месяц проекта в биллинг.
     */
    public static final String MONTH_CLOSING = "ts.monthClosing.close";

    /**
     * Одобрить или отклонить любое списание, не будучи руководителем или согласующим его проекта (admin).
     */
    public static final String APPROVE_ANY_TIME_ENTRY = "ts.timeEntry.approveAny";

    /**
     * Пригласить подрядчика в любой проект, а не только в тот, которым руководит пользователь (admin), спека 03/05.
     */
    public static final String INVITE_CONTRACTOR_ANYWHERE = "ts.contractor.inviteAny";

    private TimesheetsPolicies() {
    }
}
