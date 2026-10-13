package com.company.timesheets.view.task;

import com.company.timesheets.entity.Task;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "tasks/:id", layout = MainView.class)
@ViewController(id = "ts_Task.detail")
@ViewDescriptor(path = "task-detail-view.xml")
@EditedEntityContainer("taskDc")
@DialogMode(width = "35em")
public class TaskDetailView extends StandardDetailView<Task> {
}