package com.company.timesheets.event;

import org.springframework.context.ApplicationEvent;

public class TimeEntryStatusChangedEvent extends ApplicationEvent {
    public TimeEntryStatusChangedEvent(Object source) {
        super(source);
    }
}
