# Build and Test

**목적**: 모든 유닛 빌드 및 종합적 테스팅 전략 실행

## 사전 조건
- 모든 유닛의 코드 생성 완료 필수
- 모든 코드 아티팩트 생성 완료 필수
- 프로젝트가 빌드 및 테스트 준비 상태

---

## Step 1: 테스팅 요구사항 분석

프로젝트를 분석하여 적절한 테스팅 전략 결정:
- **단위 테스트**: 코드 생성 중 유닛별로 이미 생성됨
- **통합 테스트**: 유닛/서비스 간 상호작용 테스트
- **성능 테스트**: 부하, 스트레스, 확장성 테스트
- **E2E 테스트**: 완전한 사용자 워크플로우
- **계약 테스트**: 서비스 간 API 계약 검증
- **보안 테스트**: 취약점 스캔, 침투 테스트
- **요구사항 검증**: 구현된 각 요구사항, 사용자 스토리,
  인수 기준, API 계약, 정책을 입증하는 테스트 증거에 매핑

규칙:
- Build and Test는 기능 중심 테스트와 관련 회귀 테스트를 모두 실행해야 한다.
- `mvn test`, `npm test`, `.\\gradlew.bat test` 등의 명령어는 실행
  증거이나, 요약에는 변경된 기능이나 계약을 검증하는 테스트도
  반드시 식별해야 한다.
- 자동화 테스트 증거 없는 구현된 요구사항이나 스토리는 반드시
  N/A로 표시하고 사유를 기재하며, 해당 시 수동 검증 방법을 명시한다.

---

## Step 2: 빌드 지침 생성

`aidlc-docs/construction/build-and-test/build-instructions.md` 생성:

```markdown
# 빌드 지침

## 사전 조건
- **빌드 도구**: [도구명 및 버전]
- **의존성**: [필요한 모든 의존성 나열]
- **환경 변수**: [필요한 환경 변수 나열]
- **시스템 요구사항**: [OS, 메모리, 디스크 공간]

## 빌드 단계

### 1. 의존성 설치
\`\`\`bash
[의존성 설치 명령어]
# 예: npm install, mvn dependency:resolve, pip install -r requirements.txt
\`\`\`

### 2. 환경 설정
\`\`\`bash
[환경 설정 명령어]
# 예: export 변수, 자격증명 설정
\`\`\`

### 3. 전체 유닛 빌드
\`\`\`bash
[전체 유닛 빌드 명령어]
# 예: mvn clean install, npm run build, brazil-build
\`\`\`

### 4. 빌드 성공 확인
- **예상 출력**: [성공적인 빌드 출력 설명]
- **빌드 아티팩트**: [생성된 아티팩트 및 위치 나열]
- **일반 경고**: [허용 가능한 경고 사항 기재]

## 트러블슈팅

### 의존성 오류로 빌드 실패
- **원인**: [일반적 원인]
- **해결책**: [단계별 수정 방법]

### 컴파일 오류로 빌드 실패
- **원인**: [일반적 원인]
- **해결책**: [단계별 수정 방법]
```

---

## Step 3: 단위 테스트 실행 지침 생성

`aidlc-docs/construction/build-and-test/unit-test-instructions.md` 생성:

```markdown
# 단위 테스트 실행

## 단위 테스트 실행

### 1. 전체 단위 테스트 실행
\`\`\`bash
[전체 단위 테스트 실행 명령어]
# 예: mvn test, npm test, pytest tests/unit
\`\`\`

### 2. 테스트 결과 검토
- **예상**: [X]건 통과, 0건 실패
- **테스트 커버리지**: [예상 커버리지 비율]
- **테스트 리포트 위치**: [테스트 리포트 경로]
- **기능 테스트 증거**: [변경된 요구사항/스토리를 직접 검증하는 테스트 클래스 또는 메서드 나열]
- **회귀 증거**: [기존 동작이 여전히 통과함을 입증하는 명령어 또는 스위트 나열]

### 3. 실패 테스트 수정
테스트 실패 시:
1. [위치]의 테스트 출력 검토
2. 실패 테스트 케이스 식별
3. 코드 이슈 수정
4. 모든 테스트 통과할 때까지 재실행
```

---

## Step 4: 통합 테스트 지침 생성

`aidlc-docs/construction/build-and-test/integration-test-instructions.md` 생성:

```markdown
# 통합 테스트 지침

## 목적
유닛/서비스 간 상호작용을 테스트하여 올바르게 함께 작동하는지 확인.

## 테스트 시나리오

### 시나리오 1: [Unit A] → [Unit B] 통합
- **설명**: [테스트 대상]
- **설정**: [필요한 테스트 환경 설정]
- **테스트 단계**: [단계별 테스트 실행]
- **예상 결과**: [기대되는 결과]
- **정리**: [테스트 후 정리 방법]

### 시나리오 2: [Unit B] → [Unit C] 통합
[동일 구조]

## 통합 테스트 환경 설정

### 1. 필요 서비스 시작
\`\`\`bash
[서비스 시작 명령어]
# 예: docker-compose up, 테스트 데이터베이스 시작
\`\`\`

### 2. 서비스 엔드포인트 설정
\`\`\`bash
[엔드포인트 설정 명령어]
# 예: export API_URL=http://localhost:8080
\`\`\`

## 통합 테스트 실행

### 1. 통합 테스트 스위트 실행
\`\`\`bash
[통합 테스트 실행 명령어]
# 예: mvn integration-test, npm run test:integration
\`\`\`

### 2. 서비스 상호작용 검증
- **테스트 시나리오**: [주요 통합 테스트 시나리오 나열]
- **예상 결과**: [기대되는 결과 설명]
- **로그 위치**: [로그 확인 위치]
- **요구사항/스토리 커버리지**: [이 시나리오에서 검증되는 요구사항 또는 스토리 나열]

### 3. 정리
\`\`\`bash
[테스트 환경 정리 명령어]
# 예: docker-compose down, 테스트 서비스 중지
\`\`\`
```

---

## Step 5: 성능 테스트 지침 생성 (해당 시)

`aidlc-docs/construction/build-and-test/performance-test-instructions.md` 생성:

```markdown
# 성능 테스트 지침

## 목적
부하 환경에서의 시스템 성능을 검증하여 요구사항 충족 확인.

## 성능 요구사항
- **응답 시간**: [Y]% 요청에 대해 < [X]ms
- **처리량**: 초당 [X]건 요청
- **동시 사용자**: [X]명 동시 사용자 지원
- **에러율**: < [X]%

## 성능 테스트 환경 설정

### 1. 테스트 환경 준비
\`\`\`bash
[성능 테스트 설정 명령어]
# 예: 서비스 스케일링, 로드밸런서 설정
\`\`\`

### 2. 테스트 파라미터 설정
- **테스트 기간**: [X]분
- **램프업 시간**: [X]초
- **가상 사용자**: [X]명

## 성능 테스트 실행

### 1. 부하 테스트 실행
\`\`\`bash
[부하 테스트 실행 명령어]
# 예: jmeter -n -t test.jmx, k6 run script.js
\`\`\`

### 2. 스트레스 테스트 실행
\`\`\`bash
[스트레스 테스트 실행 명령어]
# 예: 실패 시점까지 부하를 점진적으로 증가
\`\`\`

### 3. 성능 결과 분석
- **응답 시간**: [실제 vs 예상]
- **처리량**: [실제 vs 예상]
- **에러율**: [실제 vs 예상]
- **병목 지점**: [식별된 병목 지점]
- **결과 위치**: [성능 리포트 경로]

## 성능 최적화

성능이 요구사항을 충족하지 못하는 경우:
1. 테스트 결과에서 병목 지점 식별
2. 코드/쿼리/설정 최적화
3. 개선 사항 검증을 위한 테스트 재실행
```

---

## Step 6: 추가 테스트 지침 생성 (필요 시)

프로젝트 요구사항에 따라 추가 테스트 지침 파일 생성:

### 계약 테스트 (마이크로서비스용)
`aidlc-docs/construction/build-and-test/contract-test-instructions.md` 생성:
- 서비스 간 API 계약 검증
- 소비자 주도 계약 테스트
- 스키마 검증

### 보안 테스트
`aidlc-docs/construction/build-and-test/security-test-instructions.md` 생성:
- 취약점 스캔
- 의존성 보안 점검
- 인증/권한 부여 테스트
- 입력 검증 테스트

### E2E 테스트
`aidlc-docs/construction/build-and-test/e2e-test-instructions.md` 생성:
- 완전한 사용자 워크플로우 테스트
- 교차 서비스 시나리오
- UI 테스트 (해당 시)

---

## Step 7: 테스트 요약 생성

`aidlc-docs/construction/build-and-test/build-and-test-summary.md` 생성:

```markdown
# 빌드 및 테스트 요약

## 빌드 상태
- **빌드 도구**: [도구명]
- **빌드 상태**: [성공/실패]
- **빌드 아티팩트**: [아티팩트 나열]
- **빌드 시간**: [소요 시간]

## 테스트 실행 요약

### 단위 테스트
- **전체 테스트**: [X]건
- **통과**: [X]건
- **실패**: [X]건
- **커버리지**: [X]%
- **상태**: [통과/실패]

### 통합 테스트
- **테스트 시나리오**: [X]건
- **통과**: [X]건
- **실패**: [X]건
- **상태**: [통과/실패]

### 성능 테스트
- **응답 시간**: [실제] (목표: [예상])
- **처리량**: [실제] (목표: [예상])
- **에러율**: [실제] (목표: [예상])
- **상태**: [통과/실패]

### 추가 테스트
- **계약 테스트**: [통과/실패/N/A]
- **보안 테스트**: [통과/실패/N/A]
- **E2E 테스트**: [통과/실패/N/A]

## 요구사항 검증 요약

| 요구사항/스토리 | 인수 기준 또는 계약 | 테스트 증거 | 명령어 | 결과 | goals.json 목표 ID |
| --- | --- | --- | --- | --- | --- |
| R-[ID]/S-[ID] | [관찰 가능한 조건] | [테스트 클래스, 메서드, 리포트 또는 시나리오] | [실행된 명령어] | [통과/실패/N/A] | [G0XX] |

### 검증 참고사항
- **기능 테스트**: [새로 구현되거나 변경된 동작을 직접 검증하는 테스트]
- **회귀 테스트**: [통과한 기존 스위트 또는 명령어]
- **N/A 항목**: [자동화 증거 없는 요구사항/스토리 및 사유]
- **수동 점검**: [수행된 수동 검증 (있는 경우)]

## 전체 상태
- **빌드**: [성공/실패]
- **전체 테스트**: [통과/실패]
- **요구사항 검증**: [완료/미완료]
- **운영 준비**: [Yes/No]

## 다음 단계
[모두 통과 시]: 배포 계획을 위한 Operations 단계로 진행 준비 완료
[실패 또는 미완료 검증 시]: 실패 테스트 해결 또는 누락된
검증 증거 보완 후 재빌드
```

> **Enhancement B — Goal-Evidence Cross-Mapping**
>
> A `goals.json Goal ID` column is added to the Requirement Verification Summary table.
>
> #### Bidirectional Mapping Rules
> 1. The value of the `Result` column in the Requirement Verification Summary must match the corresponding goal's `evidence` field in goals.json.
> 2. Cross-verify that every requirement (R-[ID]/S-[ID]) has its verification evidence synced back to goals.json.
> 3. Discrepancies block the completion of the Build and Test stage.
>
> #### ledger.jsonl Logging Rules
> - Write a `goal_checkpointed` event to ledger.jsonl when the summary is finalized.
> - Event format: `{"eventId":"UUID","event":"goal_checkpointed","goalId":"G0XX","status":"complete","evidence":"R-[ID] verified: [summary]","timestamp":"ISO_DATE"}`

---

## Step 7.1: Evidence Enforcement Integration

> **Origin**: Integrated from B-system Goal-Driven Execution  
> **Purpose**: Auto-sync test outcomes with goals.json state to prevent verification bypass.

Updating the goal's `evidence` field in `goals.json` with concrete test results is **MANDATORY** after all tests run.

#### Enforcement Rules

1. **Mandatory Evidence Updates**: After verification passes, write the following properties into the goal's `evidence` field:
   - Test execution command (e.g., `mvn test -pl module-a`).
   - Pass count (e.g., `42/42 tests passed`).
   - Coverage (e.g., `line coverage 87%`).
   - Assertion confirmation (e.g., `OrderValidationTest.shouldRejectInvalidStatus PASSED`).

2. **Standard Evidence Format**:
   ```
   Command: [command]
   Pass Rate: [passed]/[total] tests
   Coverage: [coverage]%
   Assertions: [assertion1] PASSED, [assertion2] PASSED
   ```

3. **Auto Transition on Failures**:
   - Mark goals as `review_blocked` when test failures are detected.
   - Write failure logs and test names in `evidence`.
   - Log `goal_checkpointed` (review_blocked) and `review_blockers_recorded` events.

4. **Sub-Blocker Goal Injection**:
   - Inject a child goal with `steering.blockedGoalId` for each failing test.
   - Child goal structure:
     ```json
     {
       "id": "G0XX",
       "title": "Fix blocker: [test name]",
       "objective": "Resolve [failure root cause] and re-run",
       "status": "pending",
       "createdAt": "ISO_DATE",
       "updatedAt": "ISO_DATE",
       "evidence": "",
       "steering": {
         "kind": "review_blocker",
         "blockedGoalId": "[parent goal ID]"
       }
     }
     ```
   - Re-transition parent status to `active` and re-verify only when all blocker child goals resolve to `complete`.

5. **Cross-References**:
   - State transitions: `../micro-loop/goal-driven-execution.md`
   - goals.json schema: `../schemas/goals-schema.md`

---

## Step 8: 상태 추적 업데이트

`aidlc-docs/aidlc-state.md` 업데이트:
- Build and Test 단계를 완료로 표시
- 현재 상태 업데이트

---

## Step 9: 사용자에게 결과 제시

다음 구조의 완료 메시지 제시:
     1. **완료 공지** (필수): 항상 이것으로 시작:

```markdown
# 🔨 Build and Test Complete
```

     2. **AI 요약** (선택): 빌드 및 테스트 결과의 구조화된 불릿 포인트 요약 제공
        - 형식: "빌드 및 테스트가 다음 결과로 완료되었습니다:"
        - 빌드 상태 및 아티팩트 나열
        - 카테고리별 테스트 결과 나열 (단위, 통합, 성능 등)
        - 요구사항/스토리 검증 상태 및 주요 테스트 증거 나열
        - 생성된 지침 파일 나열
        - 워크플로우 지시 포함 금지 ("검토해 주세요", "알려주세요", "다음 단계로 진행", "진행하기 전에")
        - 사실적이고 내용 중심으로 유지
     3. **포맷된 워크플로우 메시지** (필수): 항상 다음 정확한 형식으로 종료:

```markdown
> **📋 <u>**REVIEW REQUIRED:**</u>**  
> 빌드 및 테스트 요약을 검토하세요: `aidlc-docs/construction/build-and-test/build-and-test-summary.md`



> **🚀 <u>**WHAT'S NEXT?**</u>**
>
> **선택 가능:**
>
> 🔧 **변경 요청** - 검토 결과에 따라 빌드 및 테스트 지침 수정 요청
> ✅ **승인 및 계속** - 빌드 및 테스트 결과 승인 후 **Operations**로 진행

---
```

---

## Step 10: 상호작용 기록

**필수**: `aidlc-docs/audit.md`에 단계 완료 기록:

```markdown
## Build and Test Stage
**Timestamp**: [ISO 타임스탬프]
**Build Status**: [성공/실패]
**Test Status**: [통과/실패]
**Requirement Verification Status**: [완료/미완료]
**Requirement Verification Evidence**:
- [요구사항/스토리] -> [테스트 증거] -> [통과/실패/N/A]
**Goal Evidence Sync Status**: [goals.json 연동 완료/미완료]
**Files Generated**:
- build-instructions.md
- unit-test-instructions.md
- integration-test-instructions.md
- performance-test-instructions.md
- build-and-test-summary.md

---
```
