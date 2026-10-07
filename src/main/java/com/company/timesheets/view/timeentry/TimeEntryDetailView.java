package com.company.timesheets.view.timeentry;

import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;


@Route(value = "time-entries/:id", layout = MainView.class)
@ViewController(id = "ts_TimeEntry.detail")
@ViewDescriptor(path = "time-entry-detail-view.xml")
@EditedEntityContainer("timeEntryDc")
public class TimeEntryDetailView extends StandardDetailView<TimeEntry> {
}