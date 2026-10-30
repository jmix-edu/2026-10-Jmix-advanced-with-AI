package com.company.timesheets.service;

import io.jmix.pessimisticlock.LockDescriptorProvider;
import io.jmix.pessimisticlock.entity.LockDescriptor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Регистрирует блокировку, которая выстраивает закрытия месяца одного проекта в очередь. Без дескриптора
 * {@code LockManager} вообще не блокирует это имя.
 * <p>
 * Имя намеренно не совпадает с именем сущности: дескриптор с именем {@code ts_Project} заблокировал бы и
 * экран редактирования проекта для всех, кроме первого редактора.
 */
@Component
public class MonthClosingLockDescriptorProvider implements LockDescriptorProvider {

    /**
     * Снимается в {@code finally} сразу после закрытия; таймаут нужен только на случай зависшего потока.
     */
    private static final int TIMEOUT_SEC = 600;

    @Override
    public Collection<LockDescriptor> getLockDescriptors() {
        return List.of(new LockDescriptor(MonthClosingService.LOCK_NAME, TIMEOUT_SEC));
    }
}
