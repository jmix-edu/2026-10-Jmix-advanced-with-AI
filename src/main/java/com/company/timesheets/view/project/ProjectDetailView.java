package com.company.timesheets.view.project;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.Task;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.action.BaseAction;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "projects/:id", layout = MainView.class)
@ViewController(id = "ts_Project.detail")
@ViewDescriptor(path = "project-detail-view.xml")
@EditedEntityContainer("projectDc")
public class ProjectDetailView extends StandardDetailView<Project> {

    private DataGrid<ProjectParticipant> participantsDataGrid;
    private DataGrid<Task> tasksDataGrid;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private DialogWindows dialogWindows;
    @ViewComponent
    private CollectionLoader<ProjectParticipant> participantsDl;
    @ViewComponent
    private CollectionLoader<Task> tasksDl;
    @Autowired
    private Notifications notifications;
    @ViewComponent
    private CollectionContainer<ProjectParticipant> participantsDc;
    @ViewComponent
    private CollectionContainer<Task> tasksDc;

    @Subscribe("tabSheet")
    public void onTabSheetSelectedChange(final JmixTabSheet.SelectedChangeEvent event) {
        String tabId = event.getSelectedTab().getId().orElse("");
        if (tabId.equals("participantsTab")) {
            initParticipants();
        }
        if (tabId.equals("tasksTab")) {
            initTasks();
        }
    }

    private void initParticipants() {
        participantsDl.setParameter("project", getEditedEntity());
        participantsDl.load();

        if (participantsDataGrid  != null) {
            return;
        }
        participantsDataGrid = (DataGrid<ProjectParticipant>) getContent().findComponent("participantsDataGrid").orElseThrow();
       ((BaseAction<?>) participantsDataGrid.getAction("createAction"))
                .addActionPerformedListener(this::onParticipantsDataGridCreateAction);
        ((BaseAction<?>) participantsDataGrid.getAction("editAction"))
                .addActionPerformedListener(this::onParticipantsDataGridEditAction);
    }

    private void initTasks() {
        tasksDl.setParameter("project", getEditedEntity());
        tasksDl.load();

        if (tasksDataGrid  != null) {
            return;
        }
        tasksDataGrid = (DataGrid<Task>) getContent().findComponent("tasksDataGrid").orElseThrow();
        ((BaseAction<?>) tasksDataGrid.getAction("createAction"))
                .addActionPerformedListener(this::onTasksDataGridCreateAction);
        ((BaseAction<?>) tasksDataGrid.getAction("editAction"))
                .addActionPerformedListener(this::onTasksDataGridEditAction);
    }

    public void onParticipantsDataGridCreateAction(final ActionPerformedEvent event) {
        ProjectParticipant newParticipant = dataManager.create(ProjectParticipant.class);
        newParticipant.setProject(getEditedEntity());
        dialogWindows.detail(participantsDataGrid)
                .newEntity(newParticipant)
                .withParentDataContext(getViewData().getDataContext())
                .open();
    }
    public void onParticipantsDataGridEditAction(final ActionPerformedEvent event) {
        ProjectParticipant participantToEdit = participantsDataGrid.getSingleSelectedItem();
        if (participantToEdit == null) {
            return;
        }
        dialogWindows.detail(participantsDataGrid)
                .editEntity(participantToEdit)
                .withParentDataContext(getViewData().getDataContext())
                .open();
    }

    public void onTasksDataGridCreateAction(final ActionPerformedEvent event) {
        Task newTask = dataManager.create(Task.class);
        newTask.setProject(getEditedEntity());
        dialogWindows.detail(tasksDataGrid)
                .newEntity(newTask)
                .withParentDataContext(getViewData().getDataContext())
                .open();
    }

    public void onTasksDataGridEditAction(final ActionPerformedEvent event) {
        Task taskToEdit = tasksDataGrid.getSingleSelectedItem();
        if (taskToEdit == null) {
            return;
        }
        dialogWindows.detail(tasksDataGrid)
                .editEntity(taskToEdit)
                .withParentDataContext(getViewData().getDataContext())
                .open();
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        notifications.show("Participants loaded: " + participantsDc.getItems().size());
        notifications.show("Tasks loaded: " + tasksDc.getItems().size());
    }





}