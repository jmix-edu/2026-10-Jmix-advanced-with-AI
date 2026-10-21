package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.DashboardValue;
import com.company.timesheets.entity.ProjectStatus;
import io.jmix.core.Messages;
import io.jmix.core.Metadata;
import io.jmix.core.TimeSource;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.entity.KeyValueEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Totals for the public dashboard, which anonymous visitors open (specs/03_access_control/02-public-dashboard).
 * <p>
 * Reads without access constraints, because the anonymous user has no rights on entities, and returns
 * aggregates only: project and task type names with hours. No clients, users, descriptions or single entries.
 * Hours count as in the reports: approved and closed entries.
 */
@Service
public class PublicDashboardService {

    private final UnconstrainedDataManager dataManager;
    private final Metadata metadata;
    private final Messages messages;
    private final TimeSource timeSource;

    public PublicDashboardService(UnconstrainedDataManager dataManager, Metadata metadata, Messages messages,
                                  TimeSource timeSource) {
        this.dataManager = dataManager;
        this.metadata = metadata;
        this.messages = messages;
        this.timeSource = timeSource;
    }

    public DashboardSummary summary() {
        YearMonth month = currentMonth();
        Long openProjects = dataManager.loadValue("select count(p) from ts_Project p where p.status = :open",
                        Long.class)
                .parameter("open", ProjectStatus.OPEN)
                .one();
        return new DashboardSummary(openProjects == null ? 0 : openProjects,
                new SpentTime(totalMinutes(month)), new SpentTime(totalMinutes(month.minusMonths(1))));
    }

    /**
     * Open projects with time in the month, most hours first.
     */
    public List<DashboardValue> hoursByProject(YearMonth month) {
        List<KeyValueEntity> rows = dataManager.loadValues("""
                        select e.task.project.id, e.task.project.name, sum(e.spentTime) from ts_TimeEntry e
                        where e.status in :statuses and e.date between :from and :to
                          and e.task.project.status = :open
                        group by e.task.project.id, e.task.project.name""")
                .properties("projectId", "name", "minutes")
                .parameter("statuses", HoursReportService.COUNTED_STATUSES)
                .parameter("from", month.atDay(1))
                .parameter("to", month.atEndOfMonth())
                .parameter("open", ProjectStatus.OPEN)
                .list();
        return rows.stream()
                .map(row -> value(row.getValue("name"), toLong(row.getValue("minutes"))))
                .filter(value -> value.getHours().signum() > 0)
                .sorted(Comparator.comparing(DashboardValue::getHours).reversed())
                .toList();
    }

    /**
     * The given number of months up to the current one, oldest first; months without time are zero.
     */
    public List<DashboardValue> hoursByMonth(int months) {
        YearMonth last = currentMonth();
        YearMonth first = last.minusMonths(months - 1L);
        Map<YearMonth, Long> minutes = new LinkedHashMap<>();
        Stream.iterate(first, m -> m.plusMonths(1)).limit(months).forEach(m -> minutes.put(m, 0L));
        dataManager.loadValues("""
                        select e.date, sum(e.spentTime) from ts_TimeEntry e
                        where e.status in :statuses and e.date between :from and :to
                        group by e.date""")
                .properties("date", "minutes")
                .parameter("statuses", HoursReportService.COUNTED_STATUSES)
                .parameter("from", first.atDay(1))
                .parameter("to", last.atEndOfMonth())
                .list()
                .forEach(row -> minutes.merge(YearMonth.from(row.<LocalDate>getValue("date")),
                        toLong(row.getValue("minutes")), Long::sum));
        List<DashboardValue> values = new ArrayList<>();
        minutes.forEach((month, total) -> values.add(value(month.toString(), total)));
        return values;
    }

    /**
     * Hours of the month by task type, most hours first; tasks without a type go together.
     */
    public List<DashboardValue> hoursByTaskType(YearMonth month) {
        return dataManager.loadValues("""
                        select tt.name, sum(e.spentTime) from ts_TimeEntry e left join e.task.taskType tt
                        where e.status in :statuses and e.date between :from and :to
                        group by tt.name""")
                .properties("name", "minutes")
                .parameter("statuses", HoursReportService.COUNTED_STATUSES)
                .parameter("from", month.atDay(1))
                .parameter("to", month.atEndOfMonth())
                .list().stream()
                .map(row -> value(row.getValue("name") == null
                                ? messages.getMessage(getClass(), "withoutTaskType")
                                : row.getValue("name"),
                        toLong(row.getValue("minutes"))))
                .filter(value -> value.getHours().signum() > 0)
                .sorted(Comparator.comparing(DashboardValue::getHours).reversed())
                .toList();
    }

    public YearMonth currentMonth() {
        return YearMonth.from(timeSource.now().toLocalDate());
    }

    private long totalMinutes(YearMonth month) {
        Object total = dataManager.loadValue("""
                        select sum(e.spentTime) from ts_TimeEntry e
                        where e.status in :statuses and e.date between :from and :to""", Object.class)
                .parameter("statuses", HoursReportService.COUNTED_STATUSES)
                .parameter("from", month.atDay(1))
                .parameter("to", month.atEndOfMonth())
                .optional().orElse(null);
        return toLong(total);
    }

    private DashboardValue value(String label, long minutes) {
        DashboardValue value = metadata.create(DashboardValue.class);
        value.setLabel(label);
        value.setHours(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        return value;
    }

    /**
     * SUM over the SpentTime column skips the converter: a plain number whose type depends on the database.
     */
    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
