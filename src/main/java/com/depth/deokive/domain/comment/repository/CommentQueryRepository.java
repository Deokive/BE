package com.depth.deokive.domain.comment.repository;

import com.depth.deokive.domain.comment.entity.Comment;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public List<Comment> findAllByPostId(Long postId) {
        return queryFactory
                .selectFrom(comment)
                .leftJoin(comment.user, user).fetchJoin()
                .where(comment.post.id.eq(postId))
                .orderBy(
                        // 부모(NULL 먼저) -> 자식
                        comment.parent.id.asc().nullsFirst(),
                        // 시간순 정렬
                        comment.createdAt.asc()
                )
                .fetch();
    }
}
