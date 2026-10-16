package com.company.timesheets.service;

import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.User;
import io.jmix.core.DataManager;
import io.jmix.core.querycondition.JpqlCondition;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.querycondition.PropertyCondition;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Stream;

@Component("ts_TaskSupport")
public class TaskSupport {

    private final DataManager dataManager;

    public TaskSupport(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public Stream<Task> getUserActiveTasks(User user, int firstResult, int maxResult, @Nullable String filter) {
        return load(firstResult, maxResult, user, filter);
    }

    public Stream<Task> getActiveTasks(int firstResult, int maxResult, @Nullable String filter) {
        return load(firstResult, maxResult, null, filter);
    }

    private Stream<Task> load(int firstResult, int maxResult, @Nullable User user, @Nullable String filter) {
        PropertyCondition nameCondition = PropertyCondition.contains("name", filter);
        nameCondition.setSkipNullOrEmpty(true);

        JpqlCondition participantCondition = JpqlCondition.createWithParameters(
                "exists (select pp from ts_ProjectParticipant pp where pp.project = {E}.project and pp.user = :user)",
                null, Collections.singletonMap("user", user));
        participantCondition.setSkipNullOrEmpty(true);

        return dataManager.load(Task.class)
                .query("select e from ts_Task e " +
                        "where e.project.status = @enum(com.company.timesheets.entity.ProjectStatus.OPEN) " +
                        "and e.status = @enum(com.company.timesheets.entity.TaskStatus.ACTIVE) " +
                        "order by e.project.name asc, e.name asc")
                .condition(LogicalCondition.and(nameCondition, participantCondition))
                .fetchPlan("_instance_name")
                .firstResult(firstResult)
                .maxResults(maxResult)
                .list()
                .stream();
    }
}
