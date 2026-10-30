package com.company.timesheets.job;

import com.company.timesheets.service.RegistrationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Удаляет регистрации, не активированные вовремя: specs/03_access_control/04-registration-cleanup.spec.adoc.
 * Только запускает: работа и её аутентификация — в {@link RegistrationService#removeExpiredRegistrations()}.
 * Quartz создаёт задание через Spring, поэтому сервис внедряется.
 */
@DisallowConcurrentExecution
public class ExpiredRegistrationsCleanupJob implements Job {

    @Autowired
    private RegistrationService registrationService;

    @Override
    public void execute(JobExecutionContext context) {
        registrationService.removeExpiredRegistrations();
    }
}
