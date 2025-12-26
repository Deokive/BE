package com.depth.deokive.domain.file.batch;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBatchTest
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
class FileCleanupBatchJobTest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired private FileRepository fileRepository;
    @Autowired private ArchiveRepository archiveRepository;
    @Autowired private UserRepository userRepository;

    // TestConfiguration에서 정의한 Mock S3Client 주입
    @Autowired private S3Client s3Client;

    @Autowired private JdbcTemplate jdbcTemplate;

    // ✅ MockBean 대체: TestConfiguration을 통해 Mock Bean을 Context에 등록
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary // 실제 S3Client 대신 이 Mock Bean이 주입되도록 우선순위 부여
        public S3Client s3Client() {
            return mock(S3Client.class);
        }
    }

    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();
        reset(s3Client); // Mock 객체 상태 초기화 (호출 기록 등)
        cleanupDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        archiveRepository.deleteAll();
        fileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("✅ 고아 파일 정리 Job 통합 테스트")
    void fileCleanupJob_IntegrationTest() throws Exception {
        // given
        // 1. [삭제 대상] 25시간 전 생성 + 연결 없음
        File targetFile = createFile("target.jpg", LocalDateTime.now().minusHours(25));

        // 2. [보존 대상 A] 1시간 전 생성 (최신 파일) + 연결 없음
        File recentFile = createFile("recent.jpg", LocalDateTime.now().minusHours(1));

        // 3. [보존 대상 B] 25시간 전 생성 + Archive에 연결됨 (사용 중)
        File linkedFile = createFile("linked.jpg", LocalDateTime.now().minusHours(25));
        createArchiveWithBanner(linkedFile);

        // when
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        // 1. Job 상태 검증
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 2. S3 삭제 요청 검증 (Mockito Verify)
        // targetFile에 대해서만 deleteObject가 호출되었는지 확인
        verify(s3Client, times(1)).deleteObject(argThat((DeleteObjectRequest req) ->
                req.key().equals(targetFile.getS3ObjectKey())
        ));

        // 보존 파일들은 호출되지 않았는지 검증
        verify(s3Client, never()).deleteObject(argThat((DeleteObjectRequest req) ->
                req.key().equals(recentFile.getS3ObjectKey()) ||
                        req.key().equals(linkedFile.getS3ObjectKey())
        ));

        // 3. DB 데이터 검증
        List<File> remainingFiles = fileRepository.findAll();
        List<Long> remainingIds = remainingFiles.stream().map(File::getId).toList();

        assertThat(remainingFiles).hasSize(2); // 3개 중 1개 삭제되어 2개 남음
        assertThat(remainingIds)
                .contains(recentFile.getId(), linkedFile.getId()) // 보존 대상 존재 확인
                .doesNotContain(targetFile.getId()); // 삭제 대상 부재 확인
    }

    // --- Helper Methods (Fixture) ---

    private File createFile(String filename, LocalDateTime createdAt) {
        // 1. 먼저 정상적으로 저장 (이때 DB에는 현재 시간이 들어감)
        File file = File.builder()
                .s3ObjectKey("files/" + filename)
                .filename(filename)
                .filePath("http://cdn.com/" + filename)
                .fileSize(1024L)
                .mediaType(MediaType.IMAGE)
                .isThumbnail(false)
                .build();

        File savedFile = fileRepository.save(file);

        // 2. JdbcTemplate을 사용하여 DB의 created_at을 강제로 과거로 변경 (JPA Auditing 우회)
        jdbcTemplate.update("UPDATE files SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(createdAt),
                savedFile.getId());

        // 3. 변경된 내용이 반영된 객체를 다시 조회하거나,
        //    테스트 검증에는 DB 상태가 중요하므로 savedFile에 시간만 세팅해서 리턴
        ReflectionTestUtils.setField(savedFile, "createdAt", createdAt);

        return savedFile;
    }

    private void createArchiveWithBanner(File bannerFile) {
        User user = userRepository.save(User.builder()
                .email("test@depth.com")
                .nickname("Tester")
                .username("testUser")
                .role(Role.USER)
                .build());

        archiveRepository.save(Archive.builder()
                .user(user)
                .title("Linked Archive")
                .visibility(Visibility.PUBLIC)
                .badge(Badge.NEWBIE)
                .bannerFile(bannerFile)
                .build());
    }
}