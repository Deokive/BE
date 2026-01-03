package com.depth.deokive.domain.file.batch;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.diary.entity.Diary;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.entity.DiaryFileMap;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.gallery.entity.Gallery;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostFileMap;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.ticket.entity.Ticket;
import com.depth.deokive.domain.ticket.entity.TicketBook;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class FileCleanupBatchIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private S3Client s3Client;

    @Autowired
    @Qualifier("fileCleanupJob")
    private Job fileCleanupJob;

    private User testUser;

    @AfterEach
    void tearDown() {
        // FK 제약조건 역순으로 삭제 (테스트 환경 초기화)
        // 실제 운영에선 truncate 등을 쓰지 않지만 테스트 격리를 위해 수행
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE gallery");
        jdbcTemplate.execute("TRUNCATE TABLE gallery_book");
        jdbcTemplate.execute("TRUNCATE TABLE diary_file_map");
        jdbcTemplate.execute("TRUNCATE TABLE diary");
        jdbcTemplate.execute("TRUNCATE TABLE diary_book");
        jdbcTemplate.execute("TRUNCATE TABLE ticket");
        jdbcTemplate.execute("TRUNCATE TABLE ticket_book");
        jdbcTemplate.execute("TRUNCATE TABLE post_file_map");
        jdbcTemplate.execute("TRUNCATE TABLE post");
        jdbcTemplate.execute("TRUNCATE TABLE archive");
        jdbcTemplate.execute("TRUNCATE TABLE files");
        jdbcTemplate.execute("TRUNCATE TABLE users");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    @DisplayName("통합 검증: 모든 도메인 연결 관계를 고려하여 고아 파일만 정확히 삭제한다.")
    void fileCleanupJob_Full_Scenario() throws Exception {
        // [설정] 테스트할 Job 지정
        jobLauncherTestUtils.setJob(fileCleanupJob);

        // given
        createBaseUser(); // 트랜잭션 커밋 완료

        // 1. [삭제 대상] 24시간 지난 고아 파일
        File orphanFile = createFile("orphan.jpg", LocalDateTime.now().minusHours(25));

        // 2. [유지 대상] 24시간 안 지난 고아 파일 (최신 파일)
        File recentFile = createFile("recent.jpg", LocalDateTime.now().minusHours(1));

        // 3. [유지 대상] Archive Banner 연결
        File archiveBanner = createFile("archive.jpg", LocalDateTime.now().minusHours(48));
        createArchiveWithBanner(archiveBanner);

        // 4. [유지 대상] Post Content (FileMap) 연결
        File postContent = createFile("post_content.jpg", LocalDateTime.now().minusHours(48));
        createPostWithFileMap(postContent);

        // 5. [유지 대상 - 핵심] Post Thumbnail (역정규화 필드) 연결
        File postThumb = createFile("post_thumb.jpg", LocalDateTime.now().minusHours(48));
        createPostWithThumbnail(postThumb);

        // 6. [유지 대상] Diary (FileMap) 연결
        File diaryImage = createFile("diary.jpg", LocalDateTime.now().minusHours(48));
        createDiaryWithImage(diaryImage);

        // 7. [유지 대상] Ticket Image 연결
        File ticketImage = createFile("ticket.jpg", LocalDateTime.now().minusHours(48));
        createTicketWithImage(ticketImage);

        // 8. [유지 대상] Gallery Image 연결
        File galleryImage = createFile("gallery.jpg", LocalDateTime.now().minusHours(48));
        createGalleryWithImage(galleryImage);

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();

        // then
        // 1. Job 성공 여부
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        // 2. DB 데이터 검증
        // 고아 파일은 삭제되어야 함
        assertThat(fileRepository.findById(orphanFile.getId())).isEmpty();

        // 나머지는 모두 살아있어야 함
        assertThat(fileRepository.findById(recentFile.getId())).isPresent(); // 최신 파일
        assertThat(fileRepository.findById(archiveBanner.getId())).isPresent(); // 아카이브
        assertThat(fileRepository.findById(postContent.getId())).isPresent(); // 포스트 본문
        assertThat(fileRepository.findById(postThumb.getId())).isPresent(); // 포스트 썸네일 (중요!)
        assertThat(fileRepository.findById(diaryImage.getId())).isPresent(); // 다이어리
        assertThat(fileRepository.findById(ticketImage.getId())).isPresent(); // 티켓
        assertThat(fileRepository.findById(galleryImage.getId())).isPresent(); // 갤러리

        // 3. S3 삭제 요청은 orphanFile 1개에 대해서만 발생해야 함
        verify(s3Client, times(1)).deleteObject(any(Consumer.class));
    }

    // --- Helper Methods (TransactionTemplate 적용 + Merge) ---

    private void createBaseUser() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            testUser = User.builder()
                    .email("test@test.com")
                    .username("tester")
                    .nickname("tester")
                    .role(Role.USER)
                    .build();
            em.persist(testUser);
        });
    }

    private File createFile(String filename, LocalDateTime createdAt) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            File file = File.builder()
                    .s3ObjectKey("files/" + filename)
                    .filename(filename)
                    .filePath("http://cdn/" + filename)
                    .fileSize(100L)
                    .mediaType(MediaType.IMAGE)
                    .createdBy(testUser.getId())
                    .lastModifiedBy(testUser.getId())
                    .build();
            em.persist(file);
            em.flush();
            // 생성 시간 조작 (Auditing 우회)
            jdbcTemplate.update("UPDATE files SET created_at = ? WHERE id = ?", createdAt, file.getId());
            return file;
        });
    }

    private void createArchiveWithBanner(File banner) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User u = em.merge(testUser);
            File f = em.merge(banner);
            Archive archive = Archive.builder().title("A").user(u).bannerFile(f).visibility(Visibility.PUBLIC).build();
            em.persist(archive);
        });
    }

    private void createPostWithFileMap(File file) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User u = em.merge(testUser);
            File f = em.merge(file);
            Post post = Post.builder().title("P").content("C").category(Category.IDOL).user(u).build();
            em.persist(post);
            PostFileMap map = PostFileMap.builder().post(post).file(f).mediaRole(MediaRole.CONTENT).sequence(0).build();
            em.persist(map);
        });
    }

    private void createPostWithThumbnail(File thumbnail) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User u = em.merge(testUser);
            File f = em.merge(thumbnail);
            Post post = Post.builder().title("P_Thumb").content("C").category(Category.IDOL).user(u)
                    .thumbnailFile(f) // [검증 핵심]
                    .build();
            em.persist(post);
        });
    }

    private void createDiaryWithImage(File file) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User u = em.merge(testUser);
            File f = em.merge(file);
            // DiaryBook(Archive) 필요
            Archive archive = Archive.builder().title("DiaryBook").user(u).visibility(Visibility.PUBLIC).build();
            em.persist(archive);
            DiaryBook book = DiaryBook.builder().archive(archive).title("DB").build();
            em.persist(book);

            Diary diary = Diary.builder().title("D").content("C").color("#000000").recordedAt(LocalDate.now()).visibility(Visibility.PUBLIC).diaryBook(book).build();
            em.persist(diary);

            DiaryFileMap map = DiaryFileMap.builder().diary(diary).file(f).mediaRole(MediaRole.CONTENT).sequence(0).build();
            em.persist(map);
        });
    }

    private void createTicketWithImage(File file) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User u = em.merge(testUser);
            File f = em.merge(file);
            Archive archive = Archive.builder().title("TicketBook").user(u).visibility(Visibility.PUBLIC).build();
            em.persist(archive);
            TicketBook book = TicketBook.builder().archive(archive).title("TB").build();
            em.persist(book);

            Ticket ticket = Ticket.builder().title("T").ticketBook(book).file(f).build();
            em.persist(ticket);
        });
    }

    private void createGalleryWithImage(File file) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User u = em.merge(testUser);
            File f = em.merge(file);
            Archive archive = Archive.builder().title("GalleryBook").user(u).visibility(Visibility.PUBLIC).build();
            em.persist(archive);
            GalleryBook book = GalleryBook.builder().archive(archive).title("GB").build();
            em.persist(book);

            Gallery gallery = Gallery.builder().galleryBook(book).archiveId(archive.getId()).file(f).build();
            em.persist(gallery);
        });
    }
}