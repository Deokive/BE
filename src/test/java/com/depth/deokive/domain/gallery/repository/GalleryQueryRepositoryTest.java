// package com.depth.deokive.domain.gallery.repository;
//
// import lombok.extern.slf4j.Slf4j;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.PageRequest;
// import org.springframework.test.context.ActiveProfiles;
// import org.springframework.util.StopWatch;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// @Slf4j
// @SpringBootTest
// @ActiveProfiles("dev") // 로컬 DB에 연결하기 위함
// public class GalleryQueryRepositoryTest {
//
//     @Autowired
//     private GalleryRepository galleryRepository;
//
//     // 1. Gallery 페이지네이션 Test
//     @Test
//     @DisplayName(" 갤러리 N+1 문제 검증")
//     void testGalleryNPlusOne() {
//         Long archiveId = 1L;
//         PageRequest pageRequest = PageRequest.of(0, 5);
//
//         log.info("[Gallery] N+1 문제 검증 시작");
//         long startTime = System.currentTimeMillis();
//
//         Page<GalleryResponseDto> result = galleryRepository.searchGalleries(archiveId, pageRequest);
//
//         long endTime = System.currentTimeMillis();
//         log.info("[Gallery] N+1 문제 검증 종료");
//
//         assertThat(result.getContent()).hasSize(5);
//
//         assertThat(result.getContent().get(0).getTitle()).isNotNull();
//         assertThat(result.getContent().get(0).getThumbnailUrl()).isNotNull();
//
//         log.info("첫 페이지(5개) 조회 시간: {}ms", (endTime - startTime));
//         log.info("콘솔 로그에서 'Hibernate:'로 시작하는 쿼리가 [Count 1개 + Select 1개] 총 2개만 나가야 정상.");
//     }
//
//     @Test
//     @DisplayName("갤러리: 대용량 데이터 성능 검증 (50만 번째 데이터 조회)")
//     void testGalleryDeepPagination() {
//
//         Long archiveId = 1L;
//         int page = 100000;
//         PageRequest pageRequest = PageRequest.of(page, 5);
//
//
//         log.info("[Gallery] 성능 검증 시작 (Offset: 500,000)");
//         StopWatch stopWatch = new StopWatch();
//         stopWatch.start();
//
//         Page<GalleryResponseDto> result = galleryRepository.searchGalleries(archiveId, pageRequest);
//
//         stopWatch.stop();
//         log.info("[Gallery] 성능 검증 종료");
//
//         assertThat(result.getContent()).hasSize(5);
//
//         log.info("⏱50만 번째 페이지 조회 시간: {}ms", stopWatch.getTotalTimeMillis());
//     }
// }
