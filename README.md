# Project Introduction

<img width="4608" height="6490" alt="덕카이브_랜딩페이지_2단" src="https://github.com/user-attachments/assets/baf7ea07-6328-4609-9e83-a80281ea6eeb" />

<img width="4816" height="3408" alt="포스터" src="https://github.com/user-attachments/assets/0558e2c6-bb16-4d83-aeca-6167b9e5a9ae" />

<br>

---

# Team Introduction

## Server Members

<div align="left">

| **김태현** | **송성호** |
|:--------:|:---------:|
| [<img src="https://avatars.githubusercontent.com/u/92258189?v=4" height=150 width=150> <br/> @Youcu](https://github.com/Youcu) | [<img src="https://avatars.githubusercontent.com/u/173684716?v=4" height=150 width=150> <br/> @sungho1949](https://github.com/sungho1949) |
| 파트리더 | 팀원 |

</div>
<br>

## Other Parts

### Front-End — [@Deokive/FE](https://github.com/Deokive/FE)

---

# Development

## Key Features

Backend 파트가 담당하는 서비스 핵심 기능입니다.

- **아카이브(덕질 보드)** : 아카이브 CRUD · 피드 · 좋아요, 누적일수 기반 **Badge 5단계 자동 승급**(매일 배치)
- **아카이빙 도메인** : 다이어리 · 캘린더 이벤트(스포츠 경기 기록 포함, 월별 조회) · 티켓 · 갤러리 · 스티커
- **커뮤니티** : 게시글 · 댓글 · 좋아요, **핫스코어 랭킹** 기반 인기 피드
- **리포스트(SNS 스크랩)** : 탭 기반 큐레이션, **Open Graph 메타데이터 비동기 추출**(RabbitMQ) + SSE 진행 스트림
- **친구 · 실시간 알림** : 친구 요청/수락 관계 관리, SSE 구독 기반 실시간 알림 발행
- **인증 · 보안** : 소셜 로그인 3종(Google · Kakao · Naver) + 자체 회원가입(이메일 인증), JWT Access/Refresh 페어 + Redis 블랙리스트
- **트래픽 대응** : 조회수·좋아요 Redis 집계 후 스케줄러 DB 동기화(MQ fallback 포함), Bucket4j 레이트 리밋, Caffeine(L1)+Redis 캐시 · 워밍업
- **파일 처리** : S3 멀티파트 업로드, Spring Batch 기반 미사용 파일 자동 정리

## Environment

| Category | Stack |
| :--- | :--- |
| **Backend** | ![Java](https://img.shields.io/badge/Java%2021-007396?style=flat&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203.5-6DB33F?style=flat&logo=springboot&logoColor=white) ![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=flat&logo=spring&logoColor=white) ![QueryDSL](https://img.shields.io/badge/QueryDSL%205.1-0769AD?style=flat) ![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat&logo=springsecurity&logoColor=white) ![OAuth2](https://img.shields.io/badge/OAuth2%20%C3%973-4285F4?style=flat&logo=google&logoColor=white) ![JWT](https://img.shields.io/badge/JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white) ![Spring Batch](https://img.shields.io/badge/Spring%20Batch-6DB33F?style=flat&logo=spring&logoColor=white) |
| **DB · Cache · MQ** | ![MySQL](https://img.shields.io/badge/MySQL%208.0-4479A1?style=flat&logo=mysql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis%207%20·%20Redisson-FF4438?style=flat&logo=redis&logoColor=white) ![Caffeine](https://img.shields.io/badge/Caffeine-6F4E37?style=flat) ![RabbitMQ](https://img.shields.io/badge/RabbitMQ%203.12-FF6600?style=flat&logo=rabbitmq&logoColor=white) ![Bucket4j](https://img.shields.io/badge/Bucket4j-4B5563?style=flat) |
| **Test · Monitoring** | ![Testcontainers](https://img.shields.io/badge/Testcontainers-2496ED?style=flat&logo=docker&logoColor=white) ![RestAssured](https://img.shields.io/badge/RestAssured-14B85C?style=flat) ![Jacoco](https://img.shields.io/badge/Jacoco-8B0000?style=flat) ![Prometheus](https://img.shields.io/badge/Actuator%20·%20Prometheus-E6522C?style=flat&logo=prometheus&logoColor=white) |
| **버전 관리** | ![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat&logo=github&logoColor=white) |
| **협업 툴** | ![Jira](https://img.shields.io/badge/Jira-0052CC?style=flat&logo=jira&logoColor=white) ![Notion](https://img.shields.io/badge/Notion-000000?style=flat&logo=notion&logoColor=white) ![Discord](https://img.shields.io/badge/Discord-5865F2?style=flat&logo=discord&logoColor=white) |
| **CI/CD** | ![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat&logo=githubactions&logoColor=white) ![Docker](https://img.shields.io/badge/Docker%20Compose-2496ED?style=flat&logo=docker&logoColor=white) |
| **Infra** | ![AWS EC2](https://img.shields.io/badge/AWS%20EC2-FF9900?style=flat&logo=amazonec2&logoColor=white) ![AWS S3](https://img.shields.io/badge/AWS%20S3-569A31?style=flat&logo=amazons3&logoColor=white) |

## Docs

- **깃허브 컨벤션** : [@Git-Convention](https://www.notion.so/hooby/Deokive-Git-Convention-28af6c063f3e80eab179f61d10616486)
- **코드 컨벤션** : [@Code-Convention](https://www.notion.so/hooby/Deokive-Code-Convention-28af6c063f3e804582e5d106fe22a104)
- **백엔드팀 문서 허브** : [@Backend-Docs](https://hooby.notion.site/DEPth-Main-Project-Deokive-28af6c063f3e8001b775f4f68c632844)

<br>

> 백엔드팀 문서 허브에 들어가면 다음의 내용들을 확인하실 수 있습니다.  
>  
> ◦ 개발 가이드라인, 각종 컨벤션, 규칙 컨벤션  
> ◦ ERD, API 명세서  
> ◦ 협업 스페이스 (Jira, Figma - 현재 Jira는 기한 종료로 닫혀있음)  
> ◦ 백엔드 파트 회의 / 전체 팀 회의  
> ◦ 팀원 별 워크스페이스 - 설계, 인사이트, 작업물, 테스트 시나리오 등 팀원 별 개발과정을 녹여내는 공간

<br>

## Timeline

- **전체 프로젝트 일정** : 2025.10.12 ~ 2026.02.10
- **환경 세팅 마감일** : 2025.11.01
- **기능 구현 일정** : 2025.11.02 ~ 2026.01.16
- **테스트 일정** : 2026.01.17 ~ 2026.02.09

<img width="3200" height="1280" alt="timeline-gantt" src="https://github.com/user-attachments/assets/b0c43ba0-1251-4178-80a6-4785e2434470" />


> 시험 기간(중간 · 기말)의 개발 중단 구간은 실제 git 활동 기준으로 비워 표기했습니다.

## Management

- **관리 방식** : 애자일(Agile) 기반 스프린트 운영
- **이슈 관리** : Jira 칸반 보드를 활용하여 백로그 → 진행중 → 리뷰중 → 완료 상태 관리
- **문서 관리** : Notion을 활용해 회의록, 기획안, 기술 문서 기록
- **소스 코드 관리** : GitHub Issues & Pull Request를 통한 코드 리뷰 및 히스토리 관리

---

💬 **About Deokive Team**

> ◦ 명지대학교 연합동아리 DEPth 에서 진행하는 Main Project Team 입니다.<br>
> ◦ 기획팀 2인, 디자인팀 1인, 프론트엔드 2인, 백엔드 2인 구성으로 협업을 진행합니다.
