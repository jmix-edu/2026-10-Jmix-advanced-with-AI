package com.company.timesheets.view.mytimeentries;


import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.service.TimeEntrySupport;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "my-time-entries", layout = MainView.class)
@ViewController(id = "TimeEntries.my")
@ViewDescriptor(path = "my-time-entries.xml")
public class MyTimeEntries extends StandardView {

    @ViewComponent
    private DataGrid<TimeEntry> timeEntriesDataGrid;
    @Autowired
    private TimeEntrySupport timeEntrySupport;
    @Autowired
    private DialogWindows dialogWindows;

    @Subscribe("timeEntriesDataGrid.copy")
    public void onTimeEntriesDataGridCopy(final ActionPerformedEvent event) {
        TimeEntry selectedToCopy = timeEntriesDataGrid.getSingleSelectedItem();
        if (selectedToCopy == null) {
            return;
        }

        TimeEntry copied = timeEntrySupport.copy(selectedToCopy);

        dialogWindows.detail(timeEntriesDataGrid)
                .newEntity(copied)
                .open();
    }
}
