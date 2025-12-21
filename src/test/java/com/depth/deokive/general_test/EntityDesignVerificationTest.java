package com.depth.deokive.general_test;

import com.depth.deokive.domain.archive.entity.*;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.archive.repository.*;
import com.depth.deokive.domain.comment.entity.Comment;
import com.depth.deokive.domain.comment.entity.CommentCount;
import com.depth.deokive.domain.comment.repository.CommentCountRepository;
import com.depth.deokive.domain.comment.repository.CommentRepository;
import com.depth.deokive.domain.diary.entity.Diary;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.entity.DiaryFileMap;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.event.entity.*;
import com.depth.deokive.domain.event.repository.*;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.entity.PostLikeCount;
import com.depth.deokive.domain.post.repository.PostLikeCountRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
})
@DisplayName("Deokive Entity Design Verification")
class EntityDesignVerificationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private FriendMapRepository friendMapRepository;
    @Autowired private ArchiveRepository archiveRepository;
    @Autowired private ArchiveLikeCountRepository archiveLikeCountRepository;
    @Autowired private ArchiveFileMapRepository archiveFileMapRepository;
    @Autowired private FileRepository fileRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private SportRecordRepository sportRecordRepository;
    @Autowired private HashtagRepository hashtagRepository;
    @Autowired private EventHashtagMapRepository eventHashtagMapRepository;
    @Autowired private DiaryBookRepository diaryBookRepository;
    @Autowired private DiaryRepository diaryRepository;
    @Autowired private DiaryFileMapRepository diaryFileMapRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostLikeCountRepository postLikeCountRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private CommentCountRepository commentCountRepository;

    @BeforeEach
    void setUp() {
        commentCountRepository.deleteAll();
        commentRepository.deleteAll();
        postLikeCountRepository.deleteAll();
        postRepository.deleteAll();
        diaryFileMapRepository.deleteAll();
        diaryRepository.deleteAll();
        diaryBookRepository.deleteAll();
        eventHashtagMapRepository.deleteAll();
        hashtagRepository.deleteAll();
        sportRecordRepository.deleteAll();
        eventRepository.deleteAll();
        archiveFileMapRepository.deleteAll();
        archiveLikeCountRepository.deleteAll();
        archiveRepository.deleteAll();
        fileRepository.deleteAll();
        friendMapRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =========================================================================
    // 1. User & FriendMap
    // =========================================================================
    @Test
    @DisplayName("1. User & FriendMap (2-Row Strategy)")
    @Transactional
    void testUserAndFriends() {
        // Step 1-1: 사용자 생성
        User userA = User.builder()
                .email("userA@test.com")
                .username("userA")
                .nickname("철수")
                .build();
        User userB = User.builder()
                .email("userB@test.com")
                .username("userB")
                .nickname("영희")
                .build();
        userRepository.saveAll(List.of(userA, userB));

        assertThat(userA.getId()).isNotNull();
        assertThat(userB.getId()).isNotNull();

        // Step 1-2: 친구 관계 2-Row 전략(양방향 저장) 테스트
        FriendMap map1 = FriendMap.builder()
                .user(userA)
                .friend(userB)
                .requestedBy(userA)
                .friendStatus(FriendStatus.ACCEPTED)
                .build();
        FriendMap map2 = FriendMap.builder()
                .user(userB)
                .friend(userA)
                .requestedBy(userA)
                .friendStatus(FriendStatus.ACCEPTED)
                .build();

        friendMapRepository.saveAll(List.of(map1, map2));

        // 검증
        assertThat(map1.getId()).isNotNull();
        assertThat(map2.getId()).isNotNull();
        assertThat(map1.getUser().getId()).isEqualTo(userA.getId());
        assertThat(map1.getFriend().getId()).isEqualTo(userB.getId());
        assertThat(map2.getUser().getId()).isEqualTo(userB.getId());
        assertThat(map2.getFriend().getId()).isEqualTo(userA.getId());
    }

    // =========================================================================
    // 2. Archive & File System (MapsId, ArchiveFileMap)
    // =========================================================================
    @Test
    @DisplayName("2. Archive & @MapsId & File Indexing")
    @Transactional
    void testArchiveSystem() {
        // 사전 조건: User 생성
        User user = User.builder()
                .email("userA@test.com")
                .username("userA")
                .nickname("철수")
                .build();
        userRepository.save(user);

        // Step 2-1: 아카이브 생성
        Archive archive = Archive.builder()
                .title("철수의 덕질 아카이브")
                .user(user)
                .visibility(Visibility.PUBLIC)
                .build();
        archiveRepository.save(archive);

        assertThat(archive.getId()).isNotNull();

        // Step 2-2: @MapsId 검증
        ArchiveLikeCount likeCount = ArchiveLikeCount.builder()
                .archive(archive)
                .build();
        archiveLikeCountRepository.save(likeCount);

        assertThat(likeCount.getArchiveId())
                .as("@MapsId 매핑 실패: 부모-자식 ID 불일치")
                .isEqualTo(archive.getId());

        // Step 2-3: 파일 매핑 및 Index(role+seq) 검증
        File file1 = createFile("banner.jpg", MediaType.IMAGE);
        File file2 = createFile("content.jpg", MediaType.IMAGE);
        fileRepository.saveAll(List.of(file1, file2));

        ArchiveFileMap bannerMap = ArchiveFileMap.builder()
                .archive(archive)
                .file(file1)
                .mediaRole(MediaRole.PREVIEW)
                .sequence(1)
                .build();
        ArchiveFileMap contentMap = ArchiveFileMap.builder()
                .archive(archive)
                .file(file2)
                .mediaRole(MediaRole.CONTENT)
                .sequence(1)
                .build();

        archiveFileMapRepository.saveAll(List.of(bannerMap, contentMap));

        assertThat(bannerMap.getMediaRole()).isEqualTo(MediaRole.PREVIEW);
        assertThat(contentMap.getMediaRole()).isEqualTo(MediaRole.CONTENT);
    }

    // =========================================================================
    // 3. Event & SportRecord (Hashtag Map)
    // =========================================================================
    @Test
    @DisplayName("3. Event & Hashtag (M:N Relation)")
    @Transactional
    void testEventSystem() {
        // 사전 조건: User 및 Archive 생성
        User user = User.builder()
                .email("userA@test.com")
                .username("userA")
                .nickname("철수")
                .build();
        userRepository.save(user);

        Archive archive = Archive.builder()
                .title("철수의 덕질 아카이브")
                .user(user)
                .visibility(Visibility.PUBLIC)
                .build();
        archiveRepository.save(archive);

        // Step 3-1: 이벤트 및 종속 엔티티(SportRecord) 생성
        Event event = Event.builder()
                .archive(archive)
                .title("야구 직관")
                .date(LocalDateTime.now())
                .hasTime(false)
                .isSportType(true)
                .color("#FFFFFF")
                .build();
        eventRepository.save(event);

        SportRecord record = SportRecord.builder()
                .event(event)
                .team1("LG")
                .score1(5)
                .team2("KIA")
                .score2(3)
                .build();
        sportRecordRepository.save(record);

        // SportRecord ID 검증
        assertThat(record.getEventId())
                .as("SportRecord @MapsId 실패")
                .isEqualTo(event.getId());

        // Step 3-2: 해시태그 M:N 매핑(Unique Index) 검증
        Hashtag tag1 = Hashtag.builder().name("야구").build();
        Hashtag tag2 = Hashtag.builder().name("직관").build();
        hashtagRepository.saveAll(List.of(tag1, tag2));

        EventHashtagMap map1 = EventHashtagMap.builder()
                .event(event)
                .hashtag(tag1)
                .build();
        EventHashtagMap map2 = EventHashtagMap.builder()
                .event(event)
                .hashtag(tag2)
                .build();
        eventHashtagMapRepository.saveAll(List.of(map1, map2));

        assertThat(map1.getEvent().getId()).isEqualTo(event.getId());
        assertThat(map1.getHashtag().getId()).isEqualTo(tag1.getId());
        assertThat(map2.getEvent().getId()).isEqualTo(event.getId());
        assertThat(map2.getHashtag().getId()).isEqualTo(tag2.getId());
    }

    // =========================================================================
    // 4. Diary System (Folder Structure)
    // =========================================================================
    @Test
    @DisplayName("4. Diary System (Hierarchy)")
    @Transactional
    void testDiarySystem() {
        // 사전 조건: User 및 Archive 생성
        User user = User.builder()
                .email("userA@test.com")
                .username("userA")
                .nickname("철수")
                .build();
        userRepository.save(user);

        Archive archive = Archive.builder()
                .title("철수의 덕질 아카이브")
                .user(user)
                .visibility(Visibility.PUBLIC)
                .build();
        archiveRepository.save(archive);

        // Step 4-1: 일기장(Book) -> 일기(Diary) 구조 생성
        DiaryBook book = DiaryBook.builder()
                .archive(archive)
                .title("2024 일기장")
                .build();
        diaryBookRepository.save(book);

        Diary diary = Diary.builder()
                .diaryBook(book)
                .title("오늘의 일기")
                .content("굿")
                .recordedAt(LocalDate.now())
                .color("white")
                .build();
        diaryRepository.save(diary);

        File diaryImg = createFile("diary.jpg", MediaType.IMAGE);
        fileRepository.save(diaryImg);

        DiaryFileMap diaryMap = DiaryFileMap.builder()
                .diary(diary)
                .file(diaryImg)
                .mediaRole(MediaRole.CONTENT)
                .sequence(1)
                .build();
        diaryFileMapRepository.save(diaryMap);

        assertThat(book.getId()).isNotNull();
        assertThat(diary.getId()).isNotNull();
        assertThat(diary.getDiaryBook().getId()).isEqualTo(book.getId());
        assertThat(diaryMap.getDiary().getId()).isEqualTo(diary.getId());
        assertThat(diaryMap.getFile().getId()).isEqualTo(diaryImg.getId());
    }

    // =========================================================================
    // 5. Community System (Post, Comment Path, LikeCount)
    // =========================================================================
    @Test
    @DisplayName("5. Community (Path Enumeration & Counts)")
    @Transactional
    void testCommunitySystem() {
        // 사전 조건: User 생성
        User user = User.builder()
                .email("userA@test.com")
                .username("userA")
                .nickname("철수")
                .build();
        userRepository.save(user);

        // Step 5-1: 게시글 및 Count 테이블(@MapsId) 생성
        Post post = Post.builder()
                .user(user)
                .title("가입 인사")
                .content("반갑습니다.")
                .category(Category.IDOL)
                .build();
        postRepository.save(post);

        PostLikeCount likeCount = PostLikeCount.builder()
                .post(post)
                .build();
        CommentCount commentCount = CommentCount.builder()
                .post(post)
                .build();
        postLikeCountRepository.save(likeCount);
        commentCountRepository.save(commentCount);

        assertThat(post.getId()).isNotNull();
        assertThat(likeCount.getPostId())
                .as("PostLikeCount @MapsId 실패")
                .isEqualTo(post.getId());
        assertThat(commentCount.getPostId())
                .as("CommentCount @MapsId 실패")
                .isEqualTo(post.getId());

        // Step 5-2: 댓글 Path Enumeration(무한 뎁스) 검증
        Comment root = Comment.builder()
                .post(post)
                .user(user)
                .content("부모댓글")
                .path("00001")
                .build();
        commentRepository.save(root);

        Comment child = Comment.builder()
                .post(post)
                .user(user)
                .content("자식댓글")
                .path("0000100001")
                .build();
        commentRepository.save(child);

        assertThat(root.getPath()).isEqualTo("00001");
        assertThat(child.getPath()).isEqualTo("0000100001");
        assertThat(child.getPath()).startsWith(root.getPath());
    }

    // =========================================================================
    // 6. Data Retrieval Tests
    // =========================================================================
    @Test
    @DisplayName("6. Data Retrieval Tests")
    @Transactional
    void testDataRetrieval() {
        // 사전 조건: 전체 데이터 생성
        User userA = User.builder()
                .email("userA@test.com")
                .username("userA")
                .nickname("철수")
                .build();
        User userB = User.builder()
                .email("userB@test.com")
                .username("userB")
                .nickname("영희")
                .build();
        userRepository.saveAll(List.of(userA, userB));

        FriendMap map1 = FriendMap.builder()
                .user(userA)
                .friend(userB)
                .requestedBy(userA)
                .friendStatus(FriendStatus.ACCEPTED)
                .build();
        FriendMap map2 = FriendMap.builder()
                .user(userB)
                .friend(userA)
                .requestedBy(userA)
                .friendStatus(FriendStatus.ACCEPTED)
                .build();
        friendMapRepository.saveAll(List.of(map1, map2));

        Archive archive = Archive.builder()
                .title("철수의 덕질 아카이브")
                .user(userA)
                .visibility(Visibility.PUBLIC)
                .build();
        archiveRepository.save(archive);

        ArchiveLikeCount likeCount = ArchiveLikeCount.builder()
                .archive(archive)
                .build();
        archiveLikeCountRepository.save(likeCount);

        File file1 = createFile("banner.jpg", MediaType.IMAGE);
        File file2 = createFile("content.jpg", MediaType.IMAGE);
        fileRepository.saveAll(List.of(file1, file2));

        ArchiveFileMap bannerMap = ArchiveFileMap.builder()
                .archive(archive)
                .file(file1)
                .mediaRole(MediaRole.PREVIEW)
                .sequence(1)
                .build();
        ArchiveFileMap contentMap = ArchiveFileMap.builder()
                .archive(archive)
                .file(file2)
                .mediaRole(MediaRole.CONTENT)
                .sequence(1)
                .build();
        archiveFileMapRepository.saveAll(List.of(bannerMap, contentMap));

        Event event = Event.builder()
                .archive(archive)
                .title("야구 직관")
                .date(LocalDateTime.now())
                .hasTime(false)
                .isSportType(true)
                .color("#FFFFFF")
                .build();
        eventRepository.save(event);

        SportRecord record = SportRecord.builder()
                .event(event)
                .team1("LG")
                .score1(5)
                .team2("KIA")
                .score2(3)
                .build();
        sportRecordRepository.save(record);

        Hashtag tag1 = Hashtag.builder().name("야구").build();
        Hashtag tag2 = Hashtag.builder().name("직관").build();
        hashtagRepository.saveAll(List.of(tag1, tag2));

        EventHashtagMap eventHashtagMap1 = EventHashtagMap.builder()
                .event(event)
                .hashtag(tag1)
                .build();
        EventHashtagMap eventHashtagMap2 = EventHashtagMap.builder()
                .event(event)
                .hashtag(tag2)
                .build();
        eventHashtagMapRepository.saveAll(List.of(eventHashtagMap1, eventHashtagMap2));

        DiaryBook diaryBook = DiaryBook.builder()
                .archive(archive)
                .title("2024 일기장")
                .build();
        diaryBookRepository.save(diaryBook);

        Diary diary = Diary.builder()
                .diaryBook(diaryBook)
                .title("오늘의 일기")
                .content("굿")
                .recordedAt(LocalDate.now())
                .color("white")
                .build();
        diaryRepository.save(diary);

        File diaryImg = createFile("diary.jpg", MediaType.IMAGE);
        fileRepository.save(diaryImg);

        DiaryFileMap diaryFileMap = DiaryFileMap.builder()
                .diary(diary)
                .file(diaryImg)
                .mediaRole(MediaRole.CONTENT)
                .sequence(1)
                .build();
        diaryFileMapRepository.save(diaryFileMap);

        Post post = Post.builder()
                .user(userA)
                .title("가입 인사")
                .content("반갑습니다.")
                .category(Category.IDOL)
                .build();
        postRepository.save(post);

        PostLikeCount postLikeCount = PostLikeCount.builder()
                .post(post)
                .build();
        CommentCount commentCount = CommentCount.builder()
                .post(post)
                .build();
        postLikeCountRepository.save(postLikeCount);
        commentCountRepository.save(commentCount);

        Comment rootComment = Comment.builder()
                .post(post)
                .user(userA)
                .content("부모댓글")
                .path("00001")
                .build();
        commentRepository.save(rootComment);

        Comment childComment = Comment.builder()
                .post(post)
                .user(userA)
                .content("자식댓글")
                .path("0000100001")
                .build();
        commentRepository.save(childComment);

        // Step 6-1: User 조회 테스트
        List<User> allUsers = userRepository.findAll();
        assertThat(allUsers).hasSize(2);

        userRepository.findByEmail("userA@test.com").ifPresent(u -> {
            assertThat(u.getNickname()).isEqualTo("철수");
        });

        userRepository.findByUsername("userA").ifPresent(u -> {
            assertThat(u.getNickname()).isEqualTo("철수");
        });

        // Step 6-2: Archive 및 관계 조회
        archiveRepository.findById(archive.getId()).ifPresent(a -> {
            assertThat(a.getTitle()).isEqualTo("철수의 덕질 아카이브");
            assertThat(a.getUser().getNickname()).isEqualTo("철수");

            archiveLikeCountRepository.findById(a.getId()).ifPresent(count -> {
                assertThat(count.getLikeCount()).isNotNull();
            });

            List<ArchiveFileMap> fileMaps = archiveFileMapRepository.findAll().stream()
                    .filter(fm -> fm.getArchive().getId().equals(a.getId()))
                    .collect(Collectors.toList());
            assertThat(fileMaps).hasSize(2);
        });

        // Step 6-3: FriendMap 양방향 조회
        List<FriendMap> friendMaps = friendMapRepository.findAll();
        assertThat(friendMaps).hasSize(2);

        // Step 6-4: Event 및 Hashtag M:N 관계 조회
        eventRepository.findById(event.getId()).ifPresent(e -> {
            assertThat(e.getTitle()).isEqualTo("야구 직관");

            sportRecordRepository.findById(e.getId()).ifPresent(sr -> {
                assertThat(sr.getTeam1()).isEqualTo("LG");
                assertThat(sr.getTeam2()).isEqualTo("KIA");
            });

            List<EventHashtagMap> hashtagMaps = eventHashtagMapRepository.findAll().stream()
                    .filter(ehm -> ehm.getEvent().getId().equals(e.getId()))
                    .collect(Collectors.toList());
            assertThat(hashtagMaps).hasSize(2);
        });

        // Step 6-5: Diary 계층 구조 조회
        diaryBookRepository.findById(diaryBook.getId()).ifPresent(db -> {
            assertThat(db.getTitle()).isEqualTo("2024 일기장");

            List<Diary> diaries = diaryRepository.findAll().stream()
                    .filter(d -> d.getDiaryBook().getId().equals(db.getId()))
                    .collect(Collectors.toList());
            assertThat(diaries).hasSize(1);
        });

        // Step 6-6: Post 및 Comment 조회
        postRepository.findById(post.getId()).ifPresent(p -> {
            assertThat(p.getTitle()).isEqualTo("가입 인사");

            postLikeCountRepository.findById(p.getId()).ifPresent(plc -> {
                assertThat(plc.getLikeCount()).isNotNull();
            });

            commentCountRepository.findById(p.getId()).ifPresent(cc -> {
                assertThat(cc.getCount()).isNotNull();
            });

            List<Comment> comments = commentRepository.findAll().stream()
                    .filter(c -> c.getPost().getId().equals(p.getId()))
                    .collect(Collectors.toList());
            assertThat(comments).hasSize(2);
        });

        // Step 6-7: File 조회
        List<File> allFiles = fileRepository.findAll();
        assertThat(allFiles.size()).isGreaterThanOrEqualTo(3);
    }

    // =========================================================================
    // 통합 테스트: 전체 플로우
    // =========================================================================
    @Test
    @DisplayName("통합 테스트: 전체 엔티티 관계 검증")
    @Transactional
    void testCompleteEntityRelationships() {
        // 1. User & FriendMap
        User userA = User.builder()
                .email("userA@test.com")
                .username("userA")
                .nickname("철수")
                .build();
        User userB = User.builder()
                .email("userB@test.com")
                .username("userB")
                .nickname("영희")
                .build();
        userRepository.saveAll(List.of(userA, userB));

        FriendMap map1 = FriendMap.builder()
                .user(userA)
                .friend(userB)
                .requestedBy(userA)
                .friendStatus(FriendStatus.ACCEPTED)
                .build();
        FriendMap map2 = FriendMap.builder()
                .user(userB)
                .friend(userA)
                .requestedBy(userA)
                .friendStatus(FriendStatus.ACCEPTED)
                .build();
        friendMapRepository.saveAll(List.of(map1, map2));

        // 2. Archive & File
        Archive archive = Archive.builder()
                .title("철수의 덕질 아카이브")
                .user(userA)
                .visibility(Visibility.PUBLIC)
                .build();
        archiveRepository.save(archive);

        ArchiveLikeCount likeCount = ArchiveLikeCount.builder()
                .archive(archive)
                .build();
        archiveLikeCountRepository.save(likeCount);
        assertThat(likeCount.getArchiveId()).isEqualTo(archive.getId());

        // 3. Event & Hashtag
        Event event = Event.builder()
                .archive(archive)
                .title("야구 직관")
                .date(LocalDateTime.now())
                .hasTime(false)
                .isSportType(true)
                .color("#FFFFFF")
                .build();
        eventRepository.save(event);

        SportRecord record = SportRecord.builder()
                .event(event)
                .team1("LG")
                .score1(5)
                .team2("KIA")
                .score2(3)
                .build();
        sportRecordRepository.save(record);
        assertThat(record.getEventId()).isEqualTo(event.getId());

        // 4. Diary
        DiaryBook diaryBook = DiaryBook.builder()
                .archive(archive)
                .title("2024 일기장")
                .build();
        diaryBookRepository.save(diaryBook);

        // 5. Post & Comment
        Post post = Post.builder()
                .user(userA)
                .title("가입 인사")
                .content("반갑습니다.")
                .category(Category.IDOL)
                .build();
        postRepository.save(post);

        PostLikeCount postLikeCount = PostLikeCount.builder()
                .post(post)
                .build();
        postLikeCountRepository.save(postLikeCount);
        assertThat(postLikeCount.getPostId()).isEqualTo(post.getId());

        // 모든 관계가 정상적으로 저장되었는지 검증
        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(friendMapRepository.count()).isEqualTo(2);
        assertThat(archiveRepository.count()).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(diaryBookRepository.count()).isEqualTo(1);
        assertThat(postRepository.count()).isEqualTo(1);
    }

    // --- Helper Methods ---

    private File createFile(String filename, MediaType type) {
        return File.builder()
                .s3ObjectKey("uuid-" + filename)
                .filename(filename)
                .filePath("http://mock-s3/" + filename)
                .fileSize(100L)
                .mediaType(type)
                .build();
    }
}