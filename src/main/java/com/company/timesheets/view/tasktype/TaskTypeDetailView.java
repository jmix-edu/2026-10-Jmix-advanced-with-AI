package com.company.timesheets.view.tasktype;

import com.company.timesheets.entity.TaskType;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;


@Route(value = "task-types/:id", layout = MainView.class)
@ViewController(id = "ts_TaskType.detail")
@ViewDescriptor(path = "task-type-detail-view.xml")
@EditedEntityContainer("taskTypeDc")
public class TaskTypeDetailView extends StandardDetailView<TaskType> {
}