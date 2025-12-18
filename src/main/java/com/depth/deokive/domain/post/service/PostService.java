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

        // SEQ 3. 파일 연결
        connectFilesToPost(post, request.getFiles());

        // SEQ 4. 해당 게시글의 파일 매핑 조회
        List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId());

        // SEQ 5. Return
        return PostDto.Response.of(post, maps);
    }

    @Transactional(readOnly=true)
    public PostDto.Response getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND)); // ErrorCode 필요

        return toResponse(post);
    }

    // ------ Helper Methods -------

    /** 게시글과 파일 매핑 연결 */
    private void connectFilesToPost(Post post, List<PostDto.AttachedFileRequest> fileRequests) {
        if (fileRequests == null || fileRequests.isEmpty()) { return; }

        for (PostDto.AttachedFileRequest fileReq : fileRequests) {
            File file = fileRepository.findById(fileReq.getFileId())
                    .orElseThrow(() -> new RestException(ErrorCode.FILE_NOT_FOUND));

            //  PostFileMap 생성
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


