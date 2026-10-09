package com.company.timesheets.service;

import java.util.Date;

/**
 * The month of the project is not closed because another closing of the same project is running.
 */
public class MonthClosingLockedException extends RuntimeException {

    private final String username;
    private final Date since;

    public MonthClosingLockedException(String username, Date since) {
        super("Month closing of the project is already running by " + username + " since " + since);
        this.username = username;
        this.since = since;
    }

    /**
     * Login of the user who holds the lock.
     */
    public String getUsername() {
        return username;
    }

    /**
     * When the lock was taken.
     */
    public Date getSince() {
        return since;
    }
}
