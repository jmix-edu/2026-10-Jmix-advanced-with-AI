package com.company.timesheets.view.task;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.User;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.jspecify.annotations.Nullable;

@ViewController(id = "ts_Task.lookup")
@ViewDescriptor(path = "task-lookup-view.xml")
@LookupComponent("tasksDataGrid")
@DialogMode(width = "64em")
public class TaskLookupView extends StandardListView<Task> {

    @ViewComponent
    private CollectionLoader<Task> tasksDl;
    @ViewComponent
    private CollectionLoader<Project> projectsDl;

    public void setUser(@Nullable User user) {
        if (user != null) {
            tasksDl.setParameter("user", user);
            projectsDl.setParameter("user", user);
        } else {
            tasksDl.removeParameter("user");
            projectsDl.removeParameter("user");
        }
    }
}
