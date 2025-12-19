// package com.depth.deokive.domain.gallery.repository;
//
// import com.depth.deokive.domain.file.entity.enums.MediaRole;
// import com.querydsl.core.types.Projections;
// import com.querydsl.jpa.impl.JPAQueryFactory;
// import lombok.RequiredArgsConstructor;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.PageImpl;
// import org.springframework.data.domain.Pageable;
//
// import java.util.List;
//
// import static com.depth.deokive.domain.gallery.entity.QGallery.gallery;
// import static com.depth.deokive.domain.gallery.entity.QGalleryFileMap.galleryFileMap;
// import static com.depth.deokive.domain.file.entity.QFile.file;
//
// @RequiredArgsConstructor
// public class GalleryQueryRepository {
//
//     private final JPAQueryFactory queryFactory;
//
//     public Page<GalleryResponseDto> searchGalleries(Long archiveId, Pageable pageable) {
//
//         // 1. 컨텐츠 조회
//         // Gallery -> GalleryFileMap -> File 순으로 조인(N+1 문제를 해결하기 위해서)
//         List<GalleryResponseDto> content = queryFactory
//                 .select(Projections.constructor(GalleryResponseDto.class,
//                         gallery.id,
//                         gallery.title,
//                         file.filePath,
//                         gallery.createdAt
//                 ))
//                 .from(gallery)
//                 .leftJoin(galleryFileMap).on(
//                         galleryFileMap.gallery.id.eq(gallery.id)
//                                 .and(galleryFileMap.mediaRole.eq(MediaRole.PREVIEW)) // 대표 이미지만
//                 )
//                 .leftJoin(file).on(galleryFileMap.file.id.eq(file.id))
//                 .where(
//                         gallery.archive.id.eq(archiveId) // 특정 아카이브에서 조회
//                 )
//                 .orderBy(gallery.createdAt.desc()) // 최신 순으로
//                 .offset(pageable.getOffset())
//                 .limit(pageable.getPageSize())
//                 .fetch();
//
//         // 2. 카운트 쿼리
//         Long total = queryFactory
//                 .select(gallery.count())
//                 .from(gallery)
//                 .where(gallery.archive.id.eq(archiveId))
//                 .fetchOne();
//
//         return new PageImpl<>(content, pageable, total != null ? total : 0L);
//     }
// }
//
