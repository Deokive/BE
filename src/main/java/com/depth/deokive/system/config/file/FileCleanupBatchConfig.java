package com.depth.deokive.system.config.file;

import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FileCleanupBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final S3Client s3Client;
    private final FileRepository fileRepository;
    private final DataSource dataSource;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${scheduler.file-cleanup.threshold-hours:24}")
    private int thresholdHours;

    @Value("${scheduler.file-cleanup.skip-limit:10}")
    private int skipLimit;

    @Value("${scheduler.file-cleanup.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${scheduler.file-cleanup.retry.delay-ms:1000}")
    private long retryDelayMs;

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
                .reader(orphanedFileCursorReader())
                .processor(s3DeleteProcessor())
                .writer(fileDeleteWriter())
                .faultTolerant()
                .skip(S3Exception.class)
                .skipLimit(skipLimit)
                .build();
    }

    // Reader: JdbcCursorItemReader (Streaming) + Native SQL (Left Join Anti-Pattern)
    @Bean
    public JdbcCursorItemReader<File> orphanedFileCursorReader() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(thresholdHours);

        return new JdbcCursorItemReaderBuilder<File>()
                .name("orphanedFileCursorReader")
                .fetchSize(CHUNK_SIZE)
                .dataSource(dataSource)
                .rowMapper((rs, rowNum) -> File.builder()
                        .id(rs.getLong("id"))
                        .s3ObjectKey(rs.getString("s3Object_key"))
                        .filename(rs.getString("filename"))
                        .fileSize(rs.getLong("file_size"))
                        .mediaType(MediaType.valueOf(rs.getString("media_type")))
                        .build()
                )
                .sql("""
                    SELECT f.id, f.s3Object_key, f.filename, f.file_size, f.media_type
                    FROM files f
                    -- 1. Archive Banner
                    LEFT JOIN archive a ON f.id = a.banner_file_id
                    -- 2. Ticket Image
                    LEFT JOIN ticket t ON f.id = t.file_id
                    -- 3. Diary Image
                    LEFT JOIN diary_file_map dfm ON f.id = dfm.file_id
                    -- 4. Post Image (Content/Preview in Map)
                    LEFT JOIN post_file_map pfm ON f.id = pfm.file_id
                    -- 5. Gallery Image
                    LEFT JOIN gallery g ON f.id = g.file_id
                    
                    WHERE f.created_at < ?
                      AND a.id IS NULL
                      AND t.id IS NULL
                      AND dfm.id IS NULL
                      AND pfm.id IS NULL
                      AND g.id IS NULL
                """)
                .queryArguments(Timestamp.valueOf(threshold))
                .build();
    }

    @Bean
    public ItemProcessor<File, File> s3DeleteProcessor() {
        return file -> {
            int attempt = 0;
            Exception lastException = null;

            List<ObjectIdentifier> objectsToDelete = new ArrayList<>();
            objectsToDelete.add(ObjectIdentifier.builder().key(file.getS3ObjectKey()).build());

            // 이미지: small + medium 썸네일 삭제
            if (file.getMediaType() == MediaType.IMAGE) {
                // S3에 실제로 존재하는지 체크하지 않고 delete 요청 보내도 에러 안 남 (S3 특성) -> 과감하게 삭제 요청 목록에 추가
                String smallKey = ThumbnailUtils.getSmallThumbnailKey(file.getS3ObjectKey());
                String mediumKey = ThumbnailUtils.getMediumThumbnailKey(file.getS3ObjectKey());

                if (smallKey != null) objectsToDelete.add(ObjectIdentifier.builder().key(smallKey).build());
                if (mediumKey != null) objectsToDelete.add(ObjectIdentifier.builder().key(mediumKey).build());
            }
            // 동영상: medium 썸네일만 삭제 (small은 미지원)
            else if (file.getMediaType() == MediaType.VIDEO) {
                String mediumKey = ThumbnailUtils.getMediumThumbnailKey(file.getS3ObjectKey());
                if (mediumKey != null) objectsToDelete.add(ObjectIdentifier.builder().key(mediumKey).build());
            }

            while (attempt < maxRetryAttempts) {
                try {
                    attempt++;
                    log.info("🟢 [Batch] Deleting S3 Objects for FileId {}: {} (Attempt {}/{})",
                            file.getId(), file.getS3ObjectKey(), attempt, maxRetryAttempts);

                    s3Client.deleteObjects(builder -> builder
                            .bucket(bucketName)
                            .delete(Delete.builder().objects(objectsToDelete).build())
                    );

                    return file; // 성공 시 파일 객체 반환

                } catch (Exception e) {
                    lastException = e;
                    log.warn("⚠️ [Batch] Failed to delete S3 Object (Attempt {}/{}): {} - {}",
                            attempt, maxRetryAttempts, file.getS3ObjectKey(), e.getMessage());

                    if (attempt < maxRetryAttempts) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Retry delay interrupted", ie);
                        }
                    }
                }
            }

            log.error("🔴 [Batch] Failed to delete S3 Object after {} attempts: {}",
                    maxRetryAttempts, file.getS3ObjectKey(), lastException);
            throw lastException != null ? lastException : new RuntimeException("S3 delete failed after retries");
        };
    }

    @Bean
    public ItemWriter<File> fileDeleteWriter() {
        return files -> {
            log.info("🟢 [Batch] Deleting {} file records from DB", files.size());
            fileRepository.deleteAll(files);
        };
    }
}