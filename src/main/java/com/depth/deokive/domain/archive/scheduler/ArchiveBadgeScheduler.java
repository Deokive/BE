package com.depth.deokive.domain.archive.scheduler;

import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveBadgeScheduler {

    private final ArchiveRepository archiveRepository;

    @Scheduled(cron = "${scheduler.badge-cron}")
    @Transactional
    public void updateArchiveBadges() {
        log.info("🏅 [Scheduler] Starting Archive Badge Update...");

        LocalDateTime now = LocalDateTime.now();
        int totalUpdated = 0;

        Badge[] badges = Badge.values();

        for (int i = badges.length - 1; i > 0; i--) {
            Badge targetBadge = badges[i];
            int requiredDays = targetBadge.getRequiredDays();

            LocalDateTime cutOffDate = now.minusDays(requiredDays);

            List<Badge> lowerBadges = Arrays.stream(badges)
                    .filter(b -> b.ordinal() < targetBadge.ordinal())
                    .collect(Collectors.toList());

            int count = archiveRepository.updateBadgesBulk(targetBadge, cutOffDate, lowerBadges);

            if (count > 0) {
                log.info("   👉 Promoted to {}: {} archives", targetBadge, count);
                totalUpdated += count;
            }
        }

        log.info("✅ [Scheduler] Badge Update Completed. (Total Promoted: {})", totalUpdated);
    }
}