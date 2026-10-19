package com.company.timesheets.view.mytimeentries;


import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.service.TimeEntrySupport;
import com.company.timesheets.view.main.MainView;
import com.company.timesheets.view.timeentry.TimeEntryDetailView;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import io.jmix.core.MetadataTools;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
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
    @Autowired
    private MetadataTools metadataTools;

    // Копия открывается диалогом: флаг своего списания передаём сеттером до open().
    @Subscribe("timeEntriesDataGrid.copy")
    public void onTimeEntriesDataGridCopy(final ActionPerformedEvent event) {
        TimeEntry selectedToCopy = timeEntriesDataGrid.getSingleSelectedItem();
        if (selectedToCopy == null) {
            return;
        }

        TimeEntry copied = timeEntrySupport.copy(selectedToCopy);

        DialogWindow<TimeEntryDetailView> dialogWindow = dialogWindows.detail(timeEntriesDataGrid)
                .withViewClass(TimeEntryDetailView.class)
                .newEntity(copied)
                .build();
        dialogWindow.getView().setOwnTimeEntry(true);
        dialogWindow.open();
    }

    // «Create» и «Edit» открывают форму навигацией, поэтому флаг передаём в адресе:
    // /time-entries/new?ownTimeEntry. Форма читает его в QueryParametersChangeEvent.
    @Install(to = "timeEntriesDataGrid.create", subject = "queryParametersProvider")
    private QueryParameters timeEntriesDataGridCreateQueryParametersProvider() {
        return QueryParameters.of(TimeEntryDetailView.PARAMETER_OWN_TIME_ENTRY, "");
    }

    @Install(to = "timeEntriesDataGrid.edit", subject = "queryParametersProvider")
    private QueryParameters timeEntriesDataGridEditQueryParametersProvider() {
        return QueryParameters.of(TimeEntryDetailView.PARAMETER_OWN_TIME_ENTRY, "");
    }

    @Supply(to = "timeEntriesDataGrid.status", subject = "renderer")
    private Renderer<TimeEntry> timeEntriesDataGridStatusRenderer() {
        return new ComponentRenderer<>(Span::new, (span, timeEntry) -> {
            // у бейджа Джеймикс под Aura нет success/error: цвет задают классы status-* (timesheets.css)
            span.getElement().setAttribute("theme", timeEntry.getStatus() == TimeEntryStatus.CLOSED
                    ? "badge contrast" : "badge");
            span.setClassName(switch (timeEntry.getStatus()) {
                case NEW, CLOSED -> "";
                case APPROVED -> "status-approved";
                case REJECTED -> "status-rejected";
            });
            span.setText(metadataTools.format(timeEntry.getStatus()));
        });
    }
}
