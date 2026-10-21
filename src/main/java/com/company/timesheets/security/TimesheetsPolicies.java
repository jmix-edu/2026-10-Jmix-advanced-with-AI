package com.company.timesheets.security;

/**
 * Specific policies of the application. Code checks these policies, never role names;
 * see specs/03_access_control/01-roles.spec.adoc.
 */
public final class TimesheetsPolicies {

    /**
     * Close a month of a project into billing.
     */
    public static final String MONTH_CLOSING = "ts.monthClosing.close";

    /**
     * Approve or reject any time entry without being its project's manager or approver (admin).
     */
    public static final String APPROVE_ANY_TIME_ENTRY = "ts.timeEntry.approveAny";

    private TimesheetsPolicies() {
    }
}
