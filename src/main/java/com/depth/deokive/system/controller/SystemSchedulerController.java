package com.depth.deokive.system.controller;

import com.depth.deokive.domain.archive.scheduler.ArchiveBadgeScheduler;
import com.depth.deokive.domain.archive.scheduler.ArchiveHotFeedScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/system/test/scheduler")
@RequiredArgsConstructor
@Profile("dev") // ⚠️ 로컬 환경에서만 빈 등록 (운영 배포 시 404)
@Tag(name = "[TEST] Scheduler Trigger", description = "스케줄러 강제 실행 API")
public class SystemSchedulerController {

    private final ArchiveHotFeedScheduler hotFeedScheduler;
    private final ArchiveBadgeScheduler badgeScheduler;

    @PostMapping("/hot-score")
    @Operation(summary = "🔥 핫 스코어 갱신 강제 실행", description = "100만 건 기준 약 1~3초 소요 예상")
    public ResponseEntity<String> triggerHotScore() {
        log.info("Manual Trigger: Hot Score Update");
        long start = System.currentTimeMillis();

        hotFeedScheduler.updateHotScores();

        long end = System.currentTimeMillis();
        return ResponseEntity.ok("Hot Score Updated! (Time: " + (end - start) + "ms)");
    }

    @PostMapping("/badge")
    @Operation(summary = "🏅 뱃지 승급 강제 실행", description = "생성일 기준으로 뱃지 등급 재산정")
    public ResponseEntity<String> triggerBadge() {
        log.info("Manual Trigger: Badge Update");
        long start = System.currentTimeMillis();

        badgeScheduler.updateArchiveBadges();

        long end = System.currentTimeMillis();
        return ResponseEntity.ok("Badge Update Completed! (Time: " + (end - start) + "ms)");
    }
}