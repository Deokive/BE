package com.depth.deokive.domain.comment.repository;

import com.depth.deokive.domain.comment.entity.Comment;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;


import static com.depth.deokive.domain.comment.entity.QComment.comment;
import static com.depth.deokive.domain.user.entity.QUser.user;

@Repository
@RequiredArgsConstructor
public class CommentQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 특정 게시글의 댓글조회
     */
    public Slice<Comment> findAllByPostId(Long postId, Long lastCommentId, Pageable pageable) {

        // SEQ 1. 부모 ID 조회
        List<Long> parentIds = queryFactory
                .select(comment.id)
                .from(comment)
                .where(
                        comment.post.id.eq(postId),
                        comment.parent.isNull(),
                        gtCommentId(lastCommentId)
                )
                .orderBy(comment.id.asc())
                .limit(pageable.getPageSize() + 1)
                .fetch();

        if (parentIds.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }

        boolean hasNext = false;
        if (parentIds.size() > pageable.getPageSize()) {
            hasNext = true;
            parentIds.remove(parentIds.size() - 1);
        }

        // SEQ 2. 부모, 자식 조회
        List<Comment> comments = queryFactory
                .selectFrom(comment)
                .leftJoin(comment.user, user)
                .fetchJoin()
                .where(
                        comment.parent.id.in(parentIds)
                                .or(comment.id.in(parentIds))
                )
                .orderBy(
                        comment.parent.id.coalesce(comment.id).asc(),
                        comment.createdAt.asc()
                )
                .fetch();

        return new SliceImpl<>(comments, pageable, hasNext);
    }

    private BooleanExpression gtCommentId(Long lastCommentId) {
        return lastCommentId == null ? null : comment.id.gt(lastCommentId);
    }
}
