package com.company.timesheets.job;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The cleanup job and its schedule. Jmix Quartz stores jobs in the database and overwrites them from these beans
 * on start (spring.quartz.overwriteExistingJobs=true in its module.properties), so there is no duplicate and a new
 * cron applies after a restart.
 */
@Configuration
public class RegistrationCleanupJobConfiguration {

    static final String GROUP = "timesheets";
    static final String JOB_NAME = "expiredRegistrationsCleanup";

    @Bean
    public JobDetail expiredRegistrationsCleanupJob() {
        return JobBuilder.newJob(ExpiredRegistrationsCleanupJob.class)
                .withIdentity(JOB_NAME, GROUP)
                .withDescription("Removes accounts registered but not activated before their link expired")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger expiredRegistrationsCleanupTrigger(JobDetail expiredRegistrationsCleanupJob,
                                                      @Value("${timesheets.registration.cleanup-cron:0 0 * * * ?}")
                                                      String cron) {
        return TriggerBuilder.newTrigger()
                .forJob(expiredRegistrationsCleanupJob)
                .withIdentity(JOB_NAME + "Trigger", GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .build();
    }
}
