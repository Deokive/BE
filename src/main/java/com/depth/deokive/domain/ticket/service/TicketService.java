package com.depth.deokive.domain.ticket.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
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

    private final TicketRepository ticketRepository;
    private final TicketBookRepository ticketBookRepository;
    private final FriendMapRepository friendMapRepository;
    private final TicketQueryRepository ticketQueryRepository;

    @Transactional
    public TicketDto.Response createTicket(UserPrincipal userPrincipal, Long archiveId, TicketDto.CreateRequest request) {
        // SEQ 1. 티켓북 조회
        TicketBook ticketBook = ticketBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        validateOwner(ticketBook.getArchive().getUser().getId(), userPrincipal);

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

        validateReadPermission(ticket.getTicketBook().getArchive(), userPrincipal);

        return TicketDto.Response.of(ticket);
    }

    @Transactional(readOnly = true)
    public TicketDto.PageListResponse getTickets(UserPrincipal userPrincipal, Long archiveId, Pageable pageable) {
        // SEQ 1. 아카이브 존재 여부 및 타이틀 조회
        TicketBook ticketBook = ticketBookRepository.findByIdWithArchiveAndUser(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 공개 범위 검사 -> 만약에 통과되지 않으면 다음 실행 X
        validateReadPermission(ticketBook.getArchive(), userPrincipal);

        // SEQ 3. 페이지네이션 조회
        Page<TicketDto.TicketElementResponse> ticketPage = ticketQueryRepository.searchTicketsByBook(archiveId, pageable);

        // SEQ 4. Index 범위 벗어나면 404에러
        if(pageable.getPageNumber() > 0 && pageable.getPageNumber() >= ticketPage.getTotalPages()) {
            throw new RestException(ErrorCode.DB_DATA_NOT_FOUND);
        }

        return TicketDto.PageListResponse.of(ticketBook.getTitle(), ticketPage);
    }

    @Transactional
    public TicketDto.Response updateTicket(UserPrincipal userPrincipal, Long ticketId, TicketDto.UpdateRequest request) {
        // SEQ 1. 티켓 조회
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RestException(ErrorCode.TICKET_NOT_FOUND));

        // SEQ 2. 소유자 검증
        validateOwner(ticket.getTicketBook().getArchive().getUser().getId(), userPrincipal);

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

    private void validateReadPermission(Archive archive, UserPrincipal userPrincipal) {
        Long ownerId = archive.getUser().getId();
        Long viewerId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
        //Visibility visibility = ticket.getTicketBook().getArchive().getVisibility();

        if (ownerId.equals(viewerId)) return;

        switch (archive.getVisibility()) {
            case PRIVATE ->
                    throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 주인 외 접근 불가
            case RESTRICTED -> {
                if (!checkFriendRelationship(viewerId, ownerId)) {
                    throw new RestException(ErrorCode.AUTH_FORBIDDEN);
                }
            }
            case PUBLIC -> { /* 모두 허용 */ }
        }
    }

    private boolean checkFriendRelationship(Long viewerId, Long ownerId) {
        if (viewerId == null) return false;

        return friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(
                viewerId,
                ownerId,
                FriendStatus.ACCEPTED
        );
    }

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