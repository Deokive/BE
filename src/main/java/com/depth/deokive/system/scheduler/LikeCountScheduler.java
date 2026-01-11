package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountScheduler {

    private final PostRepository postRepository;
    private final ArchiveRepository archiveRepository;

    // 10초마다 실행
    @Scheduled(fixedRateString = "${scheduler.like-interval:10000}")
    public void syncLikeCounts() {
        // Post Sync
        int updatedPostCount = postRepository.syncLikeCountsStatFromCountTable();

        // Archive Sync
        int updatedArchiveCount = archiveRepository.syncLikeCountsStatFromCountTable();

        if (updatedPostCount > 0 || updatedArchiveCount > 0) {
            log.info("[Like Sync] Post: {}, Archive: {}", updatedPostCount, updatedArchiveCount);
        }
    }
}