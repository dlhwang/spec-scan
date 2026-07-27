# YAML Rule Engine PoC — 요구사항 검증 질문

아래 질문은 기존 Java 알고리즘을 어느 수준까지 YAML로 외부화할지, PoC를 어디에서 종료할지, 성공을 어떤 증거로 판단할지를 확정하기 위한 것입니다. 각 질문의 `[Answer]:` 뒤에 선택지를 입력하고, 필요한 경우 `[Rationale]:`에 이유를 적어 주세요.

## Trade-off Question 1: PoC 종료 범위

### Option A: Phase 2 Go/No-Go까지 (권장)

Golden Corpus 안전망, 최소 프리미티브 실행기, 대표 룰 5개 이관과 Go/No-Go 판정까지 수행합니다. Hot-reload와 운영 도구는 후속 단계로 남깁니다.

- **Pros**: 핵심 가설인 YAML 표현력과 회귀 안전성을 가장 빠르게 검증할 수 있음
- **Cons**: 운영 중 무중단 룰 교체의 체감 데모는 포함하지 않음
- **Impact Analysis**:
  - Compatibility: 기존 엔진과 병행 실행하여 하위 호환 가능
  - Performance: 실제 이관 룰 기준 오버헤드 측정 가능
  - Maintainability: 실패 시 Step 2 수준으로 축소하기 쉬움
  - Implementation Cost: 중간
  - Risk: 중간

### Option B: Phase 3 트레이서와 작성 가이드까지

Option A에 YAML 실행 트레이스와 룰 작성자용 예제 카탈로그를 추가합니다.

- **Pros**: 룰 작성과 디버깅의 실제 운영성을 함께 평가 가능
- **Cons**: 표현력 검증이 실패하면 도구 투자 일부가 낭비될 수 있음
- **Impact Analysis**:
  - Compatibility: Option A와 동일
  - Performance: 트레이싱 비활성화 경로를 별도 검증해야 함
  - Maintainability: 룰 작성 경험은 개선되나 도구 표면적 증가
  - Implementation Cost: 높음
  - Risk: 중간 이상

### Option C: Phase 4 버저닝과 Hot-reload까지

Rule Registry 버저닝, Golden Corpus CI Gate, 무중단 룰팩 교체 데모까지 PoC에 포함합니다.

- **Pros**: 배포 없는 룰 변경이라는 최종 체감 효과까지 시연 가능
- **Cons**: Step 3 표현력이 검증되기 전에 운영 메커니즘에 투자할 위험이 큼
- **Impact Analysis**:
  - Compatibility: 멀티 버전과 롤백 계약이 추가로 필요
  - Performance: reload 동시성과 캐시 무효화 검증 필요
  - Maintainability: 운영 복잡도 증가
  - Implementation Cost: 매우 높음
  - Risk: 높음

### Option X: 기타

원하는 종료 범위를 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 

## Trade-off Question 2: YAML 외부화의 아키텍처 경계

### Option A: 제한된 선언형 프리미티브 + Java 런타임 불변식 (권장)

`match`, `follow`, `state transition`, `quantifier`, `evaluation`은 YAML에서 조합하되, 그래프 인덱스, 순회 스케줄러, 결정론, budget, dispatch precedence, suppression, candidate identity와 evidence 무결성은 Java 런타임에 남깁니다.

- **Pros**: 알고리즘 조합의 변경성과 엔진 안전성을 함께 확보
- **Cons**: 모든 새 알고리즘을 YAML만으로 표현할 수 있다는 보장은 없음
- **Impact Analysis**:
  - Compatibility: 기존 Rule Engine 계약을 보존하기 가장 쉬움
  - Performance: 핵심 순회와 인덱싱을 Java에서 최적화 가능
  - Maintainability: DSL 표면을 제한할 수 있음
  - Implementation Cost: 중간 이상
  - Risk: 중간

### Option B: Semantic Layer의 dispatch와 suppression까지 YAML 외부화

분류기 선택, 우선순위, suppression chain도 YAML 그래프로 선언합니다.

- **Pros**: Semantic Layer 변경 대부분을 룰팩 배포로 처리 가능
- **Cons**: 실행 순서, 충돌 해소, 재귀와 종료 보장을 YAML 언어가 책임져야 함
- **Impact Analysis**:
  - Compatibility: 후보 순서와 suppression 결과가 달라질 가능성 큼
  - Performance: 동적 dispatch 해석 비용 증가 가능
  - Maintainability: 사실상 그래프 질의 언어에 가까워짐
  - Implementation Cost: 높음
  - Risk: 높음

### Option C: Kind 템플릿까지만 외부화

`TaintFlow`, `OriginTrace`, `GuardClassification` 같은 Java 템플릿을 두고 YAML은 kind와 파라미터만 선택합니다.

- **Pros**: 가장 안전하고 구현이 단순함
- **Cons**: 탐색 알고리즘 자체를 YAML에서 개선하려는 원래 목표를 충분히 검증하지 못함
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 기존과 거의 동일
  - Maintainability: 단순하지만 새 kind 추가에는 엔진 배포 필요
  - Implementation Cost: 낮음
  - Risk: 낮음

### Option X: 기타

YAML과 Java의 책임 경계를 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 

## Question 3: 대표 이관 룰 포트폴리오

A) 난이도와 알고리즘 유형이 다른 5개를 선정한다: `StandardGuard`, `PasswordEncoder`, `OptionalLookup`, `BinaryConstraint`, `CompositeValidation` (권장)

B) 빠른 성공 확인을 위해 `StandardGuard`, `PasswordEncoder`, `OptionalLookup` 3개만 선정한다

C) 보안 시나리오 중심으로 `StandardGuard`, `PasswordEncoder`, `AuthorizationGuard`를 선정한다

X) 기타 (아래 `[Answer]:` 태그 뒤에 대상 또는 선정 기준을 설명해 주세요)

[Answer]: A

## Trade-off Question 4: Golden Corpus 동등성 기준

### Option A: 외부 관찰 결과의 완전 동등성 (권장)

이관 전후의 rule ID, category, normalized constraint, target, evidence, diagnostic, suppression 결과가 동일해야 하며 차이 0건을 요구합니다. 실행 트레이스 같은 신규 내부 진단은 비교에서 제외합니다.

- **Pros**: 회귀 없음에 대한 가장 강한 증거
- **Cons**: 기존의 비본질적 순서나 식별자 결합까지 드러나 이관 비용이 늘 수 있음
- **Impact Analysis**:
  - Compatibility: 가장 강함
  - Performance: 별도 기준 필요
  - Maintainability: snapshot 관리 비용 증가
  - Implementation Cost: 중간 이상
  - Risk: 낮음

### Option B: 의미 동등성

최종 탐지 여부, category, target, operator만 같으면 동등한 것으로 보고 evidence 순서나 diagnostic 차이는 허용합니다.

- **Pros**: 구현 세부에 덜 결합되고 이관 속도가 빠름
- **Cons**: 증거 추적성과 suppression 회귀를 놓칠 수 있음
- **Impact Analysis**:
  - Compatibility: 중간
  - Performance: 별도 기준 필요
  - Maintainability: 비교기가 단순함
  - Implementation Cost: 낮음
  - Risk: 중간 이상

### Option C: 허용된 차이 목록 기반 동등성

기본은 완전 동등성으로 두되, 사전 승인된 필드별 차이만 allowlist로 허용합니다.

- **Pros**: 엄격성과 현실적인 이관을 절충
- **Cons**: allowlist가 누적되면 회귀를 숨기는 통로가 될 수 있음
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 별도 기준 필요
  - Maintainability: 예외 목록의 수명주기 관리 필요
  - Implementation Cost: 중간
  - Risk: 중간

### Option X: 기타

원하는 동등성 기준을 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 

## Trade-off Question 5: 표현 불가 케이스의 탈출구 정책

### Option A: 임의 표현식 금지, 반복 패턴만 primitive 또는 kind로 승격 (권장)

`custom_expression`, script, reflection 기반 함수 호출을 허용하지 않습니다. 이관 중 표현 불가 사례는 백로그에 모으고 반복성과 일반성을 검토한 뒤 Java primitive 또는 kind로 승격합니다.

- **Pros**: DSL이 튜링 완전 언어로 팽창하는 것을 방지
- **Cons**: 일부 룰은 YAML 이관이 지연되거나 불가능할 수 있음
- **Impact Analysis**:
  - Compatibility: 안전함
  - Performance: 예측 가능
  - Maintainability: 장기적으로 가장 통제 가능
  - Implementation Cost: 초기 설계 비용 중간
  - Risk: 낮음

### Option B: 등록된 Java plugin 호출만 허용

YAML이 allowlist에 등록된 Java 확장 함수를 이름으로 호출할 수 있게 합니다.

- **Pros**: 복잡한 예외 룰을 수용하면서 임의 코드 실행은 막을 수 있음
- **Cons**: plugin API와 버전 호환성 관리가 필요하며 YAML-only 목표가 약해짐
- **Impact Analysis**:
  - Compatibility: plugin 버전에 의존
  - Performance: 구현별 편차 발생
  - Maintainability: 이중 확장 모델 관리 필요
  - Implementation Cost: 중간 이상
  - Risk: 중간

### Option C: Sandbox custom expression 허용

제한된 표현식 또는 스크립트를 YAML 안에서 실행합니다.

- **Pros**: 표현력이 가장 높음
- **Cons**: 보안, 종료 보장, 성능, 디버깅과 버전 호환 문제를 새로 해결해야 함
- **Impact Analysis**:
  - Compatibility: 예측하기 어려움
  - Performance: 편차 큼
  - Maintainability: DSL과 런타임 복잡도 급증
  - Implementation Cost: 매우 높음
  - Risk: 매우 높음

### Option X: 기타

허용할 탈출구 정책을 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 

## Question 6: PoC 성능 허용 기준

A) 동일 Golden Corpus 기준 전체 Rule Engine 평가 시간과 peak memory 증가를 각각 10% 이하로 제한한다 (권장)

B) 평가 시간과 peak memory 증가를 각각 25% 이하로 허용한다

C) 이번 PoC는 기능 동등성과 표현력만 판정하고 성능은 측정만 한다

X) 기타 (아래 `[Answer]:` 태그 뒤에 기준을 설명해 주세요)

[Answer]: C 

## Question 7: Security Baseline 확장

A) Yes — 모든 Security Baseline 규칙을 blocking constraint로 적용한다

B) No — PoC 범위에서는 Security Baseline 확장을 적용하지 않는다 (현재 프로젝트 설정 유지, 권장)

X) 기타 (아래 `[Answer]:` 태그 뒤에 적용 범위를 설명해 주세요)

[Answer]: B 

## Question 8: Property-Based Testing 확장

A) Yes — 상태 전이, 조합 연산자, 순회 종료와 직렬화 전체에 PBT 규칙을 적용한다

B) Partial — 순수 함수, YAML round-trip, 상태 전이와 결정론 불변식에만 적용한다 (현재 프로젝트 설정 유지, 권장)

C) No — 예제 기반 단위/통합 테스트만 사용한다

X) 기타 (아래 `[Answer]:` 태그 뒤에 적용 범위를 설명해 주세요)

[Answer]: C 

