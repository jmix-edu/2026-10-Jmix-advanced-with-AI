package com.company.timesheets.view.user;

import com.company.timesheets.entity.User;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.app.inputdialog.InputParameter;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.Set;

@Route(value = "users", layout = MainView.class)
@ViewController(id = "ts_User.list")
@ViewDescriptor(path = "user-list-view.xml")
@LookupComponent("usersDataGrid")
@DialogMode(width = "64em")
public class UserListView extends StandardListView<User> {

    @Autowired
    private Dialogs dialogs;
    @Autowired
    private UiComponents uiComponents;
    @ViewComponent
    private DataGrid<User> usersDataGrid;
    @ViewComponent
    private CollectionContainer<User> usersDc;
    @Autowired
    private Notifications notifications;

    @Subscribe("usersDataGrid.sendEmailAction")
    public void onUsersDataGridSendEmailAction(final ActionPerformedEvent event) {
        dialogs.createInputDialog(this)
                .withHeader("Send email(s)")
                .withLabelsPosition(Dialogs.InputDialogBuilder.LabelsPosition.TOP)
                .withParameters(
                        InputParameter.stringParameter("title")
                                .withLabel("Title")
                                .withRequired(true),
                        InputParameter.stringParameter("body")
                                .withLabel("Body")
                                .withField(() -> {
                                    JmixTextArea textArea = uiComponents.create(JmixTextArea.class);
                                    textArea.setRequired(true);
                                    textArea.setWidthFull();
                                    textArea.setHeight("9.5em");
                                    return textArea;
                                })
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        String title = closeEvent.getValue("title");
                        String body = closeEvent.getValue("body");

                        Set<User> selected = usersDataGrid.getSelectedItems();
                        Collection<User> users = selected.isEmpty() ? usersDc.getItems() : selected;

                        doSendEmail(title, body, users);
                    }
                })
                .open();
    }

    private void doSendEmail(String title, String body, Collection<User> users) {
        notifications.create(title, body).show();

    }
}