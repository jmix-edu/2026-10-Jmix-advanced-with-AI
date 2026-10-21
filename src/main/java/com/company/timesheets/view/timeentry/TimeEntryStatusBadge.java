package com.company.timesheets.view.timeentry;

import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.vaadin.flow.component.html.Span;
import io.jmix.core.MetadataTools;
import org.jspecify.annotations.Nullable;

/**
 * How a time entry status looks in grids and in the form: one place for the badge and the rejected row.
 * The colors are classes of the project theme (timesheets.css, view/time-entry-status.css).
 */
public final class TimeEntryStatusBadge {

    /**
     * Part name of a grid row with a rejected entry, styled as {@code vaadin-grid::part(rejected-row)}.
     */
    public static final String REJECTED_ROW = "rejected-row";

    private static final String STATUS_APPROVED = "status-approved";
    private static final String STATUS_REJECTED = "status-rejected";

    private TimeEntryStatusBadge() {
    }

    /**
     * Makes the span a status badge. The Jmix badge has no success/error variants under Aura,
     * so the color comes from the status-* classes.
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
     * For a grid partNameGenerator: marks rejected rows, leaves the others as they are.
     */
    @Nullable
    public static String rowPartName(TimeEntry entry) {
        return entry.getStatus() == TimeEntryStatus.REJECTED ? REJECTED_ROW : null;
    }
}
