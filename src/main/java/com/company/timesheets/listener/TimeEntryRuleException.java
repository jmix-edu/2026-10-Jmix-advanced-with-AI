package com.company.timesheets.listener;

/**
 * Thrown when saving or removing a {@link com.company.timesheets.entity.TimeEntry} breaks a time tracking rule.
 * The message is localized and can be shown to the user as is.
 */
public class TimeEntryRuleException extends RuntimeException {

    public TimeEntryRuleException(String message) {
        super(message);
    }
}
