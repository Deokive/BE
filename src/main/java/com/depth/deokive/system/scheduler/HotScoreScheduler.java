package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotScoreScheduler {

    private final PostRepository postRepository;
    private final ArchiveRepository archiveRepository;

    private static final double W1_LIKE = 20.0;    // 좋아요 가중치 (신뢰도 표현 장치)
    private static final double W2_VIEW = 3.0;     // 조회수 가중치 (로그스케일 보정)
    private static final double LAMBDA = 0.004;    // 시간 감쇠 계수 (반감기 약 7일)

    @Scheduled(cron = "${scheduler.post-hot-score-cron}")
    @ExecutionTime
    @Transactional
    public void updatePostHotScores() {
        log.info("🔥 [Scheduler] Starting Post Hot Score Update...");

        // 1. 최근 7일 게시글 일반 점수 업데이트
        int standardRows = postRepository.updateHotScoreStandard(W1_LIKE, W2_VIEW, LAMBDA);

        // 2. 7일이 갓 지난(168h ~ 169h) 게시글 패널티(0.5배) 적용 및 박제
        int penalizedRows = postRepository.applyHotScorePenalty(W1_LIKE, W2_VIEW, LAMBDA);

        log.info("✅ [Scheduler] Post Hot Score Update Completed. (Standard: {}, Penalized: {})", standardRows, penalizedRows);
    }

    @ExecutionTime
    @Scheduled(cron = "${scheduler.archive-hot-score-cron}")
    @Transactional
    public void updateArchiveHotScores() {
        log.info("🔥 [Scheduler] Starting Archive Hot Score Update...");

        // 1. 최근 7일 아카이브 일반 점수 업데이트
        int standardRows = archiveRepository.updateHotScoreStandard(W1_LIKE, W2_VIEW, LAMBDA);

        // 2. 7일이 갓 지난 아카이브 패널티 적용
        int penalizedRows = archiveRepository.applyHotScorePenalty(W1_LIKE, W2_VIEW, LAMBDA);

        log.info("✅ [Scheduler] Archive Hot Score Update Completed. (Standard: {}, Penalized: {})", standardRows, penalizedRows);
    }
}