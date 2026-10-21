package com.company.timesheets.view.timeentry;

import com.company.timesheets.service.ProjectAccessSupport;
import com.company.timesheets.service.TaskSupport;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import com.company.timesheets.view.main.MainView;
import com.company.timesheets.view.task.TaskLookupView;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import io.jmix.core.MetadataTools;
import io.jmix.core.usersubstitution.CurrentUserSubstitution;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;


@Route(value = "time-entries/:id", layout = MainView.class)
@ViewController(id = "ts_TimeEntry.detail")
@ViewDescriptor(path = "time-entry-detail-view.xml")
@EditedEntityContainer("timeEntryDc")
@DialogMode(width = "40em")
public class TimeEntryDetailView extends StandardDetailView<TimeEntry> {

    // Параметр адреса для открытия навигацией: /time-entries/new?ownTimeEntry
    public static final String PARAMETER_OWN_TIME_ENTRY = "ownTimeEntry";

    @ViewComponent
    private EntityComboBox<User> userField;
    @ViewComponent
    private EntityComboBox<Task> taskField;
    @ViewComponent
    private JmixTextArea rejectionReasonField;
    @ViewComponent
    private Span statusBadge;
    @ViewComponent
    private JmixSelect<TimeEntryStatus> statusField;
    @Autowired
    private ProjectAccessSupport projectAccessSupport;
    @Autowired
    private MetadataTools metadataTools;
    @Autowired
    private CurrentUserSubstitution currentUserSubstitution;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private TaskSupport taskSupport;

    // Своё списание: пользователь всегда текущий и не меняется. Флаг ставят до показа формы:
    // сеттером, когда форму открывают диалогом, или параметром адреса при навигации.
    private boolean ownTimeEntry = false;

    // Вызывают до открытия диалога: dialogWindow.getView().setOwnTimeEntry(true)
    public void setOwnTimeEntry(boolean ownTimeEntry) {
        this.ownTimeEntry = ownTimeEntry;
    }

    // При навигации флаг приходит из адреса, событие наступает раньше InitEntityEvent.
    @Subscribe
    public void onQueryParametersChange(final QueryParametersChangeEvent event) {
        ownTimeEntry = event.getQueryParameters()
                .getSingleParameter(PARAMETER_OWN_TIME_ENTRY)
                .isPresent();
    }

    // Только для новой записи. Своё списание: подставляем текущего пользователя
    // (поле userField в XML уже только для чтения). Общее: сначала выбирают пользователя,
    // задача недоступна до выбора. Копия или запись из «Моих задач» приходят с пользователем:
    // задачу не даём менять, если она уже задана.
    @Subscribe
    public void onInitEntity(final InitEntityEvent<TimeEntry> event) {
        TimeEntry timeEntry = event.getEntity();
        if (timeEntry.getUser() == null) {
            if (ownTimeEntry) {
                timeEntry.setUser((User) currentUserSubstitution.getEffectiveUser());
            } else {
                userField.setReadOnly(false);
                taskField.setReadOnly(true);
            }
        } else {
            taskField.setReadOnly(timeEntry.getTask() != null);
        }
    }

    @Subscribe("userField.assignSelf")
    public void onUserFieldAssignSelf(final ActionPerformedEvent event) {
        getEditedEntity().setUser((User) currentUserSubstitution.getEffectiveUser());
    }

    // Свой экран выбора задачи: передаём ему пользователя, чтобы показать только его задачи.
    // Параметр ставим сеттером между build() и open().
    @Subscribe("taskField.entityLookupAction")
    public void onTaskFieldEntityLookupAction(final ActionPerformedEvent event) {
        DialogWindow<TaskLookupView> dialogWindow = dialogWindows.lookup(taskField)
                .withViewClass(TaskLookupView.class)
                .build();
        dialogWindow.getView().setUser(getEditedEntity().getUser());
        dialogWindow.open();
    }

    @Subscribe(id = "timeEntryDc", target = Target.DATA_CONTAINER)
    public void onTimeEntryDcItemChange(final InstanceContainer.ItemChangeEvent<TimeEntry> event) {
        updateRejectionReasonField();
        updateStatusBadge();
        updateApprovalFields();
    }

    @Subscribe(id = "timeEntryDc", target = Target.DATA_CONTAINER)
    public void onTimeEntryDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<TimeEntry> event) {
        if ("status".equals(event.getProperty())) {
            updateRejectionReasonField();
            updateStatusBadge();
        }
        // Пользователь выбран: задачу можно выбирать, список задач перечитываем под него.
        // Задача выбрана в общем списке: пользователя больше не меняем.
        if ("user".equals(event.getProperty())) {
            taskField.setReadOnly(getEditedEntity().getUser() == null);
            taskField.getDataProvider().refreshAll();
        }
        if ("task".equals(event.getProperty()) && !ownTimeEntry) {
            userField.setReadOnly(getEditedEntity().getTask() != null);
        }
        if ("task".equals(event.getProperty()) || "user".equals(event.getProperty())) {
            updateApprovalFields();
        }
    }

    // Согласует и отклоняет руководитель или approver проекта, и не своё списание (спека 03/01).
    // Роли это не выражают: решает участие в проекте, его проверяет ProjectAccessSupport.
    private void updateApprovalFields() {
        boolean denied = !projectAccessSupport.canApprove(getEditedEntity());
        statusField.setReadOnly(denied || isReadOnly());
        rejectionReasonField.setReadOnly(denied || isReadOnly());
    }

    private void updateRejectionReasonField() {
        rejectionReasonField.setVisible(TimeEntryStatus.REJECTED == getEditedEntity().getStatus());
    }

    // Бейдж статуса того же вида, что в гридах списаний.
    private void updateStatusBadge() {
        TimeEntryStatusBadge.apply(statusBadge, getEditedEntity().getStatus(), metadataTools);
    }

    // Задачи в выпадающем списке грузятся порциями с фильтром по вводу:
    // активные задачи пользователя, а пока пользователь не выбран, все активные.
    @Install(to = "taskField", subject = "itemsFetchCallback")
    private Stream<Task> taskFieldItemsFetchCallback(final Query<Task, String> query) {
        User user = getEditedEntity().getUser();
        String filter = query.getFilter().orElse(null);
        return user != null
                ? taskSupport.getUserActiveTasks(user, query.getOffset(), query.getLimit(), filter)
                : taskSupport.getActiveTasks(query.getOffset(), query.getLimit(), filter);
    }
}
