package com.depth.deokive.domain.ticket.service;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.service.ArchiveGuard;
import com.depth.deokive.common.util.PageUtils;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.ticket.dto.TicketDto;
import com.depth.deokive.domain.ticket.entity.Ticket;
import com.depth.deokive.domain.ticket.entity.TicketBook;
import com.depth.deokive.domain.ticket.repository.TicketBookRepository;
import com.depth.deokive.domain.ticket.repository.TicketQueryRepository;
import com.depth.deokive.domain.ticket.repository.TicketRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final FileService fileService;
    private final ArchiveGuard archiveGuard;
    private final TicketRepository ticketRepository;
    private final TicketBookRepository ticketBookRepository;
    private final FriendMapRepository friendMapRepository;
    private final TicketQueryRepository ticketQueryRepository;

    @Transactional
    public TicketDto.Response createTicket(UserPrincipal userPrincipal, Long archiveId, TicketDto.CreateRequest request) {
        // SEQ 1. 티켓북 조회
        TicketBook ticketBook = ticketBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        archiveGuard.checkOwner(ticketBook.getArchive().getUser().getId(), userPrincipal);

        // SEQ 2. 파일 조회 (있으면 찾고, 없으면 null)
        File file = (request.getFileId() != null)
                ? fileService.validateFileOwner(request.getFileId(), userPrincipal.getUserId())
                : null;

        // SEQ 3. 저장 (Entity에 File 바로 꽂기)
        Ticket ticket = request.toEntity(ticketBook, file);
        ticketRepository.save(ticket);

        return TicketDto.Response.of(ticket);
    }

    @Transactional(readOnly = true)
    public TicketDto.Response getTicket(UserPrincipal userPrincipal, Long ticketId) {
        Ticket ticket = ticketRepository.findByIdWithFile(ticketId)
                .orElseThrow(() -> new RestException(ErrorCode.TICKET_NOT_FOUND));

        archiveGuard.checkArchiveReadPermission(ticket.getTicketBook().getArchive(), userPrincipal);

        return TicketDto.Response.of(ticket);
    }

    @Transactional(readOnly = true)
    public PageDto.PageListResponse<TicketDto.TicketPageResponse> getTickets(UserPrincipal userPrincipal, Long archiveId, Pageable pageable) {
        // SEQ 1. 아카이브 존재 여부 및 타이틀 조회
        TicketBook ticketBook = ticketBookRepository.findByIdWithArchiveAndUser(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 공개 범위 검사 -> 만약에 통과되지 않으면 다음 실행 X
        archiveGuard.checkArchiveReadPermission(ticketBook.getArchive(), userPrincipal);

        // SEQ 3. 페이지네이션 조회
        Page<TicketDto.TicketPageResponse> ticketPage = ticketQueryRepository.searchTicketsByBook(archiveId, pageable);

        // SEQ 4. Page Range Validation
        PageUtils.validatePageRange(ticketPage);

        return PageDto.PageListResponse.of(ticketBook.getTitle(), ticketPage);
    }

    @Transactional
    public TicketDto.Response updateTicket(UserPrincipal userPrincipal, Long ticketId, TicketDto.UpdateRequest request) {
        // SEQ 1. 티켓 조회
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RestException(ErrorCode.TICKET_NOT_FOUND));

        // SEQ 2. 소유자 검증
        archiveGuard.checkOwner(ticket.getTicketBook().getArchive().getUser().getId(), userPrincipal);

        // SEQ 3. 파일 조회 및 결정
        File finalFile = resolveUpdatedFile(ticket.getFile(), request, userPrincipal.getUserId());

        // SEQ 4. 업데이트
        ticket.update(request, finalFile);

        return TicketDto.Response.of(ticket);
    }

    @Transactional
    public void deleteTicket(UserPrincipal userPrincipal, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RestException(ErrorCode.TICKET_NOT_FOUND));

        archiveGuard.checkOwner(ticket.getTicketBook().getArchive().getUser().getId(), userPrincipal);

        ticketRepository.delete(ticket);
    }

    @Transactional
    public TicketDto.UpdateBookTitleResponse updateTicketBookTitle(UserPrincipal userPrincipal, Long archiveId, TicketDto.UpdateBookTitleRequest request) {
        TicketBook ticketBook = ticketBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        archiveGuard.checkOwner(ticketBook.getArchive().getUser().getId(), userPrincipal);

        ticketBook.updateTitle(request.getTitle());

        return new TicketDto.UpdateBookTitleResponse(archiveId, ticketBook.getTitle());
    }

    // --- Helpers ---
    private File resolveUpdatedFile(File currentFile, TicketDto.UpdateRequest request, Long userId) {
        // 1. 삭제 요청인 경우 -> null 리턴 (DB에서 관계 끊김)
        if (Boolean.TRUE.equals(request.getDeleteFile())) {
            return null;
        }

        // 2. 교체 요청인 경우 (fileId가 있음) -> 새 파일 리턴
        if (request.getFileId() != null) {
            return fileService.validateFileOwner(request.getFileId(), userId);
        }

        // 3. 아무 요청 없음 -> 기존 파일 리턴 (유지)
        return currentFile;
    }
}