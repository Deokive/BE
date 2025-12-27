package com.depth.deokive.domain.ticket.repository;

import com.depth.deokive.domain.ticket.entity.TicketBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketBookRepository extends JpaRepository<TicketBook, Long> {
    @Query("SELECT tb FROM TicketBook tb " +
            "JOIN FETCH tb.archive a " +
            "JOIN FETCH a.user u " +
            "WHERE tb.id = :id")
    Optional<TicketBook> findByIdWithArchiveAndUser(@Param("id") Long id);
}
