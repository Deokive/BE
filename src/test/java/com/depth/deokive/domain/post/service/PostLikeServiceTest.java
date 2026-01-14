package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.enums.ViewLikeDomain;
import com.depth.deokive.common.service.LikeRedisService;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.post.dto.PostDto;
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
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
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

@DisplayName("PostLikeService 테스트")
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
    @Autowired ApplicationContext applicationContext;

    @MockitoSpyBean
    PostLikeRepository postLikeRepository;

    private User postOwner;
    private Post targetPost;

    private RabbitListenerEndpointRegistry getRegistry() {
        return applicationContext.getBean(RabbitListenerEndpointRegistry.class);
    }

    @BeforeEach
    void setUp() {
        // 리스너 시작 전에 Redis/DB 청소 (이전 테스트 잔재 제거)
        cleanupData();
        getRegistry().start();

        // 데이터 정리 후 리스너 다시 시작
        getRegistry().start();

        // 유니크한 이메일 사용 (Duplicate Entry 방지)
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        postOwner = createUser("owner-" + uniqueSuffix + "@test.com", "owner");

        targetPost = createPost(postOwner, "Target Post");
    }

    @AfterEach
    void tearDown() {
        // 리스너부터 끄고 데이터 정리 (락 충돌 방지)
        getRegistry().stop();

        // 큐 비우기
        RabbitAdmin rabbitAdmin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());
        rabbitAdmin.purgeQueue(ViewLikeDomain.POST.getQueueName(), false);
        rabbitAdmin.purgeQueue(ViewLikeDomain.ARCHIVE.getQueueName(), false);

        cleanupData();
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

            // isLiked 호출 시 Domain과 Loader 전달
            boolean isLiked = likeRedisService.isLiked(
                    ViewLikeDomain.POST,
                    targetPost.getId(),
                    liker.getId(),
                    () -> postLikeRepository.findAllUserIdsByPostId(targetPost.getId()),
                    () -> {}
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
                    () -> postLikeRepository.findAllUserIdsByPostId(targetPost.getId()),
                    () -> {}
            );
            assertThat(isLikedAfter).isFalse();
        }

        @Test
        @DisplayName("SCENE 3. [Policy] 게시글 작성자 본인도 자신의 글에 좋아요를 누를 수 있다 (Self-Like).")
        void self_Like_Should_Succeed() {
            // Given: 게시글 주인(postOwner)이 로그인 함
            UserPrincipal ownerPrincipal = UserPrincipal.from(postOwner);

            // When: 본인이 좋아요 누름
            PostDto.LikeResponse response = postService.toggleLike(ownerPrincipal, targetPost.getId());

            // Then
            assertThat(response.isLiked()).isTrue();
            assertThat(response.getLikeCount()).isEqualTo(1L);

            // Redis 검증
            Long redisCount = likeRedisService.getCount(
                    ViewLikeDomain.POST,
                    targetPost.getId(),
                    () -> postLikeRepository.findAllUserIdsByPostId(targetPost.getId()),
                    () -> {}
            );
            assertThat(redisCount).isEqualTo(1L);
        }

        @Test
        @DisplayName("SCENE 4. [Read Integration] 게시글 상세 조회(getPost) 시 Redis의 실시간 좋아요 정보가 포함되어야 한다.")
        void getPost_Should_Include_Redis_Like_Info() {
            // Given: UserA가 좋아요를 누른 상태
            User userA = createUser("scene5@test.com", "Scene5User");
            postService.toggleLike(UserPrincipal.from(userA), targetPost.getId());

            // When: UserA가 게시글 상세 조회 (getPost 호출)
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("127.0.0.1"); // 필요하다면 IP 설정

            PostDto.Response postResponse = postService.getPost(UserPrincipal.from(userA), targetPost.getId(), mockRequest);

            // Then
            // 1. 좋아요 수가 1이어야 함 (Redis에서 가져옴)
            assertThat(postResponse.getLikeCount()).isEqualTo(1L);
            // 2. 내가 눌렀으니 isLiked가 true여야 함
            assertThat(postResponse.isLiked()).isTrue();

            // When 2: 좋아요 안 누른 UserB가 조회
            User userB = createUser("scene5_b@test.com", "Scene5UserB");
            PostDto.Response responseB = postService.getPost(UserPrincipal.from(userB), targetPost.getId(), null);

            // Then 2
            assertThat(responseB.getLikeCount()).isEqualTo(1L); // 카운트는 여전히 1
            assertThat(responseB.isLiked()).isFalse();          // B는 안 눌렀으니 false
        }

        @Test
        @DisplayName("SCENE 5. [Async Persistence] Redis 변경사항이 RabbitMQ를 거쳐 DB PostLike 테이블에 반영된다.")
        void async_Persistence_Check() {
            User liker = createUser("async@test.com", "asyncUser");

            // When
            postService.toggleLike(UserPrincipal.from(liker), targetPost.getId());

            // Then (Redis)
            // getCount 호출 시 Domain과 Loader 전달
            Long redisCount = likeRedisService.getCount(
                    ViewLikeDomain.POST,
                    targetPost.getId(),
                    () -> postLikeRepository.findAllUserIdsByPostId(targetPost.getId()),
                    () -> {}
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
        @DisplayName("SCENE 6. [Thundering Herd] 동시 접속 시 DB Warming 쿼리 폭주 방지 (Redisson Lock)")
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
                    () -> postLikeRepository.findAllUserIdsByPostId(targetPost.getId()),
                    () -> {}
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

    @Nested
    @DisplayName("IV. 예외 및 데이터 정합성 (Lazy Validation & Cleanup)")
    class EdgeCaseTest {

        @Test
        @DisplayName("SCENE 8. [Lazy Validation] 존재하지 않는 게시글에 좋아요 시도 시, DB를 확인하고 예외를 던지며 Redis를 오염시키지 않는다.")
        void not_Found_Post_Should_Throw_Exception_And_Protect_Redis() {
            // Given: 존재하지 않는 ID
            Long nonExistentId = 999999L;

            // When & Then: 예외 발생 검증
            // PostService.toggleLike 내부의 Lazy Validator 람다가 실행되는지 확인
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                            postService.toggleLike(UserPrincipal.from(postOwner), nonExistentId)
                    )
                    .isInstanceOf(com.depth.deokive.system.exception.model.RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", com.depth.deokive.system.exception.model.ErrorCode.POST_NOT_FOUND);

            // Then: Redis에 키가 생성되지 않아야 함 (Warming 방지)
            String countKey = "like:post:count:" + nonExistentId;
            assertThat(redisTemplate.hasKey(countKey)).isFalse();
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

    private void cleanupData() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();

        TransactionTemplate requireNewTemplate = new TransactionTemplate(transactionManager);
        requireNewTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        requireNewTemplate.execute(status -> {
            postLikeRepository.deleteAllInBatch();
            postStatsRepository.deleteAllInBatch();
            postRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
            return null;
        });
    }
}