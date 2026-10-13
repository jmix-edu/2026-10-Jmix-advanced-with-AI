package com.company.timesheets.view.projectparticipant;

import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "project-participants/:id", layout = MainView.class)
@ViewController(id = "ts_ProjectParticipant.detail")
@ViewDescriptor(path = "project-participant-detail-view.xml")
@EditedEntityContainer("projectParticipantDc")
@DialogMode(width = "35em")
public class ProjectParticipantDetailView extends StandardDetailView<ProjectParticipant> {

    @Autowired
    private DialogWindows dialogWindows;
    @ViewComponent
    private EntityPicker<ProjectRole> projectRoleField;

    @Subscribe("projectRoleField.entityCreateAction")
    public void onProjectRoleFieldEntityCreateAction(final ActionPerformedEvent event) {
        dialogWindows.detail(projectRoleField)
                .newEntity()
                .open();
    }
}