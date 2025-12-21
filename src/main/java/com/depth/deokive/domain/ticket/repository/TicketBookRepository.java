package com.depth.deokive.domain.ticket.repository;

import com.depth.deokive.domain.ticket.entity.TicketBook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketBookRepository extends JpaRepository<TicketBook, Long> {
}
