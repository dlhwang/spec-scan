# 기술 스택

## 언어와 런타임

- Java 21 toolchain: 애플리케이션, 정적 분석 모델 및 테스트 구현.
- JavaParser language level Java 17: 분석기 구문 해석 수준.
- Java 내장 `HttpServer`: 경량 Web UI/API 제공.

## 핵심 라이브러리

- JGit `6.8.0.202311291450-r`: GitHub 저장소 clone 및 checkout.
- Jackson Databind/YAML `2.15.2`: JSON/YAML 처리.
- JavaParser Core/Symbol Solver `3.25.7`: AST와 타입/메서드 해석.
- SLF4J Simple `1.7.36`: 런타임 로깅.

## 빌드와 테스트

- Gradle Wrapper, Java/Application 플러그인, Maven Central.
- 실행/패키징: `runDemo`, `runWeb`, `gitSpecScanJar`, `webSpecScanJar`.
- JUnit Jupiter `5.9.3`, AssertJ `3.24.2`, jqwik `1.7.4`.
- 품질 태스크: `test`, `ruleEvaluation`, `deliveryVerification`, `unit05MigrationVerification`.

## 인프라

- CDK, Terraform, CloudFormation 정의 없음.
- 로컬 임시 파일시스템을 작업공간과 산출물 저장소로 사용.
