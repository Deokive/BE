package com.depth.deokive.domain.ticket.service;

import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.ticket.dto.TicketDto;
import com.depth.deokive.domain.ticket.entity.Ticket;
import com.depth.deokive.domain.ticket.entity.TicketBook;
import com.depth.deokive.domain.ticket.repository.TicketBookRepository;
import com.depth.deokive.domain.ticket.repository.TicketRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketBookRepository ticketBookRepository;
    private final FileRepository fileRepository;

    @Transactional
    public TicketDto.Response createTicket(UserPrincipal userPrincipal, Long archiveId, TicketDto.Request request) {
        // SEQ 1. 티켓북 조회
        TicketBook ticketBook = ticketBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        validateOwner(ticketBook.getArchive().getUser().getId(), userPrincipal);

        // SEQ 2. 파일 조회 (있으면 찾고, 없으면 null)
        File file = null;
        if (request.getFileId() != null) {
            file = fileRepository.findById(request.getFileId())
                    .orElseThrow(() -> new RestException(ErrorCode.FILE_NOT_FOUND));
        }

        // SEQ 3. 저장 (Entity에 File 바로 꽂기)
        Ticket ticket = request.toEntity(ticketBook, file);
        ticketRepository.save(ticket);

        return TicketDto.Response.of(ticket);
    }

    @Transactional(readOnly = true)
    public TicketDto.Response getTicket(UserPrincipal userPrincipal, Long ticketId) {
        Ticket ticket = ticketRepository.findByIdWithFile(ticketId)
                .orElseThrow(() -> new RestException(ErrorCode.TICKET_NOT_FOUND));

        validateReadPermission(ticket, userPrincipal);

        return TicketDto.Response.of(ticket);
    }

    @Transactional
    public TicketDto.Response updateTicket(UserPrincipal userPrincipal, Long ticketId, TicketDto.Request request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RestException(ErrorCode.TICKET_NOT_FOUND));

        validateOwner(ticket.getTicketBook().getArchive().getUser().getId(), userPrincipal);

        // 파일 교체 로직
        File newFile = null;
        if (request.getFileId() != null) {
            newFile = fileRepository.findById(request.getFileId())
                    .orElseThrow(() -> new RestException(ErrorCode.FILE_NOT_FOUND));
        }

        // 업데이트 (내부적으로 File 참조 변경)
        ticket.update(request, newFile);

        return TicketDto.Response.of(ticket);
    }

    @Transactional
    public void deleteTicket(UserPrincipal userPrincipal, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RestException(ErrorCode.TICKET_NOT_FOUND));

        validateOwner(ticket.getTicketBook().getArchive().getUser().getId(), userPrincipal);

        ticketRepository.delete(ticket);
    }

    @Transactional
    public TicketDto.UpdateBookTitleResponse updateTicketBookTitle(UserPrincipal userPrincipal, Long archiveId, TicketDto.UpdateBookTitleRequest request) {
        TicketBook ticketBook = ticketBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        validateOwner(ticketBook.getArchive().getUser().getId(), userPrincipal);

        ticketBook.updateTitle(request.getTitle());

        return new TicketDto.UpdateBookTitleResponse(archiveId, ticketBook.getTitle());
    }

    // --- Helpers ---
    private void validateOwner(Long ownerId, UserPrincipal userPrincipal) {
        if (!ownerId.equals(userPrincipal.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void validateReadPermission(Ticket ticket, UserPrincipal userPrincipal) {
        Long ownerId = ticket.getTicketBook().getArchive().getUser().getId();
        Long viewerId = userPrincipal != null ? userPrincipal.getUserId() : null;
        Visibility visibility = ticket.getTicketBook().getArchive().getVisibility();

        if (ownerId.equals(viewerId)) return;
        if (visibility != Visibility.PUBLIC) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }
}