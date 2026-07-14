# Stage 2: Critic — Unit 00 기준선과 분석 경계 고정

## 실행 체크리스트

- [x] Planner의 범위와 비목표 검토
- [x] fixture 과적합 위험 검토
- [x] 테스트 격리와 회귀 범위 검토
- [x] PBT 적용 여부 검토
- [x] 최종 Verdict 기록

## Critique Items

### C1. Baseline이 기존 오류를 정답으로 고정할 위험

- **Target Section**: File Plan 1, 5
- **Problem**: 현재 하드코딩 결과를 일반 회귀 assertion으로만 작성하면 이후 제거가 regression으로 취급될 수 있다.
- **Requested Action**: 모든 기준 항목에 `PRESERVE`, `REPLACE`, `REMOVE`, `UNSUPPORTED` 중 하나를 부여한다.
- **Severity**: HIGH
- **Resolution**: Planner 계획에 migration disposition을 필수 필드로 포함했으므로 해결됨.

### C2. Holdout 오염 위험

- **Target Section**: File Plan 4
- **Problem**: holdout의 구체적인 기대값을 Rule 단위 테스트에서 반복 사용하면 더 이상 holdout이 아니다.
- **Requested Action**: holdout은 pipeline 평가 전용으로 유지하고 Unit 04 개발용 fixture와 분리한다.
- **Severity**: MEDIUM
- **Resolution**: 디렉터리 및 사용 정책이 명시돼 해결됨.

### C3. Unit 00 범위 팽창 위험

- **Target Section**: 전체
- **Problem**: baseline 작성 중 발견된 오류를 즉시 production에서 수정하면 Fact Graph 작업과 경계가 무너진다.
- **Requested Action**: `src/main` 수정 금지와 diff 검증을 완료 조건으로 둔다.
- **Severity**: HIGH
- **Resolution**: 구현 원칙과 검증 계획에 반영됨.

## Verdict

- **Decision**: OKAY
- **Rationale**: 현재 동작을 미래 정답으로 승인하지 않는 disposition 모델과 production 변경 금지 조건이 명확하다.

## Residual Risks

| ID | 위험 | 가능성 | 영향 | 완화책 |
|:---|:---|:---|:---|:---|
| R1 | JavaParser 타입 실패가 환경별로 다르게 재현될 수 있음 | 중간 | 중간 | source root와 classpath를 테스트에서 명시 |
| R2 | baseline JSON과 assertion이 중복될 수 있음 | 중간 | 낮음 | JSON은 안정적인 계약 필드만 보존 |
| R3 | 기존 전체 테스트 시간이 증가할 수 있음 | 낮음 | 낮음 | 신규 fixture는 최소 소스로 유지 |

