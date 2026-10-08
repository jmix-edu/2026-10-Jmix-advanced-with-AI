package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectSummary;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Metadata;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.entity.KeyValueEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only reports on approved and closed time. Archived (soft-deleted) records are not counted.
 */
@Service
public class HoursReportService {

    static final List<TimeEntryStatus> COUNTED_STATUSES = List.of(TimeEntryStatus.APPROVED, TimeEntryStatus.CLOSED);

    private final DataManager dataManager;
    private final Metadata metadata;

    public HoursReportService(DataManager dataManager, Metadata metadata) {
        this.dataManager = dataManager;
        this.metadata = metadata;
    }

    /**
     * Counted time per project and user for the period, both bounds included.
     * Each row has {@code project}, {@code user}, {@code totalTime} ({@link SpentTime}) and {@code entryCount}.
     */
    public List<KeyValueEntity> loadHoursByProjectAndUser(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid period: " + from + " - " + to);
        }
        // Grouping by ids keeps the SQL valid on every database; entities are loaded once below.
        List<KeyValueEntity> rows = dataManager.loadValues("""
                        select e.task.project.id, e.user.id, sum(e.spentTime), count(e)
                        from ts_TimeEntry e
                        where e.date between :from and :to and e.status in :statuses
                        group by e.task.project.id, e.user.id""")
                .properties("projectId", "userId", "totalMinutes", "entryCount")
                .parameter("from", from)
                .parameter("to", to)
                .parameter("statuses", COUNTED_STATUSES)
                .list();

        Map<UUID, Project> projects = loadByIds(Project.class, rows, "projectId");
        Map<UUID, User> users = loadByIds(User.class, rows, "userId");
        for (KeyValueEntity row : rows) {
            row.setValue("project", projects.get(row.<UUID>getValue("projectId")));
            row.setValue("user", users.get(row.<UUID>getValue("userId")));
            row.setValue("totalTime", new SpentTime(toLong(row.getValue("totalMinutes"))));
        }
        rows.sort(Comparator.<KeyValueEntity, String>comparing(r -> r.<Project>getValue("project").getName())
                .thenComparing(r -> r.<User>getValue("user").getUsername()));
        return rows;
    }

    /**
     * Totals for every non-archived project, open and closed, ordered by name.
     */
    public List<ProjectSummary> loadProjectSummaries() {
        List<Project> projects = dataManager.load(Project.class)
                .query("select p from ts_Project p order by p.name")
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("client", FetchPlan.INSTANCE_NAME))
                .list();

        Map<UUID, Long> totalMinutes = sumByProject(null, null);
        LocalDate today = LocalDate.now();
        Map<UUID, Long> monthMinutes = sumByProject(today.withDayOfMonth(1), today);
        Map<UUID, Long> participants = dataManager.loadValues("""
                        select p.project.id, count(distinct p.user.id)
                        from ts_ProjectParticipant p
                        group by p.project.id""")
                .properties("projectId", "count")
                .list().stream()
                .collect(Collectors.toMap(r -> r.getValue("projectId"), r -> toLong(r.getValue("count"))));

        return projects.stream().map(project -> {
            ProjectSummary summary = metadata.create(ProjectSummary.class);
            summary.setId(project.getId());
            summary.setProject(project);
            summary.setClient(project.getClient());
            summary.setStatus(project.getStatus());
            summary.setTotalTime(new SpentTime(totalMinutes.getOrDefault(project.getId(), 0L)));
            summary.setCurrentMonthTime(new SpentTime(monthMinutes.getOrDefault(project.getId(), 0L)));
            summary.setParticipantCount(participants.getOrDefault(project.getId(), 0L).intValue());
            return summary;
        }).toList();
    }

    /**
     * Counted minutes per project id; without a period, for all time.
     */
    private Map<UUID, Long> sumByProject(LocalDate from, LocalDate to) {
        String period = from == null ? "" : " and e.date between :from and :to";
        var loader = dataManager.loadValues("select e.task.project.id, sum(e.spentTime) from ts_TimeEntry e"
                        + " where e.status in :statuses" + period
                        + " group by e.task.project.id")
                .properties("projectId", "totalMinutes")
                .parameter("statuses", COUNTED_STATUSES);
        if (from != null) {
            loader = loader.parameter("from", from).parameter("to", to);
        }
        return loader.list().stream()
                .collect(Collectors.toMap(r -> r.getValue("projectId"), r -> toLong(r.getValue("totalMinutes"))));
    }

    private <E> Map<UUID, E> loadByIds(Class<E> entityClass, List<KeyValueEntity> rows, String idProperty) {
        Collection<UUID> ids = rows.stream().map(r -> r.<UUID>getValue(idProperty)).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String entityName = metadata.getClass(entityClass).getName();
        return dataManager.load(entityClass)
                .query("select e from " + entityName + " e where e.id in :ids")
                .parameter("ids", ids)
                .list().stream()
                .collect(Collectors.toMap(e -> (UUID) EntityValues.getId(e), Function.identity()));
    }

    /**
     * SUM/COUNT result as a long. SUM over the SpentTime column skips the converter and returns a plain number
     * whose type depends on the database (BigDecimal on HSQLDB).
     */
    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
