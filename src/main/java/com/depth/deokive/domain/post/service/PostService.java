package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostFileMap;
import com.depth.deokive.domain.post.repository.PostFileMapRepository;
import com.depth.deokive.domain.post.repository.PostQueryRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
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
    private final FileService fileService;
    private final PostQueryRepository postQueryRepository;

    @Transactional
    public PostDto.Response createPost(UserPrincipal userPrincipal, PostDto.CreateRequest request) {
        // SEQ 1. 작성자 조회
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        // SEQ 2. 게시글 저장
        Post post = PostDto.CreateRequest.from(request, foundUser);
        postRepository.save(post);

        // SEQ 3. 파일 연결
        List<PostFileMap> maps = connectFilesToPost(post, request.getFiles(), userPrincipal.getUserId());

        // SEQ 4. Response
        return PostDto.Response.of(post, maps);
    }

    @Transactional(readOnly=true)
    public PostDto.Response getPost(Long postId) {
        // SEQ 1. 게시글 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        // SEQ 2. 해당 게시글의 파일 매핑 조회
        List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(postId);

        // SEQ 4. 상세 조회 시 조회수 증가 (동시성 이슈 고려 시 Redis 권장하나 일단 DB update)
        post.increaseViewCount();

        // SEQ 5. Return
        return PostDto.Response.of(post, maps);
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

        // SEQ 4. 기존 파일 매핑 삭제 후 재생성 (🧐 파일의 순서, 파일 자체, 미디어 역할 등이 변경될 수 있음 -> 일괄 삭제 후 재매핑이 나음)
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

        // SEQ 6. Return
        return PostDto.Response.of(post, maps);
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

        // SEQ 4. 게시글 삭제
        postRepository.delete(post);
    }

    @ExecutionTime
    @Transactional(readOnly = true)
    public PostDto.PageListResponse getPosts(PostDto.PostPageRequest request) {
        // TODO: Check -> QueryDSL을 사용한 No-Offset Optimization (Category Filter 적용)
        Page<PostDto.PostPageResponse> page = postQueryRepository.searchPostFeed(
                request.getCategory(),
                request.toPageable()
        );

        String title = (request.getCategory() != null)
                ? request.getCategory().name() + " 게시판"
                : "전체 게시판";

        return PostDto.PageListResponse.of(title, page);
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
        String thumbnailPath = savedMaps.stream()
                .filter(map -> map.getMediaRole() == MediaRole.PREVIEW)
                .findFirst()
                .map(map -> map.getFile().getFilePath()) // 경로 추출
                .orElseGet(() -> savedMaps.stream()
                        .min(Comparator.comparingInt(PostFileMap::getSequence))
                        .map(map -> map.getFile().getFilePath()) // 경로 추출
                        .orElse(null));

        post.updateThumbnail(ThumbnailUtils.getMediumThumbnailUrl(thumbnailPath));

        return savedMaps;
    }

    private void validateOwner(Post post, UserPrincipal userPrincipal) {
        if (!post.getUser().getId().equals(userPrincipal.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 권한 없음 예외
        }
    }
}


