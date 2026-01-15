package com.depth.deokive.common.test;

import com.depth.deokive.domain.s3.service.S3Service;
import com.depth.deokive.domain.user.repository.UserRepository;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestSupport {

    @LocalServerPort
    protected int port;

    // --- 1. MySQL Container ---
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("deokive_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    // --- 2. Redis Container ---
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server --requirepass test")
            .withReuse(true);

    // --- 3. MailHog Container (SMTP & API) ---
    @SuppressWarnings("resource")
    static final GenericContainer<?> MAILHOG_CONTAINER = new GenericContainer<>("mailhog/mailhog")
            .withExposedPorts(1025, 8025) // 1025: SMTP, 8025: HTTP API
            .waitingFor(Wait.forHttp("/api/v2/messages").forPort(8025)) // API 헬스체크
            .withReuse(true);

    // --- 4. RabbitMQ Container (추가됨) ---
    @SuppressWarnings("resource")
    static final RabbitMQContainer RABBIT_CONTAINER = new RabbitMQContainer("rabbitmq:3.12-management")
            .withExposedPorts(5672, 15672)
            .withReuse(true);

    // AuthSteps에서 사용할 MailHog HTTP API URL (동적 포트 바인딩)
    public static String MAILHOG_HTTP_URL;

    static {
        MYSQL_CONTAINER.start();
        REDIS_CONTAINER.start();
        MAILHOG_CONTAINER.start();
        RABBIT_CONTAINER.start();

        // 컨테이너 시작 후 호스트와 매핑된 포트를 사용하여 URL 구성
        MAILHOG_HTTP_URL = "http://" + MAILHOG_CONTAINER.getHost() + ":" + MAILHOG_CONTAINER.getMappedPort(8025);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL Configuration
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL_CONTAINER::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create"); // E2E 테스트는 create 권장
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");

        // Redis Configuration
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379).toString());
        registry.add("spring.data.redis.password", () -> "test");

        // MailHog Configuration
        registry.add("spring.mail.host", MAILHOG_CONTAINER::getHost);
        registry.add("spring.mail.port", () -> MAILHOG_CONTAINER.getMappedPort(1025).toString());

        // [중요] 발신자 주소 설정 (빈 문자열이면 AddressException 발생함)
        registry.add("spring.mail.username", () -> "noreply@deokive.com");
        registry.add("spring.mail.password", () -> ""); // 비밀번호는 불필요
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");

        // 메일 그룹명 설정 (EmailService 로직용)
        registry.add("spring.mail.group", () -> "Deokive Team");

        // RabbitMQ Configuration
        registry.add("spring.rabbitmq.host", RABBIT_CONTAINER::getHost);
        registry.add("spring.rabbitmq.port", RABBIT_CONTAINER::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT_CONTAINER::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT_CONTAINER::getAdminPassword);
    }

    // --- Common Beans ---
    @Autowired protected UserRepository userRepository;

    // --- Mock Beans ---
    // API 테스트에서는 S3만 Mocking (비용/시간 절약)
    // EmailService는 Mocking 하지 않음 -> 실제 MailHog로 전송
    @MockitoBean protected S3Service s3Service;

    @BeforeEach
    void baseSetUp() {
        // RestAssured 포트 설정
        RestAssured.port = port;
    }
}