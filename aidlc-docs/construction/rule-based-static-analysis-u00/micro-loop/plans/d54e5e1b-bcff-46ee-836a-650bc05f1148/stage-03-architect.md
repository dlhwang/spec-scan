# Stage 3: Architect — Unit 00 기준선과 분석 경계 고정

## 실행 체크리스트

- [x] 레이어 경계 검토
- [x] 의존 방향 검토
- [x] 다음 Unit과의 계약 검토
- [x] 기술적 실행 가능성 검토
- [x] 최종 Decision 기록

## Architecture Audit Checklist

### A1. Layer Boundaries and Separation

- **Result**: PASS
- **Details**: Unit 00은 test source와 test resources에만 기준선을 추가하며 production 분석 계층을 변경하지 않는다.

### A2. Dependency Directions

- **Result**: PASS
- **Details**: 테스트가 기존 public/application entry point를 호출하고 production 코드가 fixture나 baseline 모델을 참조하지 않는다.

### A3. Evolutionary Architecture

- **Result**: PASS
- **Details**: legacy 동작을 즉시 제거하지 않고 disposition으로 분류하여 Unit 01 이후 신규 구조와 비교할 수 있다.

### A4. Generalization Boundary

- **Result**: PASS
- **Details**: synthetic와 holdout을 분리하고 기존 주문·권한 명칭을 피한 fixture로 도메인 독립성을 검증한다.

### A5. Verification Feasibility

- **Result**: PASS
- **Details**: Gradle/JUnit 5 환경에서 별도 production dependency 없이 실행 가능하다.

## Decision

- **Verdict**: APPROVE
- **Rationale**: 기준선 격리는 이후 Fact Graph와 Rule Engine 변경의 회귀 위험을 통제하며 기존 계층을 침범하지 않는다.
- **Conditions**:
  - production 파일을 수정하지 않는다.
  - baseline의 모든 known defect에 `REPLACE` 또는 `REMOVE` 의도를 남긴다.
  - holdout fixture를 Rule 구현의 단위 테스트 oracle로 사용하지 않는다.

