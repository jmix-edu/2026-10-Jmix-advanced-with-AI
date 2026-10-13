package com.company.timesheets.service;

import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import io.jmix.core.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("ts_TimeEntrySupport")
public class TimeEntrySupport {
    private final MetadataTools metadataTools;
    private final DataManager dataManager;

    public TimeEntrySupport(MetadataTools metadataTools, DataManager dataManager) {
        this.metadataTools = metadataTools;
        this.dataManager = dataManager;
    }

    public TimeEntry copy(TimeEntry source){
        TimeEntry reloaded = dataManager.load(Id.of(source))
                .fetchPlan(planBuilder ->
                        planBuilder.addFetchPlan(FetchPlan.BASE)
                                .add("user", FetchPlan.INSTANCE_NAME)
                                .add("task", FetchPlan.INSTANCE_NAME))
                .one();

        TimeEntry copied = metadataTools.copy(reloaded);
        copied.setId(UuidProvider.createUuid());
        copied.setVersion(null);
        copied.setDate(LocalDate.now());
        copied.setStatus(TimeEntryStatus.NEW);
        copied.setRejectionReason(null);
        copied.setBillingRecordId(null);
        copied.setCreatedBy(null);
        copied.setCreatedDate(null);
        copied.setLastModifiedBy(null);
        copied.setLastModifiedDate(null);
        return copied;

    }
}
