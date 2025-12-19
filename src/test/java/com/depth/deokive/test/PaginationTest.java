package com.depth.deokive.test;

import com.depth.deokive.common.init.DataInitializer;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class PaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 1. 더미 데이터 생성 (User 1명, Archive 30개)
        dataInitializer.initDummyData();

        // 2. 로그인 처리
        // DataInitializer에서 만든 이메일로 유저 조회
        User user = userRepository.findByEmail("test@deokive.com")
                .orElseThrow(() -> new RuntimeException("테스트용 유저를 찾을 수 없습니다. DataInitializer를 확인해주세요."));

        // UserPrincipal.from() 메서드 사용
        UserPrincipal principal = UserPrincipal.from(user);

        // SecurityContext에 인증 객체 주입
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("내 아카이브 페이지네이션 - 0페이지(5개) 조회 성공")
    @WithMockUser(username = "test@deokive.com", roles = "USER")
    void getMyArchivesTest() throws Exception {
        mockMvc.perform(get("/api/v1/archives/me")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(30)) // 전체 30개
                .andExpect(jsonPath("$.page.totalPages").value(6)) // 총 6페이지
                .andExpect(jsonPath("$.page.pageNumber").value(0)) // 현재 0페이지
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.content.length()").value(5)) // 데이터 5개
                .andExpect(jsonPath("$.content[0].title").exists()) // 제목 필드 확인
                .andDo(print());
    }
}