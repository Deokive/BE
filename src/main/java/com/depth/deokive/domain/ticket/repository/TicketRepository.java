package com.depth.deokive.domain.ticket.repository;

import com.depth.deokive.domain.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.file WHERE t.id = :id")
    Optional<Ticket> findByIdWithFile(@Param("id") Long id);
}