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
 * Задание очистки и его расписание. Jmix Quartz хранит задания в базе и при старте перезаписывает их из этих бинов
 * (spring.quartz.overwriteExistingJobs=true в его module.properties), поэтому дубликатов нет, а новый
 * cron начинает действовать после перезапуска.
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
