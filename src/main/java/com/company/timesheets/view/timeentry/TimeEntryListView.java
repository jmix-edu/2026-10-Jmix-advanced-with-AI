package com.company.timesheets.view.timeentry;

import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "time-entries", layout = MainView.class)
@ViewController(id = "ts_TimeEntry.list")
@ViewDescriptor(path = "time-entry-list-view.xml")
@LookupComponent("timeEntriesDataGrid")
@DialogMode(width = "64em")
public class TimeEntryListView extends StandardListView<TimeEntry> {

}