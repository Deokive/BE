package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.enums.ViewLikeDomain;
import com.depth.deokive.common.service.LikeRedisService;
import com.depth.deokive.common.service.PaginationCountCacheService;
import com.depth.deokive.common.service.PaginationIdCacheService;
import com.depth.deokive.common.service.RedisViewService;
import com.depth.deokive.common.util.ClientUtils;
import com.depth.deokive.common.util.PageUtils;
import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.comment.repository.CommentRepository;
import com.depth.deokive.domain.comment.service.CommentCountRedisService;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.*;
import com.depth.deokive.domain.post.repository.*;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostFileMapRepository postFileMapRepository;
    private final PostLikeRepository postLikeRepository;
    private final FileService fileService;
    private final PostQueryRepository postQueryRepository;
    private final RedisViewService redisViewService;
    private final PostStatsRepository postStatsRepository;
    private final LikeRedisService likeRedisService;
    private final CommentCountRedisService commentCountRedisService;
    private final CommentRepository commentRepository;
    private final PaginationCountCacheService paginationCountCacheService;
    private final PaginationIdCacheService paginationIdCacheService;

    @Transactional
    public PostDto.Response createPost(UserPrincipal userPrincipal, PostDto.CreateRequest request) {
        // SEQ 1. 작성자 조회
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        // SEQ 2. 게시글 저장
        Post post = PostDto.CreateRequest.from(request, foundUser);
        postRepository.save(post);

        // SEQ 3. 통계 엔티티 생성 및 저장 (Sync OK)
        PostStats stats = PostStats.create(post);
        postStatsRepository.save(stats);

        // SEQ 4. 파일 연결
        List<PostFileMap> maps = connectFilesToPost(post, request.getFiles(), userPrincipal.getUserId());

        // SEQ 5. 페이지네이션 캐시 무효화
        paginationCountCacheService.evictByPrefix("post");
        paginationIdCacheService.evictByPrefix("post");

        // SEQ 6. Response (생성 시점에는 좋아요 없음)
        return PostDto.Response.of(post, stats, maps, false);
    }

    @Transactional
    public PostDto.Response getPost(UserPrincipal userPrincipal, Long postId, HttpServletRequest request) {
        // SEQ 1. 게시글 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        // SEQ 2. 통계 정보 조회 - ViewCount, HotScore용
        PostStats stats = postStatsRepository.findById(postId)
                .orElseGet(() -> {
                    PostStats newStats = PostStats.create(post);
                    postStatsRepository.save(newStats);
                    return newStats;
                });

        // SEQ 3. 실시간 좋아요 수 조회
        Long realTimeLikeCount = likeRedisService.getCount(
                ViewLikeDomain.POST,
                postId,
                () -> postLikeRepository.findAllUserIdsByPostId(postId),
                () -> {}
        );

        // SEQ 4. 해당 게시글의 파일 매핑 조회
        List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(postId);

        // SEQ 5. Redis 조회수 증가 (Write Back)
        increaseViewCount(userPrincipal, postId, request);

        // SEQ 6. 좋아요 여부 조회
        Long viewerId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
        boolean isLiked = (viewerId != null) && likeRedisService.isLiked(
                ViewLikeDomain.POST,
                postId,
                viewerId,
                () -> postLikeRepository.findAllUserIdsByPostId(postId),
                () -> {}
        );

        // SEQ 7. Return
        return PostDto.Response.of(post, stats.getViewCount(), realTimeLikeCount, stats.getHotScore(), maps, isLiked);
    }

    @Transactional
    public PostDto.Response updatePost(UserPrincipal userPrincipal, Long postId, PostDto.UpdateRequest request) {
        // SEQ 1. 게시글 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        // SEQ 2. 작성자 검증
        validateOwner(post, userPrincipal);

        // SEQ 3. 게시글 정보 업데이트 (Dirty Checking)
        post.update(request);

        // SEQ 4. 카테고리가 변경되었다면 PostStats도 동기화 (커버링 인덱스용)
        if (request.getCategory() != null) {
            postStatsRepository.syncUpdateCategory(postId, request.getCategory());
            paginationIdCacheService.evictByPrefix("post");
        }

        // SEQ 5. 기존 파일 매핑 삭제 후 재생성 (🧐 파일의 순서, 파일 자체, 미디어 역할 등이 변경될 수 있음 -> 일괄 삭제 후 재매핑이 나음)
        List<PostFileMap> maps;

        // request.getFiles()가 null이면 파일 변경 없음.
        // 빈 리스트([])가 오면 모든 파일 삭제, 값이 있으면 교체.
        if (request.getFiles() != null) {
            postFileMapRepository.deleteAllByPostId(post.getId());
            maps = connectFilesToPost(post, request.getFiles(), userPrincipal.getUserId());
        } else {
            // 변경사항 없으면 기존 매핑 조회하여 반환
            maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(postId);
        }

        // SEQ 6. 통계 조회, 좋아요 여부 조회
        PostStats stats = postStatsRepository.findById(postId).orElse(PostStats.create(post));
        boolean isLiked = postLikeRepository.existsByPostIdAndUserId(postId, userPrincipal.getUserId());

        // SEQ 7. Return
        return PostDto.Response.of(post, stats, maps, isLiked);
    }

    @Transactional
    @ExecutionTime // 삭제 처리 시간 로깅 AOP (실제 JPA 처리와의 차이 확인 용도)
    public void deletePost(UserPrincipal userPrincipal, Long postId) {
        // SEQ 1. 게시글 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        // SEQ 2. 작성자 검증
        validateOwner(post, userPrincipal);

        // SEQ 3. 파일 매핑 해제: Cascade.REMOVE 의 N+1 문제 및 성능 이슈 -> 명시적 삭제: Bulk 처리 (Using JPQL)
        postFileMapRepository.deleteAllByPostId(postId);

        // SEQ 4. 좋아요 삭제
        postLikeRepository.deleteByPostId(postId);

        // SEQ 5. 통계 테이블 삭제 (@MapsId 사용으로 인해 deleteById 대신 커스텀 쿼리 사용)
        postStatsRepository.deleteByPostId(postId);

        // SEQ 6. 댓글 삭제 (Post FK 제약조건으로 인해 Post 삭제 전 필수)
        // 대댓글(자식) 먼저 삭제 → 부모 댓글 삭제 (FK 제약조건 순서)
        commentRepository.deleteRepliesByPostId(postId);
        commentRepository.deleteParentsByPostId(postId);

        // SEQ 7. 게시글 삭제
        postRepository.delete(post);

        // SEQ 8. 캐시 삭제 (좋아요 + 댓글 수 + 페이지네이션)
        likeRedisService.deleteLikeData(ViewLikeDomain.POST, postId);
        commentCountRedisService.deleteCache(postId);
        paginationCountCacheService.evictByPrefix("post");
        paginationIdCacheService.evictByPrefix("post");
    }

    @ExecutionTime
    @Transactional(readOnly = true)
    public PageDto.PageListResponse<PostDto.PostPageResponse> getPosts(PostDto.PostPageRequest request) {
        Page<PostDto.PostPageResponse> page = postQueryRepository.searchPostFeed(
                request.getCategory(),
                request.toPageable()
        );

        PageUtils.validatePageRange(page);

        String title;
        if ("hotScore".equals(request.getSort())) { title = "핫한 게시판"; }
        else if (request.getCategory() == null) { title = "전체 게시판"; }
        else { title = request.getCategory().name() + " 게시판"; }

        return PageDto.PageListResponse.of(title, page);
    }

    public PostDto.LikeResponse toggleLike(UserPrincipal userPrincipal, Long postId) {
        boolean isLiked = likeRedisService.toggleLike(
                ViewLikeDomain.POST,
                postId,
                userPrincipal.getUserId(),
                () -> postLikeRepository.findAllUserIdsByPostId(postId),
                () -> { // Validator: 필요할 때만 실행됨
                    if (!postRepository.existsById(postId)) {
                        throw new RestException(ErrorCode.POST_NOT_FOUND);
                    }
                }
        );

        Long realTimeLikeCount = likeRedisService.getCount(
                ViewLikeDomain.POST,
                postId,
                () -> postLikeRepository.findAllUserIdsByPostId(postId),
                () -> { // Validator: 필요할 때만 실행됨
                    if (!postRepository.existsById(postId)) {
                        throw new RestException(ErrorCode.POST_NOT_FOUND);
                    }
                }
        );

        return PostDto.LikeResponse.builder()
                .postId(postId)
                .isLiked(isLiked)
                .likeCount(realTimeLikeCount)
                .build();
    }

    // ------ Helper Methods -------

    // 파일 목록을 한 번에 조회하고 매핑 엔티티를 생성해서 일괄 저장 -> Repost 시 썸네일 추출을 위해 MediaRole(PREVIEW) 저장이 필수임
    private List<PostFileMap> connectFilesToPost(
            Post post,
            List<PostDto.AttachedFileRequest> fileRequests,
            Long userId
    ) {
        // SEQ 1. Null Check
        if (fileRequests == null || fileRequests.isEmpty()) {
            post.updateThumbnail(null); // 파일 없으면 썸네일도 제거
            return Collections.emptyList();
        }

        // SEQ 2. 요청된 File ID 추출
        List<Long> fileIds = fileRequests.stream()
                .map(PostDto.AttachedFileRequest::getFileId)
                .collect(Collectors.toList());

        long uniqueCount = fileIds.stream().distinct().count();
        if (fileIds.size() != uniqueCount) {
            throw new RestException(ErrorCode.FILE_NOT_FOUND);
        }

        // SEQ 3. File Entity Bulk Fetch
        List<File> files = fileService.validateFileOwners(fileIds, userId);

        // SEQ 4. Validate Files
        if (files.size() != fileIds.stream().distinct().count()) {
            throw new RestException(ErrorCode.FILE_NOT_FOUND);
        }

        // SEQ 5. List -> Map 변환 (조회 성능 O(1))
        Map<Long, File> fileMap = files.stream()
                .collect(Collectors.toMap(File::getId, Function.identity()));

        // SEQ 6. Create Mapping Entities (요청 순서 유지)
        List<PostFileMap> newMaps = fileRequests.stream()
                .map(req -> {
                    File file = fileMap.get(req.getFileId());
                    return PostFileMap.builder()
                            .post(post)
                            .file(file)
                            .mediaRole(req.getMediaRole())
                            .sequence(req.getSequence())
                            .build();
                })
                .collect(Collectors.toList());

        // SEQ 7. Bulk Insert
        List<PostFileMap> savedMaps = postFileMapRepository.saveAll(newMaps);

        // SEQ 8. 대표 썸네일 선정 로직
        // 1순위: MediaRole.PREVIEW
        // 2순위: Sequence (0번)
        String originalKey = savedMaps.stream()
                .filter(map -> map.getMediaRole() == MediaRole.PREVIEW)
                .findFirst()
                .map(map -> map.getFile().getS3ObjectKey())
                .orElseGet(() -> savedMaps.stream()
                        .min(Comparator.comparingInt(PostFileMap::getSequence))
                        .map(map -> map.getFile().getS3ObjectKey())
                        .orElse(null));

        // 2. 썸네일 Key로 변환 후 저장
        post.updateThumbnail(ThumbnailUtils.getMediumThumbnailKey(originalKey));

        return savedMaps;
    }

    private void validateOwner(Post post, UserPrincipal userPrincipal) {
        if (!post.getUser().getId().equals(userPrincipal.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 권한 없음 예외
        }
    }

    private void increaseViewCount(UserPrincipal userPrincipal, Long postId, HttpServletRequest request) {
        if (request == null) return; // Soft Fail

        Long viewerId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
        String clientIp = ClientUtils.getClientIp(request);

        redisViewService.incrementViewCount(ViewLikeDomain.POST, postId, viewerId, clientIp);
    }
}