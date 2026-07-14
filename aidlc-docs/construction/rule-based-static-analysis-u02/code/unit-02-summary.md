# Unit 02 후보와 해석 모델 구현 요약

## 결과

Unit 01 Fact Graph 위에 구조 후보와 의미 후보를 분리한 immutable candidate domain 및 검증 support를 병렬 도입했다. 기존 output model과 Rule engine은 변경하지 않았다.

## 구현 내용

- extraction, semantic, target의 독립 상태 enum
- `UNKNOWN`과 해석된 `OTHER` category 구분
- 조건 Fact별 `PredicateCandidate`
- Rule 평가별 `BusinessRuleCandidate`
- constraint kind 및 확인된 expected value만 저장하는 immutable constraint
- Fact node ID와 source range 기반 Evidence
- typed candidate diagnostic
- SHA-256 기반 결정적 predicate 및 business rule candidate ID
- status, category, ruleId, targetPath, diagnostic 조합 검증
- predicate 및 evidence dangling reference 검증
- duplicate ID 검출과 candidate ID 기준 안정 정렬
- Fact payload와 typed edge 기반의 보수적 Predicate 분류
- snippet 문자열 재파싱 없는 Evidence 변환
- jqwik identity 및 상태 invariant properties

## 범용성 경계

- package, class, method 이름으로 service/domain 의미를 판단하지 않는다.
- `PredicateTypeClassifier`는 Fact payload와 edge만 사용한다.
- 현재 Fact에 충분한 구조가 없으면 특정 의미를 추측하지 않고 `UNKNOWN`을 반환한다.
- Rule matching, target resolver, 구체 Rule과 output migration은 후속 Unit으로 남겼다.

## 검증 Evidence

- 신규 candidate tests: 12/12 통과
- example-based tests: 8개
- jqwik properties: 4개
- PBT tries: 500, 500, 200, 200
- PBT seed: JUnit XML에 각 property별 기록
- 전체 suite: 65/65 통과, 19 suites
- failures/errors/skips: 0/0/0
- 기존 output model 변경: 없음
- Unit 01 Fact domain 및 builder 변경: 없음
- 작업 범위 scoped `git diff --check`: 통과

## 검증 중 발견하고 수정한 결함

1. 최초 jqwik identity generator가 길이는 유효하지만 공백 문자열을 생성해 domain 사전조건을 위반했다. blocker Goal G009로 기록하고 영문 nonblank identity generator로 제한했다.
2. finite enum generator가 exhaustive mode에서 11회만 실행돼 승인된 200 tries를 충족하지 못했다. 상태 property를 randomized mode로 변경해 각각 200회 실행했다.
3. aggregate validator가 의미 후보 evidence만 검사하고 구조 후보 evidence를 검사하지 않던 누락을 수정했다.
4. 구조 후보의 graph ID와 condition node reference 검증을 aggregate 경계에 추가했다.

## 기존 작업트리 주의사항

`build.gradle`, Unit 01 산출물, `poc_report.md` 등 기존 미커밋 변경은 보존했다. 이번 Unit 변경 범위의 scoped diff check는 통과했다.

## PBT Compliance

- PBT-01~04: identity 및 상태·참조 invariant로 충족
- PBT-07: 유효 identity와 category domain generator 사용
- PBT-08: shrinking 유지 및 seed 기록
- PBT-09: 기존 jqwik 1.7.4 재사용
- PBT-10: example-based 8개와 property 4개 병행
