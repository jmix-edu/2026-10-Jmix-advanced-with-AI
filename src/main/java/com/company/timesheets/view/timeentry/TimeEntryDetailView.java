package com.company.timesheets.view.timeentry;

import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "time-entries/:id", layout = MainView.class)
@ViewController(id = "ts_TimeEntry.detail")
@ViewDescriptor(path = "time-entry-detail-view.xml")
@EditedEntityContainer("timeEntryDc")
public class TimeEntryDetailView extends StandardDetailView<TimeEntry> {

    @Autowired
    private CurrentAuthentication currentAuthentication;
    @ViewComponent
    private JmixTextArea rejectionReasonField;
    @ViewComponent
    private EntityComboBox<Task> tasksComboBox;
    @ViewComponent
    private EntityPicker<User> userField;
    @ViewComponent
    private CollectionLoader<Task> tasksDl;

    @Subscribe("userField.assignSelf")
    public void onUserFieldAssignSelf(final ActionPerformedEvent event) {
        final User user = (User) currentAuthentication.getUser();
        getEditedEntity().setUser(user);
    }

    @Subscribe(id = "timeEntryDc", target = Target.DATA_CONTAINER)
    public void onTimeEntryDcItemChange(final InstanceContainer.ItemChangeEvent<TimeEntry> event) {
        updateRejectionReasonField();
        loadTasks();
    }

    @Subscribe(id = "timeEntryDc", target = Target.DATA_CONTAINER)
    public void onTimeEntryDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<TimeEntry> event) {
        if ("status".equals(event.getProperty())) {
            updateRejectionReasonField();
        }
        if ("user".equals(event.getProperty())) {
            tasksComboBox.setReadOnly(getEditedEntity().getUser() == null);
            tasksComboBox.getDataProvider().refreshAll();
            loadTasks();
        }
        if ("task".equals(event.getProperty())) {
            userField.setReadOnly(getEditedEntity().getTask() != null);
        }
    }

    private void loadTasks() {
        User user = getEditedEntity().getUser();
        tasksDl.setParameter("username", user != null ? user.getUsername() : null);
        tasksDl.load();
    }

    private void updateRejectionReasonField() {
        rejectionReasonField.setVisible(TimeEntryStatus.REJECTED.equals(getEditedEntity().getStatus()));
    }

    @Subscribe
    public void onInitEntity(final InitEntityEvent<TimeEntry> event) {
        TimeEntry timeEntry = event.getEntity();
        if (timeEntry.getUser() == null) {
            final User user = (User) currentAuthentication.getUser();
            timeEntry.setUser(user);
            tasksComboBox.setReadOnly(true);
        } else  {
           tasksComboBox.setReadOnly(timeEntry.getTask() != null);
        }
    }
    
    


}