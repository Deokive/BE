package com.depth.deokive.test;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired EntityManager em;

    @Test
    @DisplayName("1. 친구 아카이브 조회: PUBLIC/RESTRICTED만 보이고 PRIVATE은 숨겨진다.")
    void getFriendArchives_VisibilityCheck() {
        // given
        User me = createUser("me");
        User friend = createUser("friend");
        createFriendship(me, friend); // 친구 관계 맺기

        // 친구 아카이브 3개 생성 (각각 다른 공개범위)
        createArchive(friend, Visibility.PUBLIC, "Public Item");
        createArchive(friend, Visibility.RESTRICTED, "Restricted Item");
        createArchive(friend, Visibility.PRIVATE, "Private Item");

        em.flush(); em.clear(); // 영속성 컨텍스트 비우기 (DB 조회 및 N+1 확인용)

        // when
        ArchiveDto.PageListResponse response = archiveService.getFriendArchives(
                me.getId(), friend.getId(), PageRequest.of(0, 10)
        );

        // then
        // PRIVATE이 빠졌으므로 총 2개여야 함
        assertThat(response.getContent()).hasSize(2);

        // 내용물 검증
        assertThat(response.getContent())
                .extracting("visibility")
                .containsExactlyInAnyOrder(Visibility.PUBLIC, Visibility.RESTRICTED)
                .doesNotContain(Visibility.PRIVATE);
    }

    @Test
    @DisplayName("2. 친구 아카이브 조회 실패: 친구가 아니면 AUTH_FORBIDDEN 예외")
    void getFriendArchives_NotFriend_Fail() {
        // given
        User me = createUser("me");
        User stranger = createUser("stranger"); // 친구 아님

        // when & then
        assertThatThrownBy(() ->
                archiveService.getFriendArchives(me.getId(), stranger.getId(), PageRequest.of(0, 10))
        )
                .isInstanceOf(RestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("3. 내 아카이브 조회: PRIVATE을 포함한 모든 공개범위가 조회된다.")
    void getMyArchives_AllVisible() {
        // given
        User me = createUser("me");
        createArchive(me, Visibility.PRIVATE, "My Private"); // 내 비공개 글

        em.flush(); em.clear();

        // when
        ArchiveDto.PageListResponse response = archiveService.getMyArchives(
                me.getId(), PageRequest.of(0, 10)
        );

        // then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("My Private");
    }

    // --- Helper Methods (테스트 내 데이터 생성용) ---
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

    private void createArchive(User user, Visibility v, String title) {
        archiveRepository.save(Archive.builder()
                .user(user).title(title).visibility(v).badge(Badge.NEWBIE).build());
    }
}