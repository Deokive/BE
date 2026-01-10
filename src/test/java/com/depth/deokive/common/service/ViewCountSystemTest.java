package com.depth.deokive.common.service;

import com.depth.deokive.common.enums.ViewDomain;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.depth.deokive.system.scheduler.ViewCountScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("조회수 시스템 통합 테스트")
class ViewCountSystemTest extends IntegrationTestSupport {

    @Autowired private RedisViewService redisViewService;
    @Autowired private ViewCountScheduler viewCountScheduler;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private PostRepository postRepository;
    @Autowired private ArchiveRepository archiveRepository;

    private Post post;
    private User user;
    private Archive archive;

    @BeforeEach
    void setUp() {
        // 1. Redis 초기화
        Set<String> keys = redisTemplate.keys("view:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 2. 테스트 유저 생성 (NPE 방지)
        user = userRepository.save(User.builder()
                .email("viewtest@test.com")
                .username("view_tester_" + UUID.randomUUID())
                .nickname("ViewTester")
                .password("password")
                .role(Role.USER)
                .userType(UserType.COMMON)
                .isEmailVerified(true)
                .build());

        // 3. 테스트용 게시글 생성
        setupMockUser(user);
        post = postRepository.save(Post.builder()
                .title("Test Post")
                .content("Content")
                .category(Category.IDOL)
                .user(user)
                .viewCount(0L) // 초기값 0
                .build());

        // 4. 테스트용 아카이브 생성
        archive = archiveRepository.save(
                com.depth.deokive.domain.archive.entity.Archive.builder()
                        .title("Test Archive")
                        .user(user)
                        .visibility(com.depth.deokive.common.enums.Visibility.PUBLIC)
                        .viewCount(0L)
                        .build()
        );

        flushAndClear();
    }

    @Test
    @DisplayName("SCENE 1. [동시성] 100명의 유저가 동시에 조회 -> Redis에 100이 정확히 쌓여야 한다.")
    void concurrency_increment() throws InterruptedException {
        // Given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1000L;
            executorService.submit(() -> {
                try {
                    redisViewService.incrementViewCount(ViewDomain.POST, post.getId(), userId, "127.0.0.1");
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // Then
        // 1. DB는 0이어야 함 (스케줄러 동작 전)
        Post dbPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(dbPost.getViewCount()).isEqualTo(0L);

        // 2. Redis는 100이어야 함
        String countKey = "view:count:post:" + post.getId();
        String value = redisTemplate.opsForValue().get(countKey);
        assertThat(value).isNotNull();
        assertThat(Long.parseLong(value)).isEqualTo(100L);
    }

    @Test
    @DisplayName("SCENE 2. [어뷰징 방지] 동일 유저가 여러 번 조회해도 조회수는 1만 증가해야 한다.")
    void abuse_prevention() {
        // Given
        Long userId = 1234L;
        String ip = "127.0.0.1";

        // When
        for (int i = 0; i < 10; i++) {
            redisViewService.incrementViewCount(ViewDomain.POST, post.getId(), userId, ip);
        }

        // Then
        String countKey = "view:count:post:" + post.getId();
        String value = redisTemplate.opsForValue().get(countKey);

        assertThat(value).isNotNull();
        assertThat(Long.parseLong(value)).isEqualTo(1L);
    }

    @Test
    @DisplayName("SCENE 3. [동기화] 스케줄러 메서드를 직접 호출하면 Redis 데이터가 DB로 이관된다.")
    void scheduler_sync_success() {
        // Given: Redis에 50 세팅
        String countKey = "view:count:post:" + post.getId();
        redisTemplate.opsForValue().set(countKey, "50");

        // When: 스케줄러 메서드 직접 호출
        viewCountScheduler.syncAllViewCounts();

        // Then
        // 1. DB 반영 확인
        Post updatedPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(updatedPost.getViewCount()).isEqualTo(50L);

        // 2. Redis 삭제 확인 (0 이하가 되면 삭제됨)
        Boolean hasKey = redisTemplate.hasKey(countKey);
        assertThat(hasKey).isFalse();
    }

    @Test
    @DisplayName("SCENE 4. [대용량 배치] RedisViewService는 설정된 Limit만큼만 데이터를 가져온다.")
    void batch_size_limit() {
        // Given: 150개의 서로 다른 게시글 키 생성
        for (int i = 1; i <= 150; i++) {
            redisTemplate.opsForValue().set("view:count:post:" + i, "1");
        }

        // When: limit 100으로 조회 (RedisViewService 메서드 직접 테스트)
        Map<Long, Long> result = redisViewService.getAndFlushViewCounts(ViewDomain.POST, 100);

        // Then: 정확히 100개만 가져와야 함
        assertThat(result).hasSize(100);
    }

    @Test
    @DisplayName("SCENE 5. [정합성/Race Condition] 스케줄러 실행 중 대량의 추가 조회가 발생해도 총합(DB+Redis)은 일치해야 한다.")
    void race_condition_during_sync() throws InterruptedException {
        // Given
        String countKey = "view:count:post:" + post.getId();
        long initialCount = 5000L; // 스케줄러가 처리할 물량 (충분히 많게 설정)
        long additionalCount = 2000L; // 스케줄러 실행 중 추가될 물량

        redisTemplate.opsForValue().set(countKey, String.valueOf(initialCount));

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        // When
        // Thread 1: 스케줄러 실행 (5000건 업데이트 시도 -> DB I/O 발생으로 시간 소요됨)
        executorService.submit(() -> {
            try {
                viewCountScheduler.syncAllViewCounts();
            } finally {
                latch.countDown();
            }
        });

        // Thread 2: 스케줄러가 도는 동안 추가 조회수 2000건 폭격
        executorService.submit(() -> {
            try {
                // 스케줄러가 Redis 값을 읽어갈 틈을 살짝 줌
                Thread.sleep(1);
                for (int i = 0; i < additionalCount; i++) {
                    // ID를 매번 바꿔서 어뷰징 체크를 회피하며 카운트 증가
                    redisViewService.incrementViewCount(ViewDomain.POST, post.getId(), 20000L + i, "1.1.1.1");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        latch.await();

        // Then
        // DB 값 조회
        Post dbPost = postRepository.findById(post.getId()).orElseThrow();
        long dbViewCount = dbPost.getViewCount();

        // Redis 값 조회 (남아있는 값)
        String redisValStr = redisTemplate.opsForValue().get(countKey);
        long redisViewCount = (redisValStr != null) ? Long.parseLong(redisValStr) : 0;

        System.out.println("### Race Condition Result (No Mockito) ###");
        System.out.println("DB ViewCount: " + dbViewCount);
        System.out.println("Redis ViewCount: " + redisViewCount);
        System.out.println("Total Expected: " + (initialCount + additionalCount));
        System.out.println("Total Actual: " + (dbViewCount + redisViewCount));

        // 검증: (DB에 반영된 값) + (Redis에 남은 값) == (초기값) + (추가된 값)
        // 유실이 발생했다면 Total Actual이 더 작게 나옴
        assertThat(dbViewCount + redisViewCount).isEqualTo(initialCount + additionalCount);
    }

    @Test
    @DisplayName("SCENE 6. [CoolDown] 쿨타임(TTL)이 지나면 다시 조회수가 증가해야 한다.")
    void abuse_cooldown_simulation() {
        // Given
        Long userId = 777L;
        String ip = "127.0.0.1";

        // 1. 최초 조회 (+1)
        redisViewService.incrementViewCount(ViewDomain.POST, post.getId(), userId, ip);

        // 2. 쿨타임 내 재조회 (무시됨)
        redisViewService.incrementViewCount(ViewDomain.POST, post.getId(), userId, ip);

        String countKey = "view:count:post:" + post.getId();
        assertThat(redisTemplate.opsForValue().get(countKey)).isEqualTo("1");

        // When: 쿨타임 만료 시뮬레이션 (Log Key 강제 삭제)
        String logKey = "view:log:post:" + post.getId() + ":user:" + userId;
        redisTemplate.delete(logKey);

        // 3. 만료 후 재조회 (+1)
        redisViewService.incrementViewCount(ViewDomain.POST, post.getId(), userId, ip);

        // Then
        assertThat(redisTemplate.opsForValue().get(countKey)).isEqualTo("2");
    }

    @Test
    @DisplayName("SCENE 7. [비회원] 비회원은 IP를 기준으로 조회수를 카운팅한다.")
    void anonymous_user_view_count() {
        // Given
        String ipA = "1.1.1.1";
        String ipB = "2.2.2.2";

        // When
        redisViewService.incrementViewCount(ViewDomain.POST, post.getId(), null, ipA); // +1
        redisViewService.incrementViewCount(ViewDomain.POST, post.getId(), null, ipA); // 중복 무시
        redisViewService.incrementViewCount(ViewDomain.POST, post.getId(), null, ipB); // +1

        // Then
        String countKey = "view:count:post:" + post.getId();
        assertThat(redisTemplate.opsForValue().get(countKey)).isEqualTo("2");
    }

    @Test
    @DisplayName("SCENE 8. [Error Safety] 처리 중 예외가 발생하면(데이터 오염 등) Redis 데이터는 차감되지 않고 유지된다.")
    void error_handling_safety() {
        // Given: Redis에 숫자가 아닌 "오염된 데이터" 주입 -> Long.parseLong() 예외 유발
        String countKey = "view:count:post:" + post.getId();
        redisTemplate.opsForValue().set(countKey, "INVALID_NUMBER");

        // When: 스케줄러 실행 (내부 try-catch로 예외 처리)
        viewCountScheduler.syncAllViewCounts();

        // Then: Redis 데이터가 삭제되지 않고 유지되어야 함 (유실 방지)
        String preservedValue = redisTemplate.opsForValue().get(countKey);
        assertThat(preservedValue).isEqualTo("INVALID_NUMBER");

        // DB는 변동 없음
        Post dbPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(dbPost.getViewCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("SCENE 9. [배치 격리성] 배치 처리 중 특정 Key에서 에러가 나도, 다른 정상 Key들은 문제없이 DB에 반영되어야 한다.")
    void batch_isolation_test() {
        // Given
        // Post A: 정상 (100)
        Post postA = postRepository.save(Post.builder().title("A").content("C").category(Category.IDOL).user(user).viewCount(0L).build());
        String keyA = "view:count:post:" + postA.getId();
        redisTemplate.opsForValue().set(keyA, "100");

        // Post B: 에러 유발 (Not a Number)
        Post postB = postRepository.save(Post.builder().title("B").content("C").category(Category.IDOL).user(user).viewCount(0L).build());
        String keyB = "view:count:post:" + postB.getId();
        redisTemplate.opsForValue().set(keyB, "ERROR");

        // Post C: 정상 (200)
        Post postC = postRepository.save(Post.builder().title("C").content("C").category(Category.IDOL).user(user).viewCount(0L).build());
        String keyC = "view:count:post:" + postC.getId();
        redisTemplate.opsForValue().set(keyC, "200");

        // When
        viewCountScheduler.syncAllViewCounts();

        // Then
        // A, C는 정상 반영 & Redis 삭제
        assertThat(postRepository.findById(postA.getId()).get().getViewCount()).isEqualTo(100L);
        assertThat(redisTemplate.hasKey(keyA)).isFalse();

        assertThat(postRepository.findById(postC.getId()).get().getViewCount()).isEqualTo(200L);
        assertThat(redisTemplate.hasKey(keyC)).isFalse();

        // B는 DB 반영 안 됨 & Redis 보존
        assertThat(postRepository.findById(postB.getId()).get().getViewCount()).isEqualTo(0L);
        assertThat(redisTemplate.opsForValue().get(keyB)).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("SCENE 10. [로직 개선 검증] Redis 값이 0 이하인 Zombie Key는 DB 업데이트 없이 삭제되어야 한다.")
    void cleanup_zombie_key() {
        // Given: 값이 0인 키 (DB 업데이트 불필요, 하지만 Redis에는 남아있음)
        String countKey = "view:count:post:" + post.getId();
        redisTemplate.opsForValue().set(countKey, "0");

        // When
        viewCountScheduler.syncAllViewCounts();

        // Then
        // 1. DB는 여전히 0이어야 함 (불필요한 쿼리 방지)
        Post dbPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(dbPost.getViewCount()).isEqualTo(0L);

        // 2. [검증] Redis Key는 삭제되어야 함 (좀비 키 정리 확인)
        Boolean hasKey = redisTemplate.hasKey(countKey);
        assertThat(hasKey).isFalse();
    }

    @Test
    @DisplayName("SCENE 11. [Archive-동시성] 100명의 유저가 아카이브 동시 조회 -> Redis에 100이 정확히 쌓여야 한다.")
    void archive_concurrency_increment() throws InterruptedException {
        // Given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 2000L; // Post 테스트와 ID 구분
            executorService.submit(() -> {
                try {
                    redisViewService.incrementViewCount(ViewDomain.ARCHIVE, archive.getId(), userId, "127.0.0.1");
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // Then
        // 1. DB는 0
        com.depth.deokive.domain.archive.entity.Archive dbArchive = archiveRepository.findById(archive.getId()).orElseThrow();
        assertThat(dbArchive.getViewCount()).isEqualTo(0L);

        // 2. Redis는 100
        String countKey = "view:count:archive:" + archive.getId();
        String value = redisTemplate.opsForValue().get(countKey);
        assertThat(value).isNotNull();
        assertThat(Long.parseLong(value)).isEqualTo(100L);
    }

    @Test
    @DisplayName("SCENE 12. [Archive-어뷰징 방지] 동일 유저가 아카이브를 여러 번 조회해도 조회수는 1만 증가해야 한다.")
    void archive_abuse_prevention() {
        // Given
        Long userId = 5678L;
        String ip = "192.168.0.1";

        // When
        for (int i = 0; i < 10; i++) {
            redisViewService.incrementViewCount(ViewDomain.ARCHIVE, archive.getId(), userId, ip);
        }

        // Then
        String countKey = "view:count:archive:" + archive.getId();
        String value = redisTemplate.opsForValue().get(countKey);

        assertThat(value).isNotNull();
        assertThat(Long.parseLong(value)).isEqualTo(1L);
    }

    @Test
    @DisplayName("SCENE 13. [Archive-동기화] 스케줄러 실행 시 Redis 아카이브 조회수가 DB로 이관된다.")
    void archive_scheduler_sync_success() {
        // Given: Redis에 77 세팅
        String countKey = "view:count:archive:" + archive.getId();
        redisTemplate.opsForValue().set(countKey, "77");

        // When
        viewCountScheduler.syncAllViewCounts(); // 내부적으로 syncArchiveViews() 호출됨

        // Then
        com.depth.deokive.domain.archive.entity.Archive updatedArchive = archiveRepository.findById(archive.getId()).orElseThrow();
        assertThat(updatedArchive.getViewCount()).isEqualTo(77L);

        Boolean hasKey = redisTemplate.hasKey(countKey);
        assertThat(hasKey).isFalse();
    }
}