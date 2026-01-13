package com.depth.deokive.domain.comment.service;

import com.depth.deokive.domain.comment.dto.CommentDto;
import com.depth.deokive.domain.comment.entity.Comment;
import com.depth.deokive.domain.comment.repository.CommentCountRepository;
import com.depth.deokive.domain.comment.repository.CommentQueryRepository;
import com.depth.deokive.domain.comment.repository.CommentRepository;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentQueryRepository commentQueryRepository;
    private final CommentCountRepository commentCountRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * 댓글 생성
     */
    @Transactional
    public void createComment(UserPrincipal userPrincipal, CommentDto.Request request) {
        User user = userRepository.getReferenceById(userPrincipal.getUserId());

        // Post 조회
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new RestException(ErrorCode.POST_NOT_FOUND));

        Comment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RestException(ErrorCode.COMMENT_NOT_FOUND));

            // 부모 댓글이 같은 게시글인지
            if (!parent.getPost().getId().equals(post.getId())) {
                throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST);
            }
            // 2-Depth 제한
            if (parent.getParent() != null) {
                throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST);
            }
        }

        Comment comment = Comment.builder()
                .user(user)
                .post(post)
                .content(request.getContent())
                .parent(parent)
                .build();

        commentRepository.save(comment);

        // 댓글 수 증가
        commentCountRepository.increaseCount(post.getId());
    }

    /**
     * 댓글 조회
     */
    @Transactional
    public Slice<CommentDto.Response> getComments(Long postId, Long lastCommentId, Pageable pageable, Long currentUserId) {
        if (!postRepository.existsById(postId)) {
            throw new RestException(ErrorCode.POST_NOT_FOUND);
        }

        // SEQ 1. Repository에서 Slice<Entity> 조회
        Slice<Comment> commentSlice = commentQueryRepository.findAllByPostId(postId, lastCommentId, pageable);

        // SEQ 2. 조회된 Entity 리스트를 계층형 DTO 구조로 변환
        List<CommentDto.Response> responseList = convertToHierarchy(commentSlice.getContent(), currentUserId);

        // 3. 변환된 DTO 리스트와 Slice 정보를 합쳐서 반환
        return new SliceImpl<>(responseList, pageable, commentSlice.hasNext());
    }

    /**
     * 댓글 삭제
     */
    @Transactional
    public void deleteComment(UserPrincipal userPrincipal, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RestException(ErrorCode.COMMENT_NOT_FOUND));

        // SEQ 1. 작성자인지 확인
        if (!comment.getUser().getId().equals(userPrincipal.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }

        // SEQ 2. 댓글 수 감소
        long deletedCount = 1 + comment.getChildren().size();

        // SEQ 3. 댓글 삭제
        commentRepository.delete(comment);

        // SER 4. 게시글 댓글 수 감소
        commentCountRepository.decreaseCount(comment.getPost().getId(), deletedCount);
    }

    // --- Helper Methods ---
    private List<CommentDto.Response> convertToHierarchy(List<Comment> comments, Long currentUserId) {
        List<CommentDto.Response> result = new ArrayList<>();
        Map<Long, CommentDto.Response> map = new HashMap<>();

        // SEQ 1. DTO 변환 및 Map 저장
        comments.forEach(c -> {
            CommentDto.Response dto = CommentDto.Response.from(c, currentUserId);
            map.put(dto.getCommentId(), dto);
        });

        // SEQ 2. 부모-자식 연결
        comments.forEach(c -> {
            CommentDto.Response dto = map.get(c.getId());
            if (c.getParent() != null) {
                CommentDto.Response parentDto = map.get(c.getParent().getId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                }
            } else {
                result.add(dto);
            }
        });

        return result;
    }
}