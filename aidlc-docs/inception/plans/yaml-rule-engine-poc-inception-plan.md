# YAML Rule Engine PoC — INCEPTION 실행 계획

## 목적

기존 `Semantic Analysis & Rule Engine Layer`의 동작을 유지하면서, 시맨틱 분류와 코드그래프 탐색 규칙을 제한된 선언형 YAML 프리미티브로 외부화할 수 있는 범위와 실현 가능성을 검증한다.

## 문서 범위

- 현재 단계에서는 구현하지 않는다.
- 기존 로드맵과 실제 코드의 간극을 요구사항과 검증 가능한 작업 단위로 정제한다.
- 사용자 승인 이후에만 Workflow Planning 및 후속 설계 단계로 이동한다.

## 실행 체크리스트

### Workspace Detection

- [x] Unified AI-DLC Kit 루트와 공통 규칙 로드
- [x] 기존 `aidlc-state.md` 및 세션 연속성 상태 복원
- [x] brownfield 여부와 Java 21/Gradle 빌드 구조 확인
- [x] 최신 Reverse Engineering 산출물 존재 및 현재성 확인
- [x] 기존 Reverse Engineering 재사용 결정

### Requirements Analysis

- [x] 사용자가 제공한 YAML 외부화 로드맵 검토
- [x] 실제 YAML 로더와 Rule Engine/Semantic Dispatcher 경계 확인
- [x] 요청 유형, 범위, 복잡도 판정
- [x] 요구사항 명확화 및 트레이드오프 질문 작성
- [x] 사용자 답변 수신 및 완전성/모순 검증
- [x] 확장 기능 선택 확정 — Security Baseline No, PBT No; 전체 규칙 로드 생략
- [x] 확정 요구사항과 Acceptance Criteria 작성
- [x] 핵심 결정 및 Hard Constraints 영속화
- [x] Requirements Analysis Revision 1 승인 — Revision 2로 대체됨

### 이후 후보 단계

- [ ] Workflow Planning
- [ ] Application Design
- [ ] Units Generation
- [ ] 사용자 승인 후 Construction 진입 여부 결정

### Requirements Analysis Revision 2

- [x] Spring MVC 지원 envelope와 분석 보장 수준 재정의
- [x] 특정 업무 규칙과 generic semantic recipe 구분
- [x] engine configuration / semantic recipe / framework catalog 책임 분리
- [x] concrete field·literal의 runtime binding 요구사항 작성
- [x] DelegatedGuard domain hardcoding 교정 요구사항 작성
- [x] cross-repository 및 rename holdout 검증 요구사항 작성
- [x] Revision 2 영속 사양 작성
- [x] Requirements Analysis Revision 2 승인

### Workflow Planning

- [x] 이전 context와 Requirements Revision 2 로드
- [x] transformation scope, component impact와 risk 평가
- [x] 실행/생략 단계 결정
- [x] 예비 Unit 구조 및 package update sequence 작성
- [x] requirement verification plan 작성
- [x] Mermaid와 text alternative 작성 및 구조 검증
- [x] Workflow Planning 승인

### Application Design

- [x] Application Design 규칙과 선행 문서 로드
- [x] 컴포넌트/서비스/의존성 미결정 항목 식별
- [x] trade-off-aware 설계 질문 작성
- [x] 최초 설계 질문 6개 답변 수신 및 선택값 검증
- [x] Q3 실행 권위와 Q5 출력 경계의 상호 모순 식별
- [x] 후속 질문 Q7 답변 수신 및 모순 해소
- [x] 필수 Application Design 산출물 5종 작성
- [x] 설계 완전성 및 일관성 검증
- [x] 정상 scan/bootstrap 모순 정정 및 graph 경계 재검증
- [x] Application Design 승인

### Units Generation

- [x] Units Generation 절차와 승인된 Application Design 로드
- [x] 예비 Unit, 의존성, 요구사항 매핑과 brownfield module 경계 분석
- [x] Units Generation 계획 및 분해 질문 작성
- [x] 사용자 답변 수신 및 모호성 검증
- [x] Unit 계획 승인
- [x] Unit 산출물 3종 생성 및 검증
- [x] Units Generation 승인

### Construction — U01 Generalization Baseline

- [x] U01 Unit 정의와 requirement mapping 로드
- [x] U01 Functional Design 규칙 및 질문 category 평가
- [x] U01 Functional Design 계획과 질문 작성
- [x] U01 Functional Design 답변 수신 및 모호성 검증
- [x] U01 Functional Design 산출물 생성 및 검증
- [x] U01 Functional Design 승인
- [x] U01 NFR Requirements 규칙과 Functional Design 로드
- [x] U01 NFR category 평가 및 질문 계획 작성
- [x] U01 NFR Requirements 답변 수신 및 모호성 검증
- [x] U01 NFR Requirements 산출물 생성 및 검증
- [x] U01 NFR Requirements 승인
- [x] U01 NFR Design 규칙과 NFR Requirements 로드
- [x] U01 NFR Design category 평가 및 질문 작성
- [x] U01 NFR Design 답변 수신 및 모호성 검증
- [x] U01 NFR Design 산출물 생성 및 검증
- [x] U01 NFR Design 승인
- [x] U01 implementation Deep Interview Gating
- [x] U01 Consensus Planning 4-stage validation 및 승인
- [ ] U01 implementation/evidence 및 G01 승인

## 현재 판정

- **요청 유형**: 기존 Rule Engine의 신규 기능 및 아키텍처 개선 PoC
- **초기 범위**: Semantic Analysis와 Graph Rule Engine의 다중 컴포넌트
- **복잡도**: 복잡
- **요구사항 깊이**: 종합
- **현재 게이트**: U01 snapshot proposal 생성 및 baseline label review 준비
