# Component Inventory

## Application Packages
- `at.aau.serg.parsers` - CLI 진입점, 파서 생성, 프레임워크 감지, 분석 오케스트레이션
- `at.aau.serg.openapi` - OpenAPI 객체 생성과 파일 저장
- `at.aau.serg.codeanalysis` - 메서드 본문 분석 보조

## Infrastructure Packages
- 없음. 런타임 인프라를 직접 정의하는 코드 패키지는 포함되지 않았다.

## Shared Packages
- `at.aau.serg.frameworks` - 프레임워크 공통 추상화
- `at.aau.serg.frameworks.spring` - Spring MVC 지원
- `at.aau.serg.frameworks.jaxrs` - Jakarta/Javax JAX-RS 지원
- `at.aau.serg.frameworks.validation` - Validation 애노테이션 공급자
- `at.aau.serg.frameworks.utils` - 애노테이션 처리 유틸리티
- `at.aau.serg.interceptors` - 응답 코드 및 예외 맵핑 인터셉터
- `at.aau.serg.annotations` - 보조 애노테이션
- `at.aau.serg.util` - Spoon 보조 유틸리티

## Test Packages
- 배포 JAR 내부에서 별도 테스트 패키지는 확인되지 않았다.

## Total Count
- **Total Packages**: 11
- **Application**: 3
- **Infrastructure**: 0
- **Shared**: 8
- **Test**: 0

## Additional Inventory Notes
- **Primary Artifact**: `auto-oas.jar`
- **User Code Class Count**: 53
- **Distribution Style**: fat JAR with dependencies
