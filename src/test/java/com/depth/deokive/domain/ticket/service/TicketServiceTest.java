package com.depth.deokive.domain.ticket.service;

/** TicketService CRUD 테스트 */
class TicketServiceTest {

    /** Setup */
    // Users : UserA, UserB, UserC, AnonymousUser(not login)
    // Friend: UserA, UserB
    // Archives ::
    // - UserA : Archive_A_Public, Archive_A_Restricted, Archive_A_Private
    // - UserB : Archive_B_Public, Archive_B_Restricted, Archive_B_Private
    // - UserC : Archive_C_Public, Archive_C_Restricted, Archive_C_Private
    // Archive Files : DummyImages -> 모든 Archive에 존재하도록 세팅, 테스트를 위한 여분 더미 이미지들도 세팅 (넉넉히)
    // Tickets : 각 아카이브 당 10개의 티켓들을 생성한다. (일부는 파일 포함, 일부는 파일 없음) 

    // [Category 1]. Create ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 1. createTicket - 정상 케이스 (파일 포함, PUBLIC Archive) */
    // Given: Archive_A_Public, UserA UserPrincipal, 제목, 내용, 날짜, 파일 ID
    // When: createTicket 호출
    // Then: Ticket 엔티티가 저장되었는지 확인
    // Then: Ticket의 File이 연결되었는지 확인
    // Then: Ticket의 archiveId가 Archive_A_Public의 ID와 일치하는지 확인

    /** SCENE 2. createTicket - 정상 케이스 (파일 포함, RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserA UserPrincipal, 제목, 내용, 날짜, 파일 ID
    // When: createTicket 호출
    // Then: Ticket 엔티티가 저장되었는지 확인
    // Then: Ticket의 File이 연결되었는지 확인

    /** SCENE 3. createTicket - 정상 케이스 (파일 포함, PRIVATE Archive) */
    // Given: Archive_A_Private, UserA UserPrincipal, 제목, 내용, 날짜, 파일 ID
    // When: createTicket 호출
    // Then: Ticket 엔티티가 저장되었는지 확인
    // Then: Ticket의 File이 연결되었는지 확인

    /** SCENE 4. createTicket - 파일 없이 생성 (PUBLIC Archive) */
    // Given: Archive_A_Public, UserA UserPrincipal, 제목, 내용, 날짜, 파일 ID = null
    // When: createTicket 호출
    // Then: Ticket 엔티티가 저장되었는지 확인
    // Then: Ticket의 File이 null인지 확인

    /** SCENE 5. createTicket - 파일 없이 생성 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserA UserPrincipal, 제목, 내용, 날짜, 파일 ID = null
    // When: createTicket 호출
    // Then: Ticket 엔티티가 저장되었는지 확인
    // Then: Ticket의 File이 null인지 확인

    /** SCENE 6. createTicket - 파일 없이 생성 (PRIVATE Archive) */
    // Given: Archive_A_Private, UserA UserPrincipal, 제목, 내용, 날짜, 파일 ID = null
    // When: createTicket 호출
    // Then: Ticket 엔티티가 저장되었는지 확인
    // Then: Ticket의 File이 null인지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 7. createTicket - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId, UserA UserPrincipal, 제목, 내용, 날짜, 파일 ID
    // When: createTicket 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 8. createTicket - 타인 Archive에 생성 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public, UserC UserPrincipal (No Friend), 제목, 내용, 날짜, 파일 ID
    // When: createTicket 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 9. createTicket - 타인 Archive에 생성 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserC UserPrincipal (No Friend), 제목, 내용, 날짜, 파일 ID
    // When: createTicket 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 10. createTicket - 타인 Archive에 생성 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private, UserC UserPrincipal (No Friend), 제목, 내용, 날짜, 파일 ID
    // When: createTicket 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 11. createTicket - 친구 Archive에 생성 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserB UserPrincipal (Friend), 제목, 내용, 날짜, 파일 ID
    // When: createTicket 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 생성 불가)

    /** SCENE 12. createTicket - 다른 사용자의 파일 사용 시도 */
    // Given: Archive_A_Public, UserA UserPrincipal, UserC가 소유한 파일 ID
    // When: createTicket 호출
    // Then: FILE_NOT_FOUND 또는 권한 예외 발생 확인

    // [Category 2]. Read ----------------------------------------------------------
    
    // === [ PUBLIC Archive ] ===
    /** SCENE 13. getTicket - PUBLIC Archive (본인 조회, 파일 포함) */
    // Given: Archive_A_Public의 Ticket, File, UserA UserPrincipal
    // When: getTicket 호출
    // Then: Ticket 정보가 정확히 반환되는지 확인
    // Then: File 정보가 포함되었는지 확인
    // Then: File의 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 14. getTicket - PUBLIC Archive (타인 조회, 파일 포함) */
    // Given: Archive_A_Public의 Ticket, File, UserC UserPrincipal (No Friend)
    // When: getTicket 호출
    // Then: Ticket 정보가 정확히 반환되는지 확인
    // Then: File 정보가 포함되었는지 확인

    /** SCENE 15. getTicket - PUBLIC Archive (친구 조회, 파일 포함) */
    // Given: Archive_A_Public의 Ticket, File, UserB UserPrincipal (Friend)
    // When: getTicket 호출
    // Then: Ticket 정보가 정확히 반환되는지 확인
    // Then: File 정보가 포함되었는지 확인

    /** SCENE 16. getTicket - PUBLIC Archive (비회원 조회, 파일 포함) */
    // Given: Archive_A_Public의 Ticket, File, userPrincipal = null
    // When: getTicket 호출
    // Then: Ticket 정보가 정확히 반환되는지 확인
    // Then: File 정보가 포함되었는지 확인

    /** SCENE 17. getTicket - PUBLIC Archive (본인 조회, 파일 없음) */
    // Given: Archive_A_Public의 Ticket, File = null, UserA UserPrincipal
    // When: getTicket 호출
    // Then: Ticket 정보가 정확히 반환되는지 확인
    // Then: File 정보가 null인지 확인

    // === [ RESTRICTED Archive ] ===
    /** SCENE 18. getTicket - RESTRICTED Archive (본인 조회) */
    // Given: Archive_A_Restricted의 Ticket, File, UserA UserPrincipal
    // When: getTicket 호출
    // Then: Ticket 정보가 정확히 반환되는지 확인
    // Then: File 정보가 포함되었는지 확인

    /** SCENE 19. getTicket - RESTRICTED Archive (친구 조회) */
    // Given: Archive_A_Restricted의 Ticket, File, UserB UserPrincipal (Friend)
    // When: getTicket 호출
    // Then: Ticket 정보가 정확히 반환되는지 확인
    // Then: File 정보가 포함되었는지 확인

    /** SCENE 20. getTicket - RESTRICTED Archive (타인 조회, No Friend) */
    // Given: Archive_A_Restricted의 Ticket, File, UserC UserPrincipal (No Friend)
    // When: getTicket 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 21. getTicket - RESTRICTED Archive (비회원 조회) */
    // Given: Archive_A_Restricted의 Ticket, File, userPrincipal = null
    // When: getTicket 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive ] ===
    /** SCENE 22. getTicket - PRIVATE Archive (본인 조회) */
    // Given: Archive_A_Private의 Ticket, File, UserA UserPrincipal
    // When: getTicket 호출
    // Then: Ticket 정보가 정확히 반환되는지 확인
    // Then: File 정보가 포함되었는지 확인

    /** SCENE 23. getTicket - PRIVATE Archive (타인 조회, No Friend) */
    // Given: Archive_A_Private의 Ticket, File, UserC UserPrincipal (No Friend)
    // When: getTicket 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 24. getTicket - PRIVATE Archive (친구 조회) */
    // Given: Archive_A_Private의 Ticket, File, UserB UserPrincipal (Friend)
    // When: getTicket 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 25. getTicket - PRIVATE Archive (비회원 조회) */
    // Given: Archive_A_Private의 Ticket, File, userPrincipal = null
    // When: getTicket 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ Edge Cases ] ===
    /** SCENE 26. getTicket - 존재하지 않는 Ticket */
    // Given: 존재하지 않는 ticketId, UserA UserPrincipal
    // When: getTicket 호출
    // Then: TICKET_NOT_FOUND 예외 발생 확인

    // [Category 3]. Read-Pagination ----------------------------------------------------------
    
    // === [ PUBLIC Archive ] ===
    /** SCENE 27. getTickets - PUBLIC Archive (본인 조회) */
    // Given: Archive_A_Public, 10개의 Ticket들, UserA UserPrincipal, 페이지 정보
    // When: getTickets 호출
    // Then: Ticket 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 TicketBook 제목인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인
    // Then: 각 Ticket의 File 정보가 포함되었는지 확인

    /** SCENE 28. getTickets - PUBLIC Archive (타인 조회) */
    // Given: Archive_A_Public, 10개의 Ticket들, UserC UserPrincipal (No Friend), 페이지 정보
    // When: getTickets 호출
    // Then: Ticket 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 TicketBook 제목인지 확인

    /** SCENE 29. getTickets - PUBLIC Archive (친구 조회) */
    // Given: Archive_A_Public, 10개의 Ticket들, UserB UserPrincipal (Friend), 페이지 정보
    // When: getTickets 호출
    // Then: Ticket 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 TicketBook 제목인지 확인

    /** SCENE 30. getTickets - PUBLIC Archive (비회원 조회) */
    // Given: Archive_A_Public, 10개의 Ticket들, userPrincipal = null, 페이지 정보
    // When: getTickets 호출
    // Then: Ticket 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 TicketBook 제목인지 확인

    // === [ RESTRICTED Archive ] ===
    /** SCENE 31. getTickets - RESTRICTED Archive (본인 조회) */
    // Given: Archive_A_Restricted, 10개의 Ticket들, UserA UserPrincipal, 페이지 정보
    // When: getTickets 호출
    // Then: Ticket 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 TicketBook 제목인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인

    /** SCENE 32. getTickets - RESTRICTED Archive (친구 조회) */
    // Given: Archive_A_Restricted, 10개의 Ticket들, UserB UserPrincipal (Friend), 페이지 정보
    // When: getTickets 호출
    // Then: Ticket 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 TicketBook 제목인지 확인

    /** SCENE 33. getTickets - RESTRICTED Archive (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, 10개의 Ticket들, UserC UserPrincipal (No Friend), 페이지 정보
    // When: getTickets 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 34. getTickets - RESTRICTED Archive (비회원 조회) */
    // Given: Archive_A_Restricted, 10개의 Ticket들, userPrincipal = null, 페이지 정보
    // When: getTickets 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive ] ===
    /** SCENE 35. getTickets - PRIVATE Archive (본인 조회) */
    // Given: Archive_A_Private, 10개의 Ticket들, UserA UserPrincipal, 페이지 정보
    // When: getTickets 호출
    // Then: Ticket 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 TicketBook 제목인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인

    /** SCENE 36. getTickets - PRIVATE Archive (타인 조회, No Friend) */
    // Given: Archive_A_Private, 10개의 Ticket들, UserC UserPrincipal (No Friend), 페이지 정보
    // When: getTickets 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 37. getTickets - PRIVATE Archive (친구 조회) */
    // Given: Archive_A_Private, 10개의 Ticket들, UserB UserPrincipal (Friend), 페이지 정보
    // When: getTickets 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 38. getTickets - PRIVATE Archive (비회원 조회) */
    // Given: Archive_A_Private, 10개의 Ticket들, userPrincipal = null, 페이지 정보
    // When: getTickets 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ Edge Cases ] ===
    /** SCENE 39. getTickets - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId, UserA UserPrincipal, 페이지 정보
    // When: getTickets 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 40. getTickets - 빈 결과 */
    // Given: Archive_A_Public, Ticket이 없는 Archive, UserA UserPrincipal, 페이지 정보
    // When: getTickets 호출
    // Then: 빈 페이지가 반환되는지 확인
    // Then: 페이지 제목이 TicketBook 제목인지 확인

    /** SCENE 41. getTickets - 페이지 범위 초과 */
    // Given: Archive_A_Public, 10개의 Ticket들, 존재하지 않는 페이지 번호
    // When: getTickets 호출
    // Then: 페이지 범위 예외 발생 확인

    /** SCENE 42. getTickets - 페이지네이션 정렬 확인 */
    // Given: Archive_A_Public, 10개의 Ticket들, 페이지 정보
    // When: getTickets 호출
    // Then: Ticket 목록이 올바른 순서로 반환되는지 확인
    // Then: 페이지 크기가 정확한지 확인

    // [Category 4]. Update ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 43. updateTicket - 정상 케이스 (제목, 내용, 날짜 수정) */
    // Given: UserA의 Ticket, UserA UserPrincipal, 새로운 제목, 내용, 날짜
    // When: updateTicket 호출
    // Then: Ticket의 제목이 업데이트되었는지 확인
    // Then: Ticket의 내용이 업데이트되었는지 확인
    // Then: Ticket의 날짜가 업데이트되었는지 확인
    // Then: 기존 File이 유지되었는지 확인

    /** SCENE 44. updateTicket - 제목만 수정 */
    // Given: UserA의 Ticket, UserA UserPrincipal, 새로운 제목, 내용 = null, 날짜 = null
    // When: updateTicket 호출
    // Then: Ticket의 제목만 업데이트되었는지 확인
    // Then: 기존 내용과 날짜가 유지되었는지 확인

    /** SCENE 45. updateTicket - 내용만 수정 */
    // Given: UserA의 Ticket, UserA UserPrincipal, 제목 = null, 새로운 내용, 날짜 = null
    // When: updateTicket 호출
    // Then: Ticket의 내용만 업데이트되었는지 확인
    // Then: 기존 제목과 날짜가 유지되었는지 확인

    /** SCENE 46. updateTicket - 날짜만 수정 */
    // Given: UserA의 Ticket, UserA UserPrincipal, 제목 = null, 내용 = null, 새로운 날짜
    // When: updateTicket 호출
    // Then: Ticket의 날짜만 업데이트되었는지 확인
    // Then: 기존 제목과 내용이 유지되었는지 확인

    /** SCENE 47. updateTicket - 파일 교체 (기존 파일 있음) */
    // Given: UserA의 Ticket, 기존 File, UserA UserPrincipal, 새로운 파일 ID
    // When: updateTicket 호출
    // Then: Ticket의 File이 새로운 파일로 변경되었는지 확인
    // Then: 기존 File 연결이 해제되었는지 확인

    /** SCENE 48. updateTicket - 파일 추가 (기존 파일 없음) */
    // Given: UserA의 Ticket, File = null, UserA UserPrincipal, 새로운 파일 ID
    // When: updateTicket 호출
    // Then: Ticket의 File이 새로운 파일로 연결되었는지 확인

    /** SCENE 49. updateTicket - 파일 삭제 (deleteFile = true) */
    // Given: UserA의 Ticket, 기존 File, UserA UserPrincipal, deleteFile = true
    // When: updateTicket 호출
    // Then: Ticket의 File이 null인지 확인
    // Then: 기존 File 연결이 해제되었는지 확인

    /** SCENE 50. updateTicket - 파일 유지 (fileId = null, deleteFile = false) */
    // Given: UserA의 Ticket, 기존 File, UserA UserPrincipal, fileId = null, deleteFile = false
    // When: updateTicket 호출
    // Then: Ticket의 File이 기존과 동일한지 확인

    /** SCENE 51. updateTicket - 파일 유지 (fileId = null, deleteFile = null) */
    // Given: UserA의 Ticket, 기존 File, UserA UserPrincipal, fileId = null, deleteFile = null
    // When: updateTicket 호출
    // Then: Ticket의 File이 기존과 동일한지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 52. updateTicket - 타인 수정 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public의 Ticket, UserC UserPrincipal (No Friend)
    // When: updateTicket 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 53. updateTicket - 타인 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Ticket, UserC UserPrincipal (No Friend)
    // When: updateTicket 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 54. updateTicket - 타인 수정 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private의 Ticket, UserC UserPrincipal (No Friend)
    // When: updateTicket 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 55. updateTicket - 친구 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Ticket, UserB UserPrincipal (Friend)
    // When: updateTicket 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 수정 불가)

    /** SCENE 56. updateTicket - 존재하지 않는 Ticket */
    // Given: 존재하지 않는 ticketId, UserA UserPrincipal
    // When: updateTicket 호출
    // Then: TICKET_NOT_FOUND 예외 발생 확인

    /** SCENE 57. updateTicket - 다른 사용자의 파일 사용 시도 */
    // Given: UserA의 Ticket, UserA UserPrincipal, UserC가 소유한 파일 ID
    // When: updateTicket 호출
    // Then: FILE_NOT_FOUND 또는 권한 예외 발생 확인

    // [Category 5]. Delete ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 58. deleteTicket - 정상 케이스 (파일 포함) */
    // Given: UserA의 Ticket, File, UserA UserPrincipal
    // When: deleteTicket 호출
    // Then: Ticket이 삭제되었는지 확인
    // Then: File 엔티티는 유지되었는지 확인 (다른 Ticket에서 사용 가능)

    /** SCENE 59. deleteTicket - 정상 케이스 (파일 없음) */
    // Given: UserA의 Ticket, File = null, UserA UserPrincipal
    // When: deleteTicket 호출
    // Then: Ticket이 삭제되었는지 확인

    /** SCENE 60. deleteTicket - 정상 케이스 (PUBLIC Archive) */
    // Given: Archive_A_Public의 Ticket, UserA UserPrincipal
    // When: deleteTicket 호출
    // Then: Ticket이 삭제되었는지 확인

    /** SCENE 61. deleteTicket - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Ticket, UserA UserPrincipal
    // When: deleteTicket 호출
    // Then: Ticket이 삭제되었는지 확인

    /** SCENE 62. deleteTicket - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private의 Ticket, UserA UserPrincipal
    // When: deleteTicket 호출
    // Then: Ticket이 삭제되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 63. deleteTicket - 타인 삭제 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public의 Ticket, UserC UserPrincipal (No Friend)
    // When: deleteTicket 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 64. deleteTicket - 타인 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Ticket, UserC UserPrincipal (No Friend)
    // When: deleteTicket 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 65. deleteTicket - 타인 삭제 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private의 Ticket, UserC UserPrincipal (No Friend)
    // When: deleteTicket 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 66. deleteTicket - 친구 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Ticket, UserB UserPrincipal (Friend)
    // When: deleteTicket 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 삭제 불가)

    /** SCENE 67. deleteTicket - 존재하지 않는 Ticket */
    // Given: 존재하지 않는 ticketId, UserA UserPrincipal
    // When: deleteTicket 호출
    // Then: TICKET_NOT_FOUND 예외 발생 확인

    // [Category 6]. Update Book Title ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 68. updateTicketBookTitle - 정상 케이스 (PUBLIC Archive) */
    // Given: Archive_A_Public의 TicketBook, UserA UserPrincipal, 새로운 제목
    // When: updateTicketBookTitle 호출
    // Then: TicketBook의 제목이 업데이트되었는지 확인
    // Then: Response가 정확히 반환되는지 확인

    /** SCENE 69. updateTicketBookTitle - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 TicketBook, UserA UserPrincipal, 새로운 제목
    // When: updateTicketBookTitle 호출
    // Then: TicketBook의 제목이 업데이트되었는지 확인

    /** SCENE 70. updateTicketBookTitle - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private의 TicketBook, UserA UserPrincipal, 새로운 제목
    // When: updateTicketBookTitle 호출
    // Then: TicketBook의 제목이 업데이트되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 71. updateTicketBookTitle - 타인 수정 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public의 TicketBook, UserC UserPrincipal (No Friend)
    // When: updateTicketBookTitle 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 72. updateTicketBookTitle - 타인 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 TicketBook, UserC UserPrincipal (No Friend)
    // When: updateTicketBookTitle 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 73. updateTicketBookTitle - 타인 수정 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private의 TicketBook, UserC UserPrincipal (No Friend)
    // When: updateTicketBookTitle 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 74. updateTicketBookTitle - 친구 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 TicketBook, UserB UserPrincipal (Friend)
    // When: updateTicketBookTitle 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 수정 불가)

    /** SCENE 75. updateTicketBookTitle - 존재하지 않는 TicketBook */
    // Given: 존재하지 않는 archiveId, UserA UserPrincipal
    // When: updateTicketBookTitle 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인
}

