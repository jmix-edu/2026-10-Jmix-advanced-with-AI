package com.company.timesheets.view.timeentry;

import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.MetadataTools;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "time-entries", layout = MainView.class)
@ViewController(id = "ts_TimeEntry.list")
@ViewDescriptor(path = "time-entry-list-view.xml")
@LookupComponent("timeEntriesDataGrid")
@DialogMode(width = "64em")
public class TimeEntryListView extends StandardListView<TimeEntry> {
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private MetadataTools metadataTools;

    @Supply(to = "timeEntriesDataGrid.status", subject = "renderer")
    private Renderer<TimeEntry> timeEntriesDataGridStatusRenderer() {
        return new ComponentRenderer<>(Span::new, (span, timeEntry) -> {
            TimeEntryStatusBadge.apply(span, timeEntry.getStatus(), metadataTools);
        });
    }

    @Install(to = "timeEntriesDataGrid", subject = "partNameGenerator")
    private String timeEntriesDataGridPartNameGenerator(final TimeEntry timeEntry) {
        return TimeEntryStatusBadge.rowPartName(timeEntry);
    }

}