package com.company.timesheets.view.myweek;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import io.jmix.core.Metadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Week arithmetic and totals of the «My week» view, from specs/02_ui_development/01-my-week.spec.adoc.
 */
@SpringBootTest
@ActiveProfiles("test")
class WeekPeriodTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final WeekPeriod WEEK = new WeekPeriod(MONDAY);

    @Autowired
    private Metadata metadata;

    @Test
    void everyDayOfAWeekBelongsToTheSameWeek() {
        assertThat(WeekPeriod.of(MONDAY)).isEqualTo(WEEK);
        assertThat(WeekPeriod.of(LocalDate.of(2026, 10, 14))).isEqualTo(WEEK);
        assertThat(WeekPeriod.of(LocalDate.of(2026, 10, 18))).isEqualTo(WEEK);
        assertThat(WEEK.end()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(WEEK.days()).hasSize(7).startsWith(MONDAY).endsWith(WEEK.end());
    }

    @Test
    void weeksStartOnMonday() {
        assertThatThrownBy(() -> new WeekPeriod(LocalDate.of(2026, 10, 13)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void previousAndNextCrossTheYear() {
        WeekPeriod newYear = new WeekPeriod(LocalDate.of(2025, 12, 29));

        assertThat(newYear.end()).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(newYear.next()).isEqualTo(new WeekPeriod(LocalDate.of(2026, 1, 5)));
        assertThat(newYear.next().previous()).isEqualTo(newYear);
        assertThat(newYear.previous()).isEqualTo(new WeekPeriod(LocalDate.of(2025, 12, 22)));
    }

    @Test
    void parseTakesTheWeekOfAnyDateAndRejectsTheRest() {
        assertThat(WeekPeriod.parse("2026-10-12")).contains(WEEK);
        assertThat(WeekPeriod.parse("2026-10-15")).contains(WEEK);
        assertThat(WeekPeriod.parse("abc")).isEmpty();
        assertThat(WeekPeriod.parse("2026-13-01")).isEmpty();
        assertThat(WeekPeriod.parse("")).isEmpty();
        assertThat(WeekPeriod.parse(null)).isEmpty();
    }

    @Test
    void totalsSkipRejectedEntriesAndOtherWeeks() {
        List<TimeEntry> entries = List.of(
                entry(MONDAY, 90, TimeEntryStatus.NEW),
                entry(MONDAY, 30, TimeEntryStatus.APPROVED),
                entry(MONDAY.plusDays(2), 600, TimeEntryStatus.REJECTED),
                entry(MONDAY.plusDays(6), 45, TimeEntryStatus.NEW),
                entry(MONDAY.minusDays(1), 60, TimeEntryStatus.NEW),
                entry(MONDAY.plusDays(7), 60, TimeEntryStatus.NEW));

        Map<LocalDate, Long> minutes = WEEK.minutesByDay(entries);

        assertThat(minutes.keySet()).containsExactlyElementsOf(WEEK.days());
        assertThat(minutes.get(MONDAY)).isEqualTo(120);
        assertThat(minutes.get(MONDAY.plusDays(1))).isZero();
        assertThat(minutes.get(MONDAY.plusDays(2))).isZero();
        assertThat(minutes.get(MONDAY.plusDays(6))).isEqualTo(45);
        assertThat(minutes.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(165);
    }

    private TimeEntry entry(LocalDate date, long minutes, TimeEntryStatus status) {
        // Not saved: the totals only read the attributes.
        TimeEntry entry = metadata.create(TimeEntry.class);
        entry.setDate(date);
        entry.setSpentTime(new SpentTime(minutes));
        entry.setStatus(status);
        return entry;
    }
}
