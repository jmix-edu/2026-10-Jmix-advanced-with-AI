package com.company.timesheets.view.invitation;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.ProjectRoleType;
import com.company.timesheets.service.ContractorInvitationService;
import com.company.timesheets.service.ContractorInvitationService.InvitationResult;
import com.company.timesheets.service.InvitationException;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Span;
import io.jmix.core.TimeSource;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.textfield.JmixEmailField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

/**
 * Dialog where a project manager invites a contractor by email: specs/03_access_control/05-contractor-invitation.
 * Opened from the participants tab of the project form; the caller passes the project with {@link #setProject}.
 */
@ViewController(id = "ts_InviteContractorView")
@ViewDescriptor(path = "invite-contractor-view.xml")
@DialogMode(width = "36em")
public class InviteContractorView extends StandardView {

    @ViewComponent
    private Span projectText;
    @ViewComponent
    private TypedTextField<String> firstNameField;
    @ViewComponent
    private TypedTextField<String> lastNameField;
    @ViewComponent
    private JmixEmailField emailField;
    @ViewComponent
    private EntityComboBox<ProjectRole> projectRoleField;
    @ViewComponent
    private TypedDatePicker<LocalDate> contractEndDateField;
    @ViewComponent
    private CollectionContainer<ProjectRole> projectRolesDc;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private ContractorInvitationService invitationService;
    @Autowired
    private ViewValidation viewValidation;
    @Autowired
    private Notifications notifications;
    @Autowired
    private TimeSource timeSource;

    private Project project;

    public void setProject(Project project) {
        this.project = project;
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        projectText.setText(messageBundle.formatMessage("project.text", project.getName()));
        // The contract must run at least until tomorrow; the end day itself is still a working day.
        contractEndDateField.setMin(timeSource.now().toLocalDate().plusDays(1));
    }

    @Subscribe(id = "projectRolesDc", target = Target.DATA_CONTAINER)
    public void onProjectRolesDcCollectionChange(final CollectionContainer.CollectionChangeEvent<ProjectRole> event) {
        if (projectRoleField.getValue() == null) {
            projectRolesDc.getItems().stream()
                    .filter(role -> role.getType() == ProjectRoleType.MEMBER)
                    .findFirst()
                    .ifPresent(projectRoleField::setValue);
        }
    }

    @Subscribe("inviteButton")
    public void onInviteButtonClick(final ClickEvent<JmixButton> event) {
        ValidationErrors errors = viewValidation.validateUiComponents(getContent());
        if (!errors.isEmpty()) {
            viewValidation.showValidationErrors(errors);
            viewValidation.focusProblemComponent(errors);
            return;
        }
        InvitationResult result;
        try {
            result = invitationService.invite(project, firstNameField.getTypedValue(), lastNameField.getTypedValue(),
                    emailField.getValue(), projectRoleField.getValue(), contractEndDateField.getTypedValue());
        } catch (InvitationException | AccessDeniedException e) {
            notifications.create(e.getMessage()).withType(Notifications.Type.WARNING).show();
            return;
        }
        String key = switch (result) {
            case LINK_SENT -> "invited";
            case ADDED -> "addedExisting";
            case MAIL_FAILED -> "mailFailed";
        };
        notifications.create(messageBundle.formatMessage(key, emailField.getValue()))
                .withType(result == InvitationResult.MAIL_FAILED ? Notifications.Type.WARNING
                        : Notifications.Type.SUCCESS)
                .show();
        close(StandardOutcome.SAVE);
    }

    @Subscribe("cancelButton")
    public void onCancelButtonClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }
}
