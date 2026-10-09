package com.company.timesheets.service;

import io.jmix.pessimisticlock.LockDescriptorProvider;
import io.jmix.pessimisticlock.entity.LockDescriptor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Registers the lock that serializes month closing of one project. Without a descriptor
 * {@code LockManager} does not lock the name at all.
 * <p>
 * The name is not an entity name on purpose: a descriptor named after {@code ts_Project} would also lock
 * the project detail view for everyone but the first editor.
 */
@Component
public class MonthClosingLockDescriptorProvider implements LockDescriptorProvider {

    /**
     * Released in {@code finally} as soon as closing ends; the timeout only covers a hung thread.
     */
    private static final int TIMEOUT_SEC = 600;

    @Override
    public Collection<LockDescriptor> getLockDescriptors() {
        return List.of(new LockDescriptor(MonthClosingService.LOCK_NAME, TIMEOUT_SEC));
    }
}
