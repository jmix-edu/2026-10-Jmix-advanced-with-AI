package com.company.timesheets.listener;

/**
 * Выбрасывается, когда сохранение или удаление {@link com.company.timesheets.entity.TimeEntry} нарушает правило учёта времени.
 * Сообщение локализовано, его можно показать пользователю как есть.
 */
public class TimeEntryRuleException extends RuntimeException {

    public TimeEntryRuleException(String message) {
        super(message);
    }
}
