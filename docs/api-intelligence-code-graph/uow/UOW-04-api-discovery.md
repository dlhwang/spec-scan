# UOW-04 Spring MVC API 발견

## Goal

JavaParser syntax 분석으로 지원 Spring MVC endpoint를 저장소 특화 없이 결정적으로 발견한다.

## 선행 조건

UOW-01과 UOW-03 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/port/out/ApiDiscoveryPort.java`
- `src/main/java/io/atworks/apiintelligence/adapter/javaparser/JavaParserApiDiscoveryAdapter.java`
- `src/main/java/io/atworks/apiintelligence/adapter/javaparser/MappingAnnotationReader.java`
- `src/main/java/io/atworks/apiintelligence/adapter/javaparser/SourceRootParser.java`
- `src/test/resources/api-intelligence/fixture/**`
- 해당 discovery 테스트

## 필수 계약

- 클래스/메서드 수준 `RequestMapping`, GET, POST, PUT, PATCH, DELETE를 조합한다.
- 복수 path와 method는 각각 endpoint로 확장한다.
- path를 `/`로 정규화한다.
- request binding/DTO, response syntax type, handler source location을 반환한다.
- 결과는 method, path, controller FQCN, signature 순으로 정렬한다.
- symbol resolution 실패로 syntax상 발견한 API를 버리지 않는다.
- 해석 불가 type은 원문 이름과 diagnostic을 유지한다.
- API 없음은 빈 결과와 진단으로 반환하여 application이 `NO_SUPPORTED_API`로 판단하게 한다.

## 구현 절차

1. 모든 지원 annotation fixture를 만든다.
2. source roots를 stable path order로 parse한다.
3. controller/mapping과 path/method 조합을 읽는다.
4. request/response와 source location을 추출한다.
5. 결정적 API ID를 부여하고 정렬한다.

## 테스트와 완료 조건

- 모든 annotation, 빈/복수 path, class+method 조합
- request/response와 정확한 line range
- unresolved import에서도 endpoint 유지
- 반복 실행 stable ID/order
- repository 이름 또는 경로 하드코딩 없음

