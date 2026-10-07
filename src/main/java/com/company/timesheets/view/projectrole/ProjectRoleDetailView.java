package com.company.timesheets.view.projectrole;

import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;


@Route(value = "project-roles/:id", layout = MainView.class)
@ViewController(id = "ts_ProjectRole.detail")
@ViewDescriptor(path = "project-role-detail-view.xml")
@EditedEntityContainer("projectRoleDc")
public class ProjectRoleDetailView extends StandardDetailView<ProjectRole> {
}