# 코드 품질 평가

## 테스트 상태

- 테스트 Java 소스 44개와 fixture 7개가 존재한다.
- 단위/통합 테스트는 ingestion부터 output/delivery까지 주요 계층을 포괄한다.
- jqwik 속성 테스트는 ID 결정성, 규칙 순서, 중복 제거 및 실패 격리를 검증한다.
- 상태 문서의 마지막 실행 근거는 Unit 03 전체 suite 82/82 통과다. 이번 역공학 단계에서도 별도 재실행해 현재 근거를 갱신해야 한다.
- line/branch coverage 도구는 구성되어 있지 않아 정량 coverage 비율은 알 수 없다.

## 긍정적 품질 지표

- ingestion, analysis application, domain, support 경계가 명확하다.
- fact/candidate/rule/output을 불변 모델과 validator로 보호한다.
- 규칙 실행 실패 격리, 순회 budget, deterministic ID 및 quality gate가 존재한다.
- golden/holdout/synthetic 데이터로 false positive와 migration 회귀를 검증한다.
- 임시 workspace가 `finally`에서 정리된다.

## 기술 부채와 위험

- `SpecScanDemoRunner`와 `GitExecutionSpecScanService`가 분석 orchestration을 중복해 실행 방식 간 drift 가능성이 있다.
- `GitExecutionSpecScanService.scan`과 `scanWithArtifacts`도 ingestion/scan/extraction을 중복한다.
- Web 요청의 `projectName`, `baseUrl`은 필수지만 실제 결과에 사용되지 않아 API 계약이 오해를 유발한다.
- Web server는 고정 4-thread pool이며 요청 timeout, 크기 제한, 인증, rate limit이 없다.
- GitHub 전용 URL 정책과 원격 clone은 네트워크 실패/대형 저장소에 취약하며 clone 크기 제한이 명시적이지 않다.
- `SpecScanDemoRunner` 일부 주석/콘솔 문자열에 과거 인코딩 손상 흔적이 있다.
- Java 21 toolchain과 JavaParser Java 17 language level의 지원 범위 차이를 명시하고 테스트할 필요가 있다.
- 정적 분석 모델이 많은 단일 Gradle 모듈에 집중되어 package 수준 결합도가 커질 수 있다.

## 도구화 공백

- lint/format/checkstyle, dependency vulnerability/license 자동 검사 없음.
- coverage report 및 최소 threshold 없음.
- CI 설정이 저장소에서 확인되지 않는다.
- Web API contract test와 동시 요청/대형 repository 성능 테스트가 부족하다.

## 종합 판단

핵심 분석 도메인과 회귀 테스트는 성숙도가 높지만, 전달 계층 통합, 운영 안전장치와 자동 품질 도구는 추가 설계가 필요하다. 보안 확장은 현재 비활성 결정이므로 보안 항목은 blocking finding이 아니라 후속 위험으로 기록한다.
