# 요구사항 갱신 확인 질문

최신 역공학 결과를 기존 요구사항에 반영하기 위한 질문입니다. 각 `[Answer]:` 뒤에 선택지를 입력해 주세요.

## 트레이드오프 질문 1: 요구사항 갱신 범위

현행 구현에는 기존 요구사항 이후 추가된 Web UI, 사실 그래프, 규칙 엔진, YAML 규칙 팩, 출력 migration 및 품질 게이트가 포함되어 있습니다.

### Option A: 현행 기능만 문서화

- **Pros**: 현재 코드와 요구사항의 추적성이 빠르게 복구됨
- **Cons**: 구조 개선 방향과 완료 기준은 별도로 다시 결정해야 함
- **Impact Analysis**:
  - Compatibility: 현행 동작 유지
  - Performance: 영향 없음
  - Maintainability: 중립
  - Implementation Cost: 낮음
  - Risk: 낮음

### Option B: 현행 기능과 개선 목표를 함께 요구사항화 (권장)

- **Pros**: 현재 상태와 목표 상태, 완료 기준을 한 문서에서 추적 가능
- **Cons**: 범위와 우선순위를 추가로 구분해야 함
- **Impact Analysis**:
  - Compatibility: 호환성 기준을 명시적으로 관리
  - Performance: 목표 설정에 따라 영향 가능
  - Maintainability: 개선
  - Implementation Cost: 중간
  - Risk: 중간

### Option X: 기타

[Answer]:
[Rationale]:

## 트레이드오프 질문 2: CLI, Demo, Web 분석 경로

현재 세 실행 방식은 핵심 분석 클래스를 공유하지만 Demo와 Git service, service 내부 두 메서드가 orchestration을 중복합니다.

### Option A: 단일 분석 application service로 통합 (권장)

- **Pros**: 같은 입력/revision에 같은 분석 결과를 보장하기 쉬움
- **Cons**: 진입점과 출력 adapter 재정렬 필요
- **Impact Analysis**:
  - Compatibility: 기존 CLI/Web 계약을 adapter로 보존 가능
  - Performance: 중복 제거로 중립 또는 소폭 개선
  - Maintainability: 크게 개선
  - Implementation Cost: 중간
  - Risk: 중간

### Option B: 현 구조 유지 및 동등성 회귀 테스트만 추가

- **Pros**: 코드 변경이 작음
- **Cons**: 파이프라인 drift 위험이 계속 존재
- **Impact Analysis**:
  - Compatibility: 가장 안전
  - Performance: 영향 없음
  - Maintainability: 기술부채 유지
  - Implementation Cost: 낮음
  - Risk: 중간

### Option X: 기타

[Answer]:
[Rationale]:

## 트레이드오프 질문 3: `projectName`과 `baseUrl` 계약

CLI와 Web은 두 값을 필수로 받지만 현재 `GitExecutionSpecScanService` 분석 결과에는 전달하지 않습니다.

### Option A: 실행 모델과 OpenAPI 메타데이터에 실제 반영 (권장)

- **Pros**: 입력 계약과 출력 의미가 일치
- **Cons**: service 및 exporter signature 변경 필요
- **Impact Analysis**:
  - Compatibility: 필드 의미가 정상화되며 기존 요청 형식 유지
  - Performance: 영향 미미
  - Maintainability: 개선
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: 필수 입력에서 제거

- **Pros**: 현재 실제 사용 범위와 API가 일치
- **Cons**: 기존 CLI/Web 요청 계약 변경
- **Impact Analysis**:
  - Compatibility: breaking 가능
  - Performance: 영향 없음
  - Maintainability: 단순화
  - Implementation Cost: 낮음
  - Risk: 중간

### Option C: 현행 필수 검증만 유지

- **Pros**: 변경 없음
- **Cons**: 사용되지 않는 필수 입력이라는 혼란 지속
- **Impact Analysis**:
  - Compatibility: 완전 유지
  - Performance: 영향 없음
  - Maintainability: 저하
  - Implementation Cost: 없음
  - Risk: 중간

### Option X: 기타

[Answer]:
[Rationale]:

## 트레이드오프 질문 4: 규칙 기반 출력 migration 목표

현재 기본 mode는 `COMPARE`이며 legacy와 신규 규칙 출력을 비교할 수 있습니다.

### Option A: 품질 게이트 충족 후 `NEW_ONLY` 전환을 요구사항으로 확정 (권장)

- **Pros**: migration 완료 조건과 delivery gate가 명확함
- **Cons**: golden/holdout 품질 기준 유지 비용 발생
- **Impact Analysis**:
  - Compatibility: 전환 시 출력 변화 가능
  - Performance: legacy 이중 계산 제거 가능
  - Maintainability: 장기적으로 개선
  - Implementation Cost: 중간
  - Risk: 중간

### Option B: PoC 동안 `COMPARE`를 최종 상태로 유지

- **Pros**: 결과 비교와 rollback이 쉬움
- **Cons**: 이중 경로와 migration 부채 지속
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 이중 처리 비용 유지
  - Maintainability: 저하
  - Implementation Cost: 낮음
  - Risk: 낮음

### Option X: 기타

[Answer]:
[Rationale]:

## Question 5: 운영 목표 수준

A) 로컬 PoC로 유지하며 인증, rate limit, timeout, repository 크기 제한은 비필수로 둔다

B) 내부 팀용 도구 수준으로 강화하며 timeout, 요청 크기, clone 크기와 동시성 제한을 요구한다

C) 외부 공개 서비스 수준으로 강화하며 인증, 감사, rate limit 및 보안 검증을 포함한다

X) 기타 (아래 `[Answer]:` 뒤에 설명해 주세요)

[Answer]:

## Question 6: 확장 설정 재확인

A) 기존 결정을 유지한다: Security Baseline 비활성, Property-Based Testing 부분 적용

B) Security Baseline과 Property-Based Testing을 모두 활성화한다

C) Security Baseline만 활성화하고 Property-Based Testing은 부분 적용한다

X) 기타 (아래 `[Answer]:` 뒤에 설명해 주세요)

[Answer]:
