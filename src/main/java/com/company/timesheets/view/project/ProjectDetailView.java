package com.company.timesheets.view.project;

import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.Task;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.jmix.core.DataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.action.BaseAction;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;


@Route(value = "projects/:id", layout = MainView.class)
@ViewController(id = "ts_Project.detail")
@ViewDescriptor(path = "project-detail-view.xml")
@EditedEntityContainer("projectDc")
public class ProjectDetailView extends StandardDetailView<Project> {

    // Гриды ленивых вкладок: null, пока вкладку не открывали.
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
    private UiComponents uiComponents;

    // Содержимое вкладок формы загружается, только когда вкладку открывают.
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

    // Ленивая загрузка вкладки «Participants». Загрузчик вызываем при каждом выборе вкладки,
    // до проверки грида: при переключении данные перечитываются из базы и всегда актуальны.
    // Участник, добавленный и ещё не сохранённый, после перечитывания из грида пропадает,
    // но остаётся в DataContext формы и сохранится по «Save».
    // Грид и обработчики действий настраиваем один раз, при первом выборе вкладки.
    // Грид ищем через findComponent: вкладка ленивая, при создании формы грида ещё нет,
    // поэтому @ViewComponent и @Subscribe на его действия здесь не работают.
    private void initParticipants() {
        participantsDl.setParameter("project", getEditedEntity());
        participantsDl.load();

        if (participantsDataGrid != null) {
            return;
        }
        participantsDataGrid = (DataGrid<ProjectParticipant>) getContent().findComponent("participantsDataGrid").orElseThrow();
       ((BaseAction<?>) participantsDataGrid.getAction("createAction"))
                .addActionPerformedListener(this::onParticipantsDataGridCreateAction);
        ((BaseAction<?>) participantsDataGrid.getAction("editAction"))
                .addActionPerformedListener(this::onParticipantsDataGridEditAction);
    }

    // Вкладка «Tasks» устроена так же, как «Participants».
    private void initTasks() {
        tasksDl.setParameter("project", getEditedEntity());
        tasksDl.load();

        if (tasksDataGrid != null) {
            return;
        }
        tasksDataGrid = (DataGrid<Task>) getContent().findComponent("tasksDataGrid").orElseThrow();
        ((BaseAction<?>) tasksDataGrid.getAction("createAction"))
                .addActionPerformedListener(this::onTasksDataGridCreateAction);
        ((BaseAction<?>) tasksDataGrid.getAction("editAction"))
                .addActionPerformedListener(this::onTasksDataGridEditAction);
    }

    // Участник и задача правятся в диалоге во вложенном DataContext формы проекта
    // (withParentDataContext): в базу изменения уходят вместе с проектом, по «Save» или «OK».
    // Новой записи сразу проставляем текущий проект.
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


    //todo check how to hide empty space at the position 0
    @Supply(to = "clientsComboBox", subject = "renderer")
    private Renderer<Client> clientsComboBoxRenderer() {
        return new ComponentRenderer<>(client -> {
            FlexLayout wrapper = uiComponents.create(FlexLayout.class);
            wrapper.setAlignItems(FlexComponent.Alignment.CENTER);
            wrapper.getStyle().set("gap", "var(--vaadin-gap-xl)");

            String clientName = client.getName();

            wrapper.add(
                    createAvatar(clientName, client.getImage(), "1em"),
                    new Text(clientName)
            );

            return wrapper;
        }); 
    }

    private Avatar createAvatar(String name, @Nullable byte[] data, String size) {

        Avatar avatar = uiComponents.create(Avatar.class);
        avatar.setName(name);

        if (data != null) {
            DownloadHandler handler = DownloadHandler.fromInputStream(downloadEvent ->
                    new DownloadResponse(
                            new ByteArrayInputStream(data),
                            "avatar.img",
                            "application/octet-stream",
                            data.length
                    ));
            avatar.setImageHandler(handler);
        }

        avatar.setWidth(size);
        avatar.setHeight(size);

        return avatar;

    }
}
