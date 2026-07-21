# Technology Stack

Auto-OAS (SpecScan) 정적 분석 파이프라인 엔진에서 활용되는 언어, 프레임워크, 라이브러리 및 개발 환경 명세입니다.

---

## 1. Programming Language & Runtime

- **Language**: Java 21
- **Language Level**: `BLEEDING_EDGE` (JavaParser Language Level)
- **Features Used**:
  - Record Types (`record`)
  - Pattern Matching for `switch` & `instanceof`
  - Sealed Interfaces & Classes
  - Text Blocks (`"""..."""`)
  - Stream API & Lambda Expressions

---

## 2. Build & Dependency Management

- **Build System**: Gradle 9.0 (Gradle Wrapper `./gradlew`, `gradlew.bat`)
- **Key Gradle Plugins**:
  - `application` - CLI 및 런타임 실행 태스크 기동
  - `java` - 자바 컴파일 및 테스트 태스크 기동

---

## 3. Core Static Analysis & AST Engine

- **JavaParser (`com.github.javaparser:javaparser-core:3.25.7`)**:
  - AST(Abstract Syntax Tree) 생성 및 구문 순회 (Visitor Pattern)
  - 타입 레솔루션 (`javaparser-symbol-solver-core`)
- **Jackson (`com.fasterxml.jackson.core:jackson-databind`)**:
  - JSON 모델 직렬화/역직렬화 및 OpenAPI 3.0 YAML 변환 구조체 처리

---

## 4. Testing & Verification Framework

- **JUnit 5 (JUnit Jupiter 5.10.x)**: 단위 테스트 및 통합 테스트 구동 환경
- **AssertJ**: 유연한 검증 및 가독성 높은 어서션 라이브러리
- **Jqwik (1.9.2)**: Property-Based Testing (PBT) 프레임워크. 고유 불변식 및 그래프 비반복 정합성 자동 생성 검증

---

## 5. Web & Network Infrastructure

- **Sun Embedded HTTP Server (`com.sun.net.httpserver.HttpServer`)**:
  - 서드파티 무거운 웹 프레임워크(Spring Boot 런타임 엔진 자체)에 대한 의존성 없이, 경량화된 내장 자바 HTTP 서버로 분석 API 서빙.
