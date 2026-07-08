# UOW-05 NFR Requirements Plan

## Unit Context
- Unit: `UOW-05`
- Title: Contract Assembly and Output
- Goal: final contract assembly와 output persistence에 필요한 비기능 요구사항과 tech stack 결정을 확정한다.

## Execution Checklist
- [ ] `UOW-05` functional design을 기준으로 핵심 NFR 축을 식별한다.
- [ ] 성능, 신뢰성, 추적성, 유지보수성 요구를 정리한다.
- [ ] output persistence와 artifact packaging 관련 tech stack 결정을 정리한다.
- [ ] `nfr-requirements.md`를 생성한다.
- [ ] `tech-stack-decisions.md`를 생성한다.

## NFR Focus
- final assembly throughput and latency
- partial success and failure isolation
- artifact traceability and reproducibility
- file-based output reliability
- review/debug artifact retention
- Java-based implementation and serializer/tooling choices

## Planning Questions

## Question 1
`UOW-05`의 성능 목표는 어느 수준으로 둘까요?

A) PoC 수준이라 명시적 수치 없이 합리적 완료 시간만 요구

B) 샘플 repository 기준 endpoint 수십 개를 수 초 내 처리 목표

C) B안 + artifact generation과 manifest generation도 단계별 시간 예산을 둔다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: B) 샘플 repository 기준 endpoint 수십 개를 수 초 내 처리 목표

PoC 단계이므로 과도한 성능 수치나 단계별 시간 예산까지는 필요하지 않다. 다만 샘플 repository 기준 endpoint 수십 개에 대해 final assembly, OpenAPI generation, ApiCondition JSON generation, manifest generation이 수 초 내 완료되는 것을 목표로 둔다.

LLM 호출은 UOW-04 범위이므로 UOW-05 성능 목표에는 포함하지 않는다. UOW-05는 이미 검증된 direct condition과 normalization result를 입력으로 받아 deterministic하게 산출물을 조립하는 단계로 본다.

## Question 2
출력 신뢰성/재현성은 어느 수준까지 보장할까요?

A) 최종 산출물만 재생성 가능하면 충분

B) 최종 산출물 + manifest + sourceRunId 수준 재현성 보장

C) B안 + chunk/retry/rejection/conflict artifact까지 포함한 end-to-end 재현성 보장

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + chunk/retry/rejection/conflict artifact까지 포함한 end-to-end 재현성 보장

최종 OpenAPI와 ApiCondition JSON만 재생성 가능하면 부족하다. UOW-05는 LLM normalization 결과를 포함한 최종 산출물 조립 단계이므로, sourceRunId, manifest, accepted/rejected normalization, conflict record, retry summary, chunk reference까지 추적 가능해야 한다.

단, UOW-05가 raw LLM 호출을 다시 수행하는 것은 아니며, 이전 단계에서 생성된 chunk/retry/rejection/conflict 정보를 artifact로 연결해 end-to-end 재현성을 보장한다.

## Question 3
partial success 상황에서 가용성 요구는 어떻게 둘까요?

A) 일부 실패가 있으면 전체 run을 failed로 간주

B) 성공 endpoint 산출물을 우선 제공하고 실패는 부가 정보로 제공

C) B안 + manifest에서 success/failure/skipped/rejected/conflict를 명확히 분리하고 downstream이 이를 기계적으로 읽을 수 있게 한다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + manifest에서 success/failure/skipped/rejected/conflict를 명확히 분리하고 downstream이 이를 기계적으로 읽을 수 있게 한다

일부 endpoint 또는 일부 condition 조립 실패가 전체 run 실패로 이어져서는 안 된다. 성공한 endpoint의 OpenAPI와 ApiCondition 산출물은 제공하고, 실패한 endpoint와 rejected normalization, skipped candidate, conflict record는 manifest에서 분리해 제공한다.

manifest는 사람이 읽을 수 있을 뿐 아니라 downstream 도구가 기계적으로 해석할 수 있는 안정적인 schema를 가져야 한다.

## Question 4
보안/데이터 최소화 측면에서 debug artifact 보존 범위는 어디까지 허용할까요?

A) 최대한 많이 남겨도 된다

B) candidate evidence와 source trace 중심으로 제한한다

C) B안 + 민감할 수 있는 raw snippet/response는 보존하되 전체 소스 dump나 불필요한 중복 저장은 금지한다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + 민감할 수 있는 raw snippet/response는 보존하되 전체 소스 dump나 불필요한 중복 저장은 금지한다

debug artifact는 candidate evidence와 source trace 중심으로 제한한다. PoC 검토와 재현성을 위해 raw snippet, rejected response, conflict evidence는 보존할 수 있다.

다만 GitHub repository 전체 소스 dump, 불필요한 중복 snippet, 분석과 무관한 파일 내용은 저장하지 않는다. artifact에는 필요한 최소 evidence만 포함하고, source trace를 통해 원본 위치를 추적할 수 있게 한다.

## Question 5
`UOW-05`의 유지보수성과 tech stack 결정은 어느 수준으로 고정할까요?

A) Jackson 등 일반적 Java JSON tooling만 쓰면 된다

B) Java + Jackson + 명시적 schema/manifest model + file result store를 기본으로 둔다

C) B안 + serializer determinism, stable field ordering, 향후 DB/API 저장 확장을 고려한 port 계약까지 명시한다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + serializer determinism, stable field ordering, 향후 DB/API 저장 확장을 고려한 port 계약까지 명시한다

Java 기반 구현을 전제로 하며, JSON serialization은 Jackson을 기본 tooling으로 둔다. OpenAPI, ApiCondition, manifest, debug artifact는 명시적 model을 가진다.

snapshot test와 artifact diff 안정성을 위해 serializer determinism과 stable field ordering을 명시한다. output persistence는 file result store를 기본 구현으로 두되, `ResultStorePort` 계약을 유지해 향후 DB 저장 또는 API 서버 저장으로 확장 가능하게 한다.

## Question 6
관측성과 품질 검증 요구는 어디까지 둘까요?

A) 최종 output 확인 정도면 충분

B) artifact count, failed endpoint count, rejected normalization count 정도의 summary metric 필요

C) B안 + conflict count, skipped candidate count, generation duration, artifact-by-artifact validation result까지 남긴다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + conflict count, skipped candidate count, generation duration, artifact-by-artifact validation result까지 남긴다

최종 output 생성 여부만으로는 품질을 판단하기 어렵다. manifest 또는 generation summary에는 artifact count, failed endpoint count, rejected normalization count를 포함한다.

추가로 conflict count, skipped candidate count, generation duration, artifact-by-artifact validation result를 남긴다. 이 정보는 데모 검증, 회귀 테스트, 실패 원인 추적, downstream 처리 판단에 사용된다.

## Approval Notes
- 모든 `[Answer]:` 항목이 채워진 뒤에만 `UOW-05` NFR requirements 산출물 생성을 진행한다.
- 모호한 답변이 있으면 follow-up question을 같은 문서에 추가한다.
- 답변 완료 후 `aidlc-docs/construction/uow-05/nfr-requirements/` 아래 산출물을 생성한다.

## Answer Analysis
- 성능 목표는 샘플 repository의 endpoint 수십 개 기준으로 final assembly와 artifact generation을 수 초 내 처리하는 PoC 수준 목표로 확정되었다.
- 재현성은 sourceRunId, manifest, accepted/rejected normalization, conflict, retry, chunk reference까지 연결하는 end-to-end 수준으로 요구된다.
- partial success는 manifest에서 success/failure/skipped/rejected/conflict를 기계적으로 분리해 제공하는 방향으로 확정되었다.
- debug artifact는 evidence/trace 중심 최소 보존 원칙을 따르되, raw snippet/response는 필요한 범위에서 허용하고 전체 소스 dump는 금지한다.
- tech stack은 Java + Jackson + explicit models + file result store를 기본으로 하고 serializer determinism 및 stable field ordering을 명시해야 한다.
- observability는 artifact count, failed endpoint count, rejected normalization count 외에 conflict/skipped/duration/artifact validation result까지 포함해야 한다.
- 답변 간 충돌이나 모호성은 없어 follow-up question은 필요하지 않다.
