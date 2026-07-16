# UoW 06 — 기존 정규화 및 Evidence Graph 제거

## 목적

모든 소비처 이전이 끝난 레거시 정규화 파이프라인과 evidence graph 구현을 완전히 삭제함.

## 선행 조건

- UoW 05 완료
- 신규 경로의 golden 및 통합 테스트 통과

## 제거 대상

- `NormalizationService`
- `RuleBasedConditionNormalizer`
- `CandidateChunkGenerator`
- `CandidateChunkValidator`
- `GraphContextSelector`
- `CandidateChunk`
- `NormalizedResult`
- `ValidationEvidenceGraph`
- `ValidationEvidenceGraphBuilder`
- 레거시 `GraphNode`, `GraphEdge` 및 관련 enum
- 더 이상 소비되지 않는 `ValidationCandidate` 경로

`ValidationCandidate`는 다른 extractor가 사용할 수 있으므로 참조가 완전히 사라진 경우에만 삭제함.

## 작업 범위

- production 및 test 참조 제거
- 불필요한 constructor overload와 adapter 제거
- 레거시 warning code 유지 여부 정리
- 문서와 workflow 설명을 최종 구조로 갱신
- dead code 및 중복 fixture 제거

## 검증

- 제거 대상 심볼에 대한 production 참조 0건
- `ValidationEvidenceGraphBuilder` 호출 0건
- `NormalizationService.normalize()` 호출 0건
- `CandidateChunk` 생성 0건
- 전체 컴파일 및 테스트
- 대표 저장소 golden regression
- 분석 성능 및 그래프 생성 횟수 비교

## 완료 조건

- 제거 대상 클래스가 소스 트리에 존재하지 않음.
- `FactCodeGraph` 외 신규 내부 코드 그래프가 없음.
- 실행 흐름 규칙은 모두 `GraphRule`을 통해 출력됨.
- 요청 스키마 제약은 request binding 경로를 통해 출력됨.
- 기존 산출물 계약이 유지되거나 승인된 변경으로 대체됨.
- 전체 회귀 테스트가 통과함.

