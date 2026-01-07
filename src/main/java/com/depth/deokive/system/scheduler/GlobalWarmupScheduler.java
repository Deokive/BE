package com.depth.deokive.system.scheduler;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.domain.archive.repository.ArchiveQueryRepository;
import com.depth.deokive.domain.post.repository.PostQueryRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalWarmupScheduler {

    private final PostQueryRepository postQueryRepository;
    private final ArchiveQueryRepository archiveQueryRepository;

    private static final int WARMUP_PAGE_SIZE = 10;

    @ExecutionTime
    @EventListener(ApplicationReadyEvent.class) // 서버 배포 직후 최초 1회 실행 (첫 사용자 렉 방지)
    @Scheduled(fixedRate = 600000) // 10분
    @Transactional(readOnly = true)
    public void warmupMainContents() {
        log.info("🔥 [Warm-up] Start: Main Feed & Hot Contents");

        try {
            // 1. Post 도메인 웜업
            // 1-1. 전체 게시글 최신순 (메인 피드 기본)
            postQueryRepository.searchPostFeed(
                    null, // category = ALL
                    PageRequest.of(0, WARMUP_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))
            );

            // 1-2. Hot Post (인기 게시글) -> 메인 노출용
            postQueryRepository.searchPostFeed(
                    null,
                    PageRequest.of(0, WARMUP_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "hotScore"))
            );


            // 2. Archive 도메인 웜업
            // 2-1. 최신 아카이브 피드 (LATEST) -> PUBLIC만 조회
            archiveQueryRepository.searchArchiveFeed(
                    null, // filterUserId = null (전체)
                    List.of(Visibility.PUBLIC),
                    PageRequest.of(0, WARMUP_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))
            );

            // 2-2. 핫 아카이브 피드 (HOT) -> PUBLIC만 조회
            archiveQueryRepository.searchArchiveFeed(
                    null,
                    List.of(Visibility.PUBLIC),
                    PageRequest.of(0, WARMUP_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "hotScore"))
            );
        } catch (Exception e) {
            log.warn("⚠️ [Warm-up] Failed: {}", e.getMessage()); // 웜업 실패가 서비스 전체 장애로 이어지면 안 되므로 로그만 남김
        }
    }
}