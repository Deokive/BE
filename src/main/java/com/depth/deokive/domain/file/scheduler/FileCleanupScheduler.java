package com.depth.deokive.domain.file.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final JobLauncher jobLauncher;
    private final Job fileCleanupJob;

    @Scheduled(cron = "${scheduler.file-cleanup-cron}")
    public void runCleanupJob() {
        try {
            log.info("🕒 [Scheduler] Starting Orphaned File Cleanup Job...");

            // JobParameters에 시간을 넣어주어야 매번 새로운 JobInstance로 실행됨
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(fileCleanupJob, jobParameters);

        } catch (Exception e) {
            log.error("🔴 [Scheduler] Failed to run file cleanup job", e);
        }
    }
}