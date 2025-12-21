package com.depth.deokive.test;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.ArchiveLikeCount;
import com.depth.deokive.domain.archive.entity.ArchiveViewCount;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.archive.repository.ArchiveLikeCountRepository;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.archive.repository.ArchiveViewCountRepository;
import com.depth.deokive.domain.archive.service.ArchiveService;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PaginationTest {

    @Autowired ArchiveService archiveService;
    @Autowired UserRepository userRepository;
    @Autowired ArchiveRepository archiveRepository;
    @Autowired FriendMapRepository friendMapRepository;
    @Autowired ArchiveLikeCountRepository archiveLikeCountRepository;
    @Autowired ArchiveViewCountRepository archiveViewCountRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // [삭제] LOG 함수 Alias 설정 삭제 (이제 LN을 쓰므로 필요 없음)

        // 데이터 초기화
        archiveViewCountRepository.deleteAll();
        archiveLikeCountRepository.deleteAll();
        archiveRepository.deleteAll();
    }

    @Test
    @DisplayName("1. 내 아카이브 조회")
    void getMyArchives_AllVisible() {
        User me = createUser("me");
        createArchive(me, Visibility.PRIVATE, "My Private");

        em.flush(); em.clear();

        ArchiveDto.PageListResponse response = archiveService.getMyArchives(
                me.getId(), PageRequest.of(0, 10)
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("My Private");
    }

    @Test
    @DisplayName("2. 친구 아카이브 조회")
    void getFriendArchives_VisibilityCheck() {
        User me = createUser("me");
        User friend = createUser("friend");
        createFriendship(me, friend);

        createArchive(friend, Visibility.PUBLIC, "Public Item");
        createArchive(friend, Visibility.RESTRICTED, "Restricted Item");
        createArchive(friend, Visibility.PRIVATE, "Private Item");

        em.flush(); em.clear();

        ArchiveDto.PageListResponse response = archiveService.getFriendArchives(
                me.getId(), friend.getId(), PageRequest.of(0, 10)
        );

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent())
                .extracting("visibility")
                .containsExactlyInAnyOrder(Visibility.PUBLIC, Visibility.RESTRICTED);
    }

    @Test
    @DisplayName("3. 친구 아카이브 조회 실패")
    void getFriendArchives_NotFriend_Fail() {
        User me = createUser("me");
        User stranger = createUser("stranger");

        assertThatThrownBy(() ->
                archiveService.getFriendArchives(me.getId(), stranger.getId(), PageRequest.of(0, 10))
        )
                .isInstanceOf(RestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("4. 핫피드 조회: 알고리즘 검증")
    void getHotArchives_AlgorithmRanking() {
        User user = createUser("poster");

        // [수정] 생성할 때 Visibility.PUBLIC 넘기기
        Archive oldArchive = createArchiveWithDate(user, "Old Legend", Visibility.PUBLIC, LocalDateTime.now().minusDays(7));
        setArchiveStats(oldArchive, 2L, 2L);

        // [수정] 생성할 때 Visibility.PUBLIC 넘기기
        Archive newArchive = createArchiveWithDate(user, "Rising Star", Visibility.PUBLIC, LocalDateTime.now().minusHours(1));
        setArchiveStats(newArchive, 3L, 3L);

        // [수정] 생성할 때 Visibility.PRIVATE 넘기기 (별도 update 호출 불필요)
        Archive privateArchive = createArchiveWithDate(user, "Private Hit", Visibility.PRIVATE, LocalDateTime.now());
        setArchiveStats(privateArchive, 9999L, 99999L);
        // privateArchive.updateVisibility(Visibility.PRIVATE); // <--- 이 줄 삭제!

        // [중요] JPA 캐시 비우기
        em.flush();
        em.clear();

        // when
        ArchiveDto.PageListResponse response = archiveService.getHotArchives(PageRequest.of(0, 10));

        // then
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("Rising Star");
        assertThat(response.getContent().get(1).getTitle()).isEqualTo("Old Legend");
    }

    @Test
    @DisplayName("5. 페이지네이션 검증")
    void validatePageBounds_Fail() {
        User me = createUser("me");
        createArchive(me, Visibility.PUBLIC, "Item 1");

        assertThatThrownBy(() ->
                archiveService.getMyArchives(me.getId(), PageRequest.of(100, 10))
        )
                .isInstanceOf(RestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DB_DATA_NOT_FOUND);
    }

    // --- Helper Methods ---

    private User createUser(String name) {
        return userRepository.save(User.builder()
                .username(name).nickname(name).email(name+"@t.com")
                .role(Role.USER).isEmailVerified(true).password("pw").build());
    }

    private void createFriendship(User u1, User u2) {
        friendMapRepository.save(FriendMap.builder()
                .user(u1).friend(u2).requestedBy(u1)
                .friendStatus(FriendStatus.ACCEPTED)
                .acceptedAt(LocalDateTime.now()).build());
    }

    // saveAndFlush: 즉시 DB 반영
    private Archive createArchive(User user, Visibility v, String title) {
        return archiveRepository.saveAndFlush(Archive.builder()
                .user(user).title(title).visibility(v).badge(Badge.NEWBIE).build());
    }

    private Archive createArchiveWithDate(User user, String title, Visibility visibility, LocalDateTime time) {
        // 1. JPA로 저장 (여기서 visibility 설정을 바로 함)
        Archive archive = archiveRepository.saveAndFlush(Archive.builder()
                .user(user)
                .title(title)
                .visibility(visibility) // 파라미터로 받은 값 사용
                .badge(Badge.NEWBIE)
                .build());

        // 2. 시간 강제 변경
        jdbcTemplate.update("UPDATE ARCHIVE SET CREATED_AT = ? WHERE ID = ?",
                Timestamp.valueOf(time), archive.getId());

        return archive;
    }

    private void setArchiveStats(Archive archive, Long like, Long view) {
        archiveLikeCountRepository.saveAndFlush(ArchiveLikeCount.builder()
                .archive(archive).likeCount(like).build());
        archiveViewCountRepository.saveAndFlush(ArchiveViewCount.builder()
                .archive(archive).viewCount(view).build());
    }
}