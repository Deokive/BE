# Deokive Backend
![cover](https://github.com/user-attachments/assets/04016149-9221-455a-8c75-3190641cb8a0)

<br>

## 프로젝트 소개
### 📌 서비스 개요
**Deokive**는 아이돌, 애니, 게임 등 다양한 덕질 경험을 **일기, 캘린더, 디데이** 형태로 기록하고 추억할 수 있는 **개인 아카이브 웹 플랫폼**입니다.  
본 레포지토리는 해당 서비스의 **백엔드 서버**를 담당합니다.

<br>

### 🎯 서비스 기획 의도
- 덕질 경험을 한곳에 기록하고 관리할 수 있는 **디지털 아카이브** 제공
- 단순히 팬 활동에 국한되지 않고, **게임/여행/키덜트 등 다양한 취향 아카이빙** 가능
- 개인 맞춤형 아카이브 서비스를 통해 **일상의 추억을 기록하고 간직**할 수 있도록 지원

<br>

### 👥 메인 타겟
- **연령대:** 10대 후반 ~ 30대 초반
- **특징:** 개인의 취향 활동을 디지털로 기록·관리하려는 세대
- **소비 성향:** 굿즈 구매, 티켓 예매, SNS 활동에 적극적이며, 아날로그 기록 습관도 있지만 디지털 관리 욕구가 큼  
  <br>

### ⚙️ 주요 기능 (Backend)
- **덕질 보드 관리 API**
    - 보드 생성, 템플릿 제공, 덕질 시작일 설정(D-Day 계산)
    - 이벤트 기록(콘서트, 팬싸, 생일카페) 및 알림 기능 지원
      <br>
- **덕질 일기(아카이빙) API**
    - 텍스트/사진 업로드, 썸네일 지정
    - 해시태그/감정 기록 기능
      <br>
- **덕질 게이지 & 뱃지 시스템**
    - 기록 데이터 기반으로 게이지 산출
    - 단계별 뱃지 발급 및 보드 내 노출

<br>

---

## 팀원 구성

<div align="left">

| **김태현** | **송성호** |
|:--------:|:---------:|
| [<img src="https://avatars.githubusercontent.com/u/92258189?s=400&u=92cd0d19deef34b9faf65015b79a7884a6d6932e&v=4$0" height=150 width=150> <br/> @Youcu](https://github.com/Youcu) | [<img src="https://avatars.githubusercontent.com/u/173684716?v=4$0" height=150 width=150> <br/> @sungho1949](https://github.com/sungho1949) |
|  파트리더  |    팀원    |

</div>
<br>

---

## 1. 개발 환경

- **Backend** :
  ![Java](https://img.shields.io/badge/Java-007396?style=flat&logo=coffeescript&logoColor=white)
  ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat&logo=springboot&logoColor=white)

- **버전 및 이슈관리** :
  ![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat&logo=github&logoColor=white)
- **협업 툴** :
  ![Jira](https://img.shields.io/badge/Jira-0052CC?style=flat&logo=jira&logoColor=white)
  ![Notion](https://img.shields.io/badge/Notion-000000?style=flat&logo=notion&logoColor=white)
  ![Discord](https://img.shields.io/badge/Discord-5865F2?style=flat&logo=discord&logoColor=white)
- **CI/CD** :
  ![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat&logo=githubactions&logoColor=white)
  ![AWS](https://img.shields.io/badge/AWS-FF9900?style=flat&logo=amazonaws&logoColor=white)
  ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)
- **깃허브 컨벤션** : [@Git-Convention](https://www.notion.so/hooby/Deokive-Git-Convention-28af6c063f3e80eab179f61d10616486)
- **코드 컨벤션** : [@Code-Convention](https://www.notion.so/hooby/Deokive-Code-Convention-28af6c063f3e804582e5d106fe22a104$0)
  <br>

## 2. 개발 기간

- **전체 프로젝트 일정** : 2025.10.12 ~ 2025.02.10
- **환경 세팅 마감일** : 2025.11.01
- **기능 구현 일정** : 2025.11.02 ~ 2025.01.16
- **테스트 일정** : 2025.01.17 ~ 2025.02.09

<br>

## 3. 작업 관리
- **관리 방식** : 애자일(Agile) 기반 스프린트 운영
- **이슈 관리** : Jira 칸반 보드를 활용하여 백로그 → 진행중 → 리뷰중 → 완료 상태 관리
- **문서 관리** : Notion을 활용해 회의록, 기획안, 기술 문서 기록
- **소스 코드 관리** : GitHub Issues & Pull Request를 통한 코드 리뷰 및 히스토리 관리

<br>

---

💬 **About Deokive Team**

> ◦ 명지대학교 연합동아리 DEPth 에서 진행하는 Main Project Team 입니다.<br>
> ◦ 기획팀 2인, 디자인팀 1인, 프론트엔드 2인, 백엔드 2인 구성으로 협업을 진행합니다.