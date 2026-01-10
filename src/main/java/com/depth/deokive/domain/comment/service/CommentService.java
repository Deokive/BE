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
    @Transactional(readOnly = true)
    public List<CommentDto.Response> getComments(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new RestException(ErrorCode.POST_NOT_FOUND);
        }

        List<Comment> comments = commentQueryRepository.findAllByPostId(postId);

        List<CommentDto.Response> result = new ArrayList<>();
        Map<Long, CommentDto.Response> map = new HashMap<>();

        // SEQ 1. 모든 댓글을 DTO로 변환하여 Map에 저장
        comments.forEach(c -> {
            CommentDto.Response dto = CommentDto.Response.from(c);
            map.put(dto.getCommentId(), dto);
        });

        // SQE 2. 부모-자식 관계 연결
        comments.forEach(c -> {
            CommentDto.Response dto = map.get(c.getId());
            if (c.getParent() != null) {
                CommentDto.Response parentDto = map.get(c.getParent().getId());
                // 부모가 Map에 존재할 때만 자식으로 추가
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                }
            } else {
                // 최상위 댓글
                result.add(dto);
            }
        });

        return result;
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
        commentCountRepository.decreaseCount(comment.getPost().getId());

        if (comment.getChildren().size() > 0) {
            // 자식이 있으면 Soft Delete
            comment.changeDeletedStatus(true);
        } else {
            // 자식이 없으면 삭제 가능한 부모 찾아서 Hard Delete
            commentRepository.delete(getDeletableAncestorComment(comment));
        }
    }

    private Comment getDeletableAncestorComment(Comment comment) {
        Comment parent = comment.getParent();
        if (parent != null && parent.isDeleted() && parent.getChildren().size() == 1) {
            return getDeletableAncestorComment(parent);
        }
        return comment;
    }
}