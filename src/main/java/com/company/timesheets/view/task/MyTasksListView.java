package com.company.timesheets.view.task;

import com.company.timesheets.entity.Task;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "my-tasks", layout = MainView.class)
@ViewController(id = "Tasks.my")
@ViewDescriptor(path = "my-tasks-list-view.xml")
@LookupComponent("tasksDataGrid")
@DialogMode(width = "64em")
public class MyTasksListView extends StandardListView<Task> {

}