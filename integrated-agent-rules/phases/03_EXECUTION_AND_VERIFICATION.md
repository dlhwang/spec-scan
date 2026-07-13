# AI-DLC Phase 03: 구현 & 증거검증 (Execution & Verification)

이 지침서는 AI-DLC의 세 번째 단계인 **구현(코드 생성)** 단계와 **증거검증(빌드 및 테스트)** 단계의 통합 실행 매뉴얼입니다.

---

## 🔄 1. 프로세스 맵 및 연동 방식

```text
[1. 거시적 구현/구축 단계]
  - Code Generation: 최종 승인된 기획서를 바탕으로 소스 코드 구현
  - Build & Test: 로컬 테스트, 어셈블리 검증 및 회귀 테스트 수행
         ▼
[2. 미시적 목표지향 실행/검증 단계 (Goal-Driven Execution)]
  - goals.json 에 목표 트리(Goal Tree) 구성
  - 구현 턴마다 ledger.jsonl 원장에 상태 변경 기록
         ▼ (검증 실패 시)
[3. 피드백 제어 루프 가동]
  - 상위 목표 review_blocked로 락인 (Lock-in)
  - 하위 blocker 목표 동적 주입 및 선행 해결 유도
         ▼ (검증 통과 시)
[4. 최종 빌드 완료 및 토큰 비용 정산]
```

---

## 🛠️ 2. 단계별 실행 지침 (Activities)

### ① Goal Tree 설계 및 원장 관리
1. **목표 분해**: 에이전트는 기획서(`pending-approval.md`)의 개발 계획과 검증 계획을 바탕으로 세부 목표 목록을 구성하여 `goals.json`에 기록하십시오.
2. **이벤트 영속화**: 목표 상태가 변경될 때마다 `ledger.jsonl`에 정형 이벤트를 Append 하십시오.

### ② 증거(Evidence) 기반 평가 및 체크포인트
에이전트는 "코드를 다 고쳤습니다"라는 구두 보고만으로는 목표를 완료할 수 없습니다.

* **완료 조건 (complete)**:
  - 기획서에서 명세한 테스트 시나리오(Unit, Assembly, Regression)를 모두 실행하십시오.
  - 단순 빌드 성공이 아닌, **"성공한 구체적 Assertion 명세, 빌드 출력 로그 일부, 커버리지 확보 정보"** 등을 `goals.json`의 `evidence` 필드에 텍스트로 기록해야만 완료 상태 전이가 허용됩니다.

### ③ 동적 블로커 해결 (Review Blocker) 매커니즘
검증 수행 중 실패가 감지되거나 미진한 사항이 발견되었을 때, 이를 우회하여 강제 마감하는 것을 차단합니다.

1. **상태 차단 (Lock-in)**: 현재 진행 중인 목표의 상태를 즉시 **`review_blocked`**로 변경하고, 원인 분석 결과(예: Assertion 에러 로그)를 `evidence`에 기록하십시오.
2. **블로커 목표 동적 주입**:
   - 검증 실패의 원인을 직접 타격하고 해결하기 위한 새로운 하위 목표(예: `G002: Resolve regression blocker`)를 생성하여 `goals.json`에 주입하십시오.
   - `steering` 필드에 차단 관계(`"kind": "review_blocker"`, `"blockedGoalId": "G001"`)를 설정하여, 에이전트가 하위 목표를 최우선 해결(`active`)하도록 강제합니다.
3. **락 해제 및 재시도**: 하위 목표가 완료(`complete`)되면, 상위 목표의 락을 해제하고 `active` 상태로 원복하여 검증 체크포인트를 재수행합니다.

---

## ⚡ 3. 비용 통제 장치 (Guardrails)
* **턴 제한(Nudge Budget)**: 세션 내 누적 실행 턴 수가 **10회**를 초과하는 경우 작업을 즉시 정지하고 `failed` 상태를 기록하여 에이전트 요금 과청구를 예방하십시오.
* **비용 추적**: 매 턴 종료 시 `token-logs/token-log.jsonl`에 토큰 사용량을 기록하십시오.

---

## 📋 4. 에이전트 적용용 룰 (Copy & Paste Rule)
> ```text
> # RULE: EXECUTION & VERIFICATION
> - Implement source code and execute tests (Unit, Assembly, Regression).
> - Decompose tasks into Goal Tree in goals.json.
> - Target status changes must be logged in ledger.jsonl.
> - To mark a goal complete, write physical test logs and assertion success into evidence field.
> - If tests fail, transition goal to review_blocked and inject a sub-goal with steering constraint.
> - Stop coding if total turns exceed 10 (nudge_budget).
> ```
