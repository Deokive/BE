package com.depth.deokive.system.config.file;

import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.LocalDateTime;
import java.util.Collections;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FileCleanupBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final S3Client s3Client;
    private final FileRepository fileRepository;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job fileCleanupJob() {
        return new JobBuilder("fileCleanupJob", jobRepository)
                .start(fileCleanupStep())
                .build();
    }

    @Bean
    public Step fileCleanupStep() {
        return new StepBuilder("fileCleanupStep", jobRepository)
                .<File, File>chunk(CHUNK_SIZE, transactionManager)
                .reader(orphanedFileReader())
                .processor(s3DeleteProcessor())
                .writer(fileDeleteWriter())
                .faultTolerant()
                .skip(S3Exception.class) // S3 네트워크 에러 등은 Skip하고 다음 파일 진행
                .skipLimit(10)
                .build();
    }

    // Reader: 24시간 지난 고아 파일 조회
    // JPQL을 사용하여 5개의 도메인 테이블에 참조되지 않은 파일을 필터링
    @Bean
    public JpaPagingItemReader<File> orphanedFileReader() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24); // 배치 실행 시점 기준 24시간 전

        return new JpaPagingItemReaderBuilder<File>()
            .name("orphanedFileReader")
            .entityManagerFactory(entityManagerFactory)
            .pageSize(CHUNK_SIZE)
            .queryString(
                "SELECT f FROM File f " +
                    "WHERE f.createdAt < :threshold " +
                    // 1. Archive 배너
                    "AND f.id NOT IN (SELECT a.bannerFile.id FROM Archive a WHERE a.bannerFile.id IS NOT NULL) " +
                    // 2. Ticket 이미지
                    "AND f.id NOT IN (SELECT t.file.id FROM Ticket t WHERE t.file.id IS NOT NULL) " +
                    // 3. Diary 이미지 (Map 테이블)
                    "AND f.id NOT IN (SELECT dfm.file.id FROM DiaryFileMap dfm) " +
                    // 4. Post 이미지 (Map 테이블)
                    "AND f.id NOT IN (SELECT pfm.file.id FROM PostFileMap pfm) " +
                    // 5. Gallery 이미지
                    "AND f.id NOT IN (SELECT g.file.id FROM Gallery g)"
            )
            .parameterValues(Collections.singletonMap("threshold", threshold))
            .build();
    }

    // Processor: S3 객체 삭제 -> DB 삭제 전 S3에서 먼저 지움
    @Bean
    public ItemProcessor<File, File> s3DeleteProcessor() {
        return file -> {
            try {
                log.info("🟢 [Batch] Deleting S3 Object: {}", file.getS3ObjectKey());

                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(file.getS3ObjectKey())
                        .build();

                s3Client.deleteObject(deleteRequest);

                return file;

            } catch (Exception e) {
                // 여기서 예외를 던지면 Transaction이 롤백되어 DB 삭제도 안 일어남 (의도된 동작)
                log.error("🔴 [Batch] Failed to delete S3 Object: {}", file.getS3ObjectKey(), e);
                throw e;
            }
        };
    }

    /**
     * Writer: DB 메타데이터 삭제
     */
    @Bean
    public ItemWriter<File> fileDeleteWriter() {
        return files -> {
            log.info("🟢 [Batch] Deleting {} file records from DB", files.size());
            fileRepository.deleteAll(files);
        };
    }
}