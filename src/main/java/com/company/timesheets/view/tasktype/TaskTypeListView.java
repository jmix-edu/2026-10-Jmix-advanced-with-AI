package com.company.timesheets.view.tasktype;

import com.company.timesheets.entity.TaskType;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "task-types", layout = MainView.class)
@ViewController(id = "ts_TaskType.list")
@ViewDescriptor(path = "task-type-list-view.xml")
@LookupComponent("taskTypesDataGrid")
@DialogMode(width = "64em")
public class TaskTypeListView extends StandardListView<TaskType> {

}