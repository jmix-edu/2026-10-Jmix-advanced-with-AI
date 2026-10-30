package com.company.timesheets.service;

import java.util.Date;

/**
 * Месяц проекта не закрыт, потому что уже идёт другое закрытие того же проекта.
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
     * Логин пользователя, который держит блокировку.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Когда взята блокировка.
     */
    public Date getSince() {
        return since;
    }
}
