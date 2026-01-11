package com.depth.deokive.system.controller;

import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.scheduler.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.batch.core.Job;

@Slf4j
@RestController
@RequestMapping("/api/system/test/scheduler")
@RequiredArgsConstructor
@Profile({"dev", "test"}) // ⚠️ 로컬 환경에서만 빈 등록 (운영 배포 시 404)
@Tag(name = "[TEST] Scheduler Trigger", description = "스케줄러 강제 실행 API")
public class SystemSchedulerController {

    private final ArchiveHotFeedScheduler hotFeedScheduler;
    private final ArchiveBadgeScheduler badgeScheduler;
    private final PostHotScoreScheduler postHotScoreScheduler;
    private final ViewCountScheduler viewCountScheduler;
    private final LikeCountScheduler likeCountScheduler;


    private final JobLauncher jobLauncher;
    private final Job fileCleanupJob; // Bean 이름(FileCleanupBatchConfig의 메서드명)과 일치해야 자동 주입됨

    @ExecutionTime
    @PostMapping("/hot-score")
    @Operation(summary = "🔥 핫 스코어 갱신 강제 실행", description = "100만 건 기준 약 1~3초 소요 예상")
    public ResponseEntity<String> triggerHotScore() {
        long start = System.currentTimeMillis();

        hotFeedScheduler.updateHotScores();
        postHotScoreScheduler.updatePostHotScores();

        return ResponseEntity.ok("🟢 Hot Score Update Completed! (Archive & Post)");
    }

    @ExecutionTime
    @PostMapping("/badge")
    @Operation(summary = "🏅 뱃지 승급 강제 실행", description = "생성일 기준으로 뱃지 등급 재산정")
    public ResponseEntity<String> triggerBadge() {
        log.info("Manual Trigger: Badge Update");
        long start = System.currentTimeMillis();

        badgeScheduler.updateArchiveBadges();

        long end = System.currentTimeMillis();
        return ResponseEntity.ok("Badge Update Completed! (Time: " + (end - start) + "ms)");
    }

    @ExecutionTime
    @PostMapping("/batch/file-cleanup")
    @Operation(summary = "🧹 고아 파일 정리 배치 강제 실행", description = "S3 및 DB에서 연결되지 않은(24시간 경과) 파일 삭제")
    public ResponseEntity<String> triggerFileCleanupBatch() {
        log.info("Manual Trigger: File Cleanup Batch");
        long start = System.currentTimeMillis();

        try {
            // Spring Batch는 동일한 파라미터로 이미 성공한 Job을 재실행하지 않음.
            // 따라서 매번 실행할 때마다 현재 시간을 파라미터로 넣어 '새로운 작업'임을 알려야 함.
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("type", "manual_trigger") // 구분용 태그
                    .toJobParameters();

            jobLauncher.run(fileCleanupJob, jobParameters);

        } catch (Exception e) {
            log.error("🔴 Batch execution failed", e);
            return ResponseEntity.internalServerError().body("Batch Failed: " + e.getMessage());
        }

        long end = System.currentTimeMillis();
        return ResponseEntity.ok("File Cleanup Batch Completed! (Time: " + (end - start) + "ms)");
    }

    @ExecutionTime
    @PostMapping("/view-count")
    @Operation(summary = "👁️ 조회수 동기화 강제 실행 (Redis -> DB)", description = "Redis에 캐싱된 조회수를 DB에 일괄 반영하고 Redis에서 차감합니다.")
    public ResponseEntity<String> triggerViewCountSync() {
        log.info("Manual Trigger: View Count Sync");

        viewCountScheduler.syncAllViewCounts();

        return ResponseEntity.ok("🟢 View Count Sync Completed! (Redis -> DB)");
    }

    @ExecutionTime
    @PostMapping("/like-count")
    @Operation(summary = "❤️ 좋아요 동기화 (LikeCount -> PostStats)", description = "좋아요: 실시간 테이블 값을 검색용 통계 테이블로 동기화")
    public ResponseEntity<String> triggerLikeCountSync() {
        log.info("Manual Trigger: Like Count Sync");
        likeCountScheduler.syncLikeCounts();
        return ResponseEntity.ok("🟢 Like Count Sync Completed! (PostLikeCount -> PostStats)");
    }
}