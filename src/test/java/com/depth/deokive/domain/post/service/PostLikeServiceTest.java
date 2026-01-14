package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.enums.ViewLikeDomain; // [New] 도메인 Enum 추가
import com.depth.deokive.common.service.LikeRedisService;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostStats;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostLikeRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.post.repository.PostStatsRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.depth.deokive.system.scheduler.LikeCountScheduler;
import com.depth.deokive.system.security.model.UserPrincipal;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("PostLikeService E2E 통합 테스트 (Spring Boot 3.4+ Standard)")
class PostLikeServiceTest extends IntegrationTestSupport {

    @Autowired PostService postService;
    @Autowired LikeRedisService likeRedisService;
    @Autowired LikeCountScheduler likeCountScheduler;
    @Autowired TransactionTemplate transactionTemplate;

    @Autowired PostRepository postRepository;
    @Autowired PostStatsRepository postStatsRepository;
    @Autowired RedisTemplate<String, Object> redisTemplate;

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired RabbitListenerEndpointRegistry registry;

    @MockitoSpyBean
    PostLikeRepository postLikeRepository;

    private User postOwner;
    private Post targetPost;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();

        postOwner = createUser("owner@test.com", "owner");
        targetPost = createPost(postOwner, "Target Post");

        flushAndClear();
    }

    @AfterEach
    void tearDown() {
        registry.stop();

        RabbitAdmin rabbitAdmin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());

        rabbitAdmin.purgeQueue(ViewLikeDomain.POST.getQueueName(), false);
        rabbitAdmin.purgeQueue(ViewLikeDomain.ARCHIVE.getQueueName(), false);

        // 2. 그 다음 DB를 초기화
        TransactionTemplate requireNewTemplate = new TransactionTemplate(transactionManager);
        requireNewTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        requireNewTemplate.execute(status -> {
            postLikeRepository.deleteAllInBatch();
            postStatsRepository.deleteAllInBatch();
            postRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
            return null;
        });

        // 3. Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();

        registry.start();
    }

    @Nested
    @DisplayName("I. 캐시 전략 및 비동기 정합성")
    class CacheStrategyTest {

        @Test
        @DisplayName("SCENE 1 & 2. [Cache Logic] Miss면 DB 조회 1회, Hit면 DB 조회 0회")
        void cacheLogic_And_RedisUpdate() {
            User liker = createUser("liker@test.com", "liker");
            UserPrincipal principal = UserPrincipal.from(liker);

            // When: 첫 좋아요 (Warming 발생)
            postService.toggleLike(principal, targetPost.getId());

            // Then 1: Warming 검증 (findAllUserIdsByPostId 호출 확인)
            verify(postLikeRepository, times(1)).findAllUserIdsByPostId(eq(targetPost.getId()));

            // [수정] isLiked 호출 시 Domain과 Loader 전달
            boolean isLiked = likeRedisService.isLiked(
                    ViewLikeDomain.POST,
                    targetPost.getId(),
                    liker.getId(),
                    () -> postLikeRepository.findAllUserIdsByPostId(targetPost.getId())
            );
            assertThat(isLiked).isTrue();


            // 2. [Cache Hit] 상황
            // When: 좋아요 취소
            postService.toggleLike(principal, targetPost.getId());

            // Then 2: DB 조회 횟수 유지 (Warming 스킵)
            verify(postLikeRepository, times(1)).findAllUserIdsByPostId(eq(targetPost.getId()));

            // isLiked 호출 시 Domain과 Loader 전달
            boolean isLikedAfter = likeRedisService.isLiked(
                    ViewLikeDomain.POST,
                    targetPost.getId(),
                    liker.getId(),
                    () -> postLikeRepository.findAllUserIdsByPostId(targetPost.getId())
            );
            assertThat(isLikedAfter).isFalse();
        }

        @Test
        @DisplayName("SCENE 6. [Async Persistence] Redis 변경사항이 RabbitMQ를 거쳐 DB PostLike 테이블에 반영된다.")
        void async_Persistence_Check() {
            User liker = createUser("async@test.com", "asyncUser");

            // When
            postService.toggleLike(UserPrincipal.from(liker), targetPost.getId());

            // Then (Redis)
            // getCount 호출 시 Domain과 Loader 전달
            Long redisCount = likeRedisService.getCount(
                    ViewLikeDomain.POST,
                    targetPost.getId(),
                    () -> postLikeRepository.findAllUserIdsByPostId(targetPost.getId())
            );
            assertThat(redisCount).isEqualTo(1L);

            // Then (DB - Async)
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                boolean exists = postLikeRepository.existsByPostIdAndUserId(targetPost.getId(), liker.getId());
                assertThat(exists).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("II. 동시성 및 부하 테스트 (t3.small 시뮬레이션)")
    class ConcurrencyTest {

        @Test
        @DisplayName("SCENE 4. [Thundering Herd] 동시 접속 시 DB Warming 쿼리 폭주 방지 (Redisson Lock)")
        void prevent_Thundering_Herd() throws InterruptedException {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
            int threadCount = 50;
            ExecutorService executorService = Executors.newFixedThreadPool(32);
            CountDownLatch latch = new CountDownLatch(threadCount);

            List<User> users = IntStream.range(0, threadCount)
                    .mapToObj(i -> createUser("th" + i + "@t.com", "nick" + i))
                    .toList();

            flushAndClear();

            // When
            AtomicInteger successCount = new AtomicInteger();
            for (User user : users) {
                executorService.submit(() -> {
                    try {
                        postService.toggleLike(UserPrincipal.from(user), targetPost.getId());
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();

            // Then
            assertThat(successCount.get()).isEqualTo(threadCount);
            verify(postLikeRepository, org.mockito.Mockito.atMost(5)).findAllUserIdsByPostId(eq(targetPost.getId()));

            // getCount 호출 시 Domain과 Loader 전달
            Long finalCount = likeRedisService.getCount(
                    ViewLikeDomain.POST,
                    targetPost.getId(),
                    () -> postLikeRepository.findAllUserIdsByPostId(targetPost.getId())
            );
            assertThat(finalCount).isEqualTo((long) threadCount);
        }
    }

    @Nested
    @DisplayName("III. 스케줄러 동기화")
    class SchedulerTest {
        @Test
        @DisplayName("SCENE 7. [Scheduler] Redis의 좋아요 수를 PostStats 테이블에 동기화한다.")
        void sync_Redis_To_PostStats() {
            // Given: Redis에 데이터 세팅
            String countKey = "like:post:count:" + targetPost.getId();
            redisTemplate.opsForValue().set(countKey, "100");

            // When
            likeCountScheduler.syncPostLikes();

            // Then
            PostStats stats = postStatsRepository.findById(targetPost.getId()).orElseThrow();
            assertThat(stats.getLikeCount()).isEqualTo(100L);
        }
    }

    // --- Helpers (생략 없이 동일) ---
    private User createUser(String email, String nickname) {
        TransactionTemplate requireNewTemplate = new TransactionTemplate(transactionManager);
        requireNewTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return requireNewTemplate.execute(status -> {
            User user = User.builder()
                    .email(email)
                    .username("user_" + UUID.randomUUID().toString().substring(0, 8))
                    .nickname(nickname)
                    .password("pw")
                    .role(Role.USER)
                    .userType(UserType.COMMON)
                    .isEmailVerified(true)
                    .build();
            return userRepository.save(user);
        });
    }

    private Post createPost(User user, String title) {
        TransactionTemplate requireNewTemplate = new TransactionTemplate(transactionManager);
        requireNewTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return requireNewTemplate.execute(status -> {
            User mergedUser = userRepository.findById(user.getId()).orElseThrow();

            Post post = Post.builder()
                    .user(mergedUser)
                    .title(title)
                    .content("Content")
                    .category(Category.IDOL)
                    .build();
            Post savedPost = postRepository.save(post);

            postStatsRepository.save(PostStats.create(savedPost));

            return savedPost;
        });
    }
}