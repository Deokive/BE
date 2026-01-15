package com.depth.deokive.common.test;

import com.depth.deokive.domain.auth.service.EmailService;
import com.depth.deokive.domain.s3.service.S3Service;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.security.model.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {

    // 1. MySQL Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("deokive_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .withEnv("TZ", "Asia/Seoul");

    // 2. Redis Container (Context Load 실패 방지)
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server --requirepass test")
            .withReuse(true);

    // 3. RabbitMQ (추가됨)
    @SuppressWarnings("resource")
    static final RabbitMQContainer RABBIT_CONTAINER = new RabbitMQContainer("rabbitmq:3.12-management")
            .withExposedPorts(5672, 15672)
            .withReuse(true);

    static {
        MYSQL_CONTAINER.start();
        REDIS_CONTAINER.start();
        RABBIT_CONTAINER.start(); // 시작
    }

    // Datasource & Redis 설정 덮어쓰기
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", () ->
                MYSQL_CONTAINER.getJdbcUrl() + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL_CONTAINER::getDriverClassName);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");

        // Redis
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379).toString());
        registry.add("spring.data.redis.password", () -> "test");

        // RabbitMQ 설정 (추가됨)
        registry.add("spring.rabbitmq.host", RABBIT_CONTAINER::getHost);
        registry.add("spring.rabbitmq.port", RABBIT_CONTAINER::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT_CONTAINER::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT_CONTAINER::getAdminPassword);

        registry.add("scheduler.post-like-cron", () -> "-");
        registry.add("scheduler.archive-like-cron", () -> "-");
        registry.add("scheduler.post-view-cron", () -> "-");
        registry.add("scheduler.archive-view-cron", () -> "-");
        registry.add("scheduler.post-hot-score-cron", () -> "-");
        registry.add("scheduler.archive-hot-score-cron", () -> "-");
        registry.add("scheduler.file-cleanup-cron", () -> "-");
        registry.add("scheduler.badge-cron", () -> "-");
    }

    @Autowired protected UserRepository userRepository;
    @Autowired protected EntityManager em;

    // 외부 서비스 Mocking
    // S3Service를 Mocking하여 비즈니스 로직 테스트 시 S3 내부 동작은 "성공"한 것으로 간주
    @MockitoBean protected S3Service s3Service;

    // 이메일 발송 Mocking
    @MockitoBean protected EmailService emailService;

    // 공통 사용 필드 (테스트 클래스에서 @BeforeEach로 초기화하여 사용 권장)
    protected User user;
    protected User friendUser;
    protected User strangerUser;

    /** SecurityContext에 인증 정보 설정 (로그인 시뮬레이션) : JPA Auditing (@CreatedBy) 동작을 위해 필수 */
    protected void setupMockUser(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 영속성 컨텍스트 초기화 : 쿼리가 실제로 DB에 날아가는지 검증하기 위해 사용 */
    protected void flushAndClear() {
        em.flush();
        em.clear();
    }
}