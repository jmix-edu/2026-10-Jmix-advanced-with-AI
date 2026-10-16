package com.company.timesheets.view.task;

import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.User;
import com.company.timesheets.view.main.MainView;
import com.company.timesheets.view.timeentry.TimeEntryDetailView;
import com.vaadin.flow.router.Route;
import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.usersubstitution.CurrentUserSubstitution;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.accesscontext.UiEntityContext;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "my-tasks", layout = MainView.class)
@ViewController(id = "Tasks.my")
@ViewDescriptor(path = "my-tasks-list-view.xml")
@LookupComponent("tasksDataGrid")
@DialogMode(width = "64em")
public class MyTasksListView extends StandardListView<Task> {
    @ViewComponent
    private DataGrid<Task> tasksDataGrid;
    @Autowired
    private CurrentUserSubstitution currentUserSubstitution;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private Metadata metadata;
    @Autowired
    private AccessManager accessManager;

    // Списание на выбранную задачу: пользователь и задача заполнены заранее,
    // форма открывается диалогом, флаг своего списания передаём сеттером до open().
    @Subscribe("tasksDataGrid.createTimeEntryAction")
    public void onTasksDataGridCreateTimeEntryAction(final ActionPerformedEvent event) {
        Task selectedTask = tasksDataGrid.getSingleSelectedItem();
        User user = (User) currentUserSubstitution.getEffectiveUser();
         if (selectedTask == null || user == null) {
             return;
         }

        TimeEntry newEntry = dataManager.create(TimeEntry.class);
         newEntry.setUser(user);
         newEntry.setTask(selectedTask);

         DialogWindow<TimeEntryDetailView> dialogWindow = dialogWindows.detail(this, TimeEntry.class)
                 .withViewClass(TimeEntryDetailView.class)
                 .newEntity(newEntry)
                 .build();
         dialogWindow.getView().setOwnTimeEntry(true);
         dialogWindow.open();
    }

    // Кнопка доступна, только если у пользователя есть право создавать списания.
    @Install(to = "tasksDataGrid.createTimeEntryAction", subject = "enabledRule")
    private boolean tasksDataGridCreateTimeEntryActionEnabledRule() {
        UiEntityContext entityContext = new UiEntityContext(metadata.getClass(TimeEntry.class));
        accessManager.applyRegisteredConstraints(entityContext);
        return entityContext.isCreatePermitted();
    }

}