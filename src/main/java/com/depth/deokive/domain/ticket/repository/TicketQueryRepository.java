package com.depth.deokive.domain.ticket.repository;

import com.depth.deokive.domain.ticket.dto.TicketDto;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static com.depth.deokive.domain.ticket.entity.QTicket.ticket;

@Repository
@RequiredArgsConstructor
public class TicketQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<TicketDto.TicketPageResponse> searchTicketsByBook(Long ticketBookId, Pageable pageable) {

        // SEQ 1. 커버링 인덱스 활용
        List<Long> ids = queryFactory
                .select(ticket.id)
                .from(ticket)
                .where(ticket.ticketBook.id.eq(ticketBookId))
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // SEQ 2. 데이터 조회
        List<TicketDto.TicketPageResponse> content = new ArrayList<>();

        if (!ids.isEmpty()) {
            content = queryFactory
                    .select(Projections.constructor(TicketDto.TicketPageResponse.class,
                            ticket.id,
                            ticket.title,
                            ticket.date,
                            ticket.seat,
                            ticket.location,
                            ticket.casting,
                            ticket.createdAt,
                            ticket.lastModifiedAt,
                            ticket.originalKey
                    ))
                    .from(ticket)
                    .where(ticket.id.in(ids))
                    .orderBy(getOrderSpecifiers(pageable))
                    .fetch();
        }

        // SEQ 3. Count Query
        JPAQuery<Long> countQuery = queryFactory
                .select(ticket.count())
                .from(ticket)
                .where(ticket.ticketBook.id.eq(ticketBookId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier<?>[]{new OrderSpecifier<>(Order.DESC, ticket.createdAt)};
        }

        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            OrderSpecifier<?> orderSpecifier = switch (order.getProperty()) {
                case "createdAt" -> new OrderSpecifier<>(direction, ticket.createdAt);
                case "date" -> new OrderSpecifier<>(direction, ticket.date);
                default -> null;
            };
            if (orderSpecifier != null) orders.add(orderSpecifier);
        }

        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, ticket.createdAt));
        }

        orders.add(new OrderSpecifier<>(Order.DESC, ticket.id));

        return orders.toArray(new OrderSpecifier[0]);
    }
}