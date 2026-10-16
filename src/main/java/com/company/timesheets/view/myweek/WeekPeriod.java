package com.company.timesheets.view.myweek;

import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A calendar week from Monday to Sunday, whatever the locale.
 */
public record WeekPeriod(LocalDate start) {

    public WeekPeriod {
        if (start.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("A week starts on Monday: " + start);
        }
    }

    /**
     * The week the date falls in.
     */
    public static WeekPeriod of(LocalDate date) {
        return new WeekPeriod(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    /**
     * The week of an ISO date such as {@code 2026-10-12}; any day of the week will do. Empty when the text is
     * not a date.
     */
    public static Optional<WeekPeriod> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(of(LocalDate.parse(text.trim())));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public LocalDate end() {
        return start.plusDays(6);
    }

    public WeekPeriod previous() {
        return new WeekPeriod(start.minusWeeks(1));
    }

    public WeekPeriod next() {
        return new WeekPeriod(start.plusWeeks(1));
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end());
    }

    public List<LocalDate> days() {
        return Stream.iterate(start, day -> day.plusDays(1)).limit(7).toList();
    }

    /**
     * Minutes spent on each day of the week, Monday first. Rejected entries and entries of other weeks
     * do not count, as in the daily limit.
     */
    public Map<LocalDate, Long> minutesByDay(Collection<TimeEntry> entries) {
        Map<LocalDate, Long> minutes = new LinkedHashMap<>();
        days().forEach(day -> minutes.put(day, 0L));
        for (TimeEntry entry : entries) {
            if (entry.getDate() == null || !contains(entry.getDate()) || entry.getSpentTime() == null
                    || entry.getStatus() == TimeEntryStatus.REJECTED) {
                continue;
            }
            minutes.merge(entry.getDate(), entry.getSpentTime().minutes(), Long::sum);
        }
        return minutes;
    }
}
