package com.depth.deokive.domain.post.service;

import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostFileMap;
import com.depth.deokive.domain.post.repository.PostFileMapRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final PostFileMapRepository postFileMapRepository;

    @Transactional
    public PostDto.Response createPost(UserPrincipal userPrincipal, PostDto.Request request) {
        // SEQ 1. 작성자 조회
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        // SEQ 2. 게시글 저장
        Post post = PostDto.Request.from(request, foundUser);
        postRepository.save(post);

        // SEQ 3. 파일 연결
        connectFilesToPost(post, request.getFiles());

        // SEQ 4. 해당 게시글의 파일 매핑 조회
        List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId());

        // SEQ 5. Return
        return PostDto.Response.of(post, maps);
    }

    @Transactional(readOnly=true)
    public PostDto.Response getPost(Long postId) {
        // SEQ 1. 게시글 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        // SEQ 2. 해당 게시글의 파일 매핑 조회
        List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(postId);

        // SEQ 3. Return
        return PostDto.Response.of(post, maps);
    }

    @Transactional
    public PostDto.Response updatePost(UserPrincipal userPrincipal, Long postId, PostDto.Request request) {
        // SEQ 1. 게시글 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        // SEQ 2. 작성자 검증
        validateOwner(post, userPrincipal);

        // SEQ 3. 게시글 정보 업데이트 (Dirty Checking)
        post.update(request);

        // SEQ 4. 기존 파일 매핑 삭제 후 재생성 (🧐 파일의 순서, 파일 자체, 미디어 역할 등이 변경될 수 있음 -> 일괄 삭제 후 재매핑이 나음)
        postFileMapRepository.deleteAllByPostId(post.getId());
        connectFilesToPost(post, request.getFiles());

        // SEQ 5. 파일 매핑 조회
        List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(postId);

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

    // ------ Helper Methods -------
    private void connectFilesToPost(Post post, List<PostDto.AttachedFileRequest> fileRequests) {
        // SEQ 1. 파일 매핑이 없으면 종료
        if (fileRequests == null || fileRequests.isEmpty()) { return; }

        // SEQ 2. 파일 매핑 생성
        for (PostDto.AttachedFileRequest fileReq : fileRequests) {
            File file = fileRepository.findById(fileReq.getFileId())
                    .orElseThrow(() -> new RestException(ErrorCode.FILE_NOT_FOUND));

            PostFileMap map = PostFileMap.builder()
                    .post(post)
                    .file(file)
                    .mediaRole(fileReq.getMediaRole())
                    .sequence(fileReq.getSequence())
                    .build();

            postFileMapRepository.save(map);
        }
    }

    private void validateOwner(Post post, UserPrincipal userPrincipal) {
        if (!post.getUser().getId().equals(userPrincipal.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 권한 없음 예외
        }
    }
}


