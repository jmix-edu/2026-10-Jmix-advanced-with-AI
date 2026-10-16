package com.company.timesheets.view.task;

import com.company.timesheets.entity.Task;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "tasks-simple", layout = MainView.class)
@ViewController(id = "ts_Task.list-simple")
@ViewDescriptor(path = "task-list-simple-view.xml")
@LookupComponent("tasksDataGrid")
@DialogMode(width = "64em")
public class TaskListSimpleView extends StandardListView<Task> {

}