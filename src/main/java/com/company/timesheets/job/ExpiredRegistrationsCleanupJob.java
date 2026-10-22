package com.company.timesheets.job;

import com.company.timesheets.service.RegistrationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Removes registrations not activated in time: specs/03_access_control/04-registration-cleanup.spec.adoc.
 * Only a trigger: the work and its authentication are in {@link RegistrationService#removeExpiredRegistrations()}.
 * Quartz creates the job through Spring, so the service is injected.
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
