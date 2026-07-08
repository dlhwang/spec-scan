# Technology Stack

## Programming Languages
- Java - 21 - 애플리케이션 구현 및 실행

## Frameworks
- Spoon Core - 11.2.0 - Java 소스 AST 파싱 및 정적 분석
- Swagger Models - 2.0.10 - OpenAPI 객체 모델 표현
- Swagger Annotations - 2.0.10 - OpenAPI 애노테이션 타입 지원
- Spring Framework - 6.2.6 - Spring MVC 애노테이션 해석
- Jakarta WS RS API - 3.1.0 - Jakarta REST 애노테이션 해석
- Javax WS RS API - 2.1.1 - 레거시 JAX-RS 애노테이션 해석
- Jakarta Validation API - 3.0.2 - Jakarta Validation 애노테이션 처리
- Javax Validation API - 2.0.1.Final - 레거시 Validation 애노테이션 처리

## Infrastructure
- Docker - 이미지 기반 배포
- Alpine Linux - 컨테이너 베이스 이미지
- Adoptium Temurin OpenJDK - 21.0.9+10 - 컨테이너 런타임 JDK

## Build Tools
- Maven - fat JAR 패키징과 테스트 실행
- Maven Assembly Plugin - 3.7.1 - `jar-with-dependencies` 생성
- Maven Compiler Plugin - 3.14.0 - Java 21 컴파일
- Maven Surefire Plugin - 3.5.3 - 테스트 실행 규칙

## Testing Tools
- JUnit Jupiter Params - 5.12.2 - 테스트 의존성으로 선언됨

## Logging and Serialization
- SLF4J API - 2.0.17 - 로깅 추상화
- Logback Classic - 1.5.18 - 로깅 구현체
- Jackson Annotations and Databind - 2.19.0 - JSON 직렬화
