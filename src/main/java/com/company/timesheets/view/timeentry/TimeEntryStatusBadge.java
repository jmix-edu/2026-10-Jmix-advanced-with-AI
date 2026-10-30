package com.company.timesheets.view.timeentry;

import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.vaadin.flow.component.html.Span;
import io.jmix.core.MetadataTools;
import org.jspecify.annotations.Nullable;

/**
 * Как выглядит статус списания в гридах и в форме: одно место для бейджа и отклонённой строки.
 * Цвета — классы темы проекта (timesheets.css, view/time-entry-status.css).
 */
public final class TimeEntryStatusBadge {

    /**
     * Имя part строки грида с отклонённым списанием, стилизуется как {@code vaadin-grid::part(rejected-row)}.
     */
    public static final String REJECTED_ROW = "rejected-row";

    private static final String STATUS_APPROVED = "status-approved";
    private static final String STATUS_REJECTED = "status-rejected";

    private TimeEntryStatusBadge() {
    }

    /**
     * Превращает span в бейдж статуса. У бейджа Jmix под Aura нет вариантов success/error,
     * поэтому цвет задают классы status-*.
     */
    public static void apply(Span badge, @Nullable TimeEntryStatus status, MetadataTools metadataTools) {
        badge.getElement().getThemeList().clear();
        badge.removeClassNames(STATUS_APPROVED, STATUS_REJECTED);
        badge.setVisible(status != null);
        if (status == null) {
            return;
        }
        badge.getElement().getThemeList().add("badge");
        switch (status) {
            case CLOSED -> badge.getElement().getThemeList().add("contrast");
            case APPROVED -> badge.addClassName(STATUS_APPROVED);
            case REJECTED -> badge.addClassName(STATUS_REJECTED);
            case NEW -> {
            }
        }
        badge.setText(metadataTools.format(status));
    }

    /**
     * Для partNameGenerator грида: помечает отклонённые строки, остальные оставляет как есть.
     */
    @Nullable
    public static String rowPartName(TimeEntry entry) {
        return entry.getStatus() == TimeEntryStatus.REJECTED ? REJECTED_ROW : null;
    }
}
