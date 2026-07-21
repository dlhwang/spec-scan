# API Intelligence Code Graph PoC 요구사항

## 1. 목적

Java 소스를 정적 분석하여 Spring MVC API와 관련 근거 코드를 찾고, 정규화된 코드 그래프와 소스 Evidence를 OpenAI에 전달하여 API별
Intelligence를 생성하는 PoC를 구현한다.

코드 그래프는 최종 사용자 산출물이 아니라 관련 코드를 찾고 Evidence를 구성하기 위한 내부 중간 표현이다.

> API를 시작점으로 관련 코드를 제한적으로 탐색하고 코드 관계와 근거 소스를 LLM에 제공하면, 전체 저장소를 직접 전달하는 방식보다 근거가 명확한 API
> Intelligence를 생성할 수 있다.

## 2. 처리 흐름

신규 기능은 다음 순서로 동작한다.

```text
Java Source
→ Spring MVC API 추출
→ API별 관련 코드 탐색
→ API별 코드 그래프 생성
→ 코드 그래프에서 관련 소스 Evidence 구성
→ 코드 그래프와 Evidence를 OpenAI에 전달
→ 구조화된 API Intelligence 수신
→ 응답 JSON Schema 및 Evidence 참조 검증
→ 코드 그래프와 분석 결과를 JSON 파일로 저장
→ API Intelligence 결과를 UI에 표시
```

- 코드 그래프는 Java 소스에서 확인된 타입, 메서드, 필드, 조건, Validation, 예외 및 호출 관계로 구성한다.
- OpenAI 입력에는 API별 코드 그래프와 그 그래프가 가리키는 최소 소스 Evidence를 함께 포함한다.
- OpenAI 출력은 PreCondition, ResponseAssertion, BusinessRule, Exception 및 Additional Analysis로 구성한다.
- OpenAI 출력에 연결된 Evidence가 실제 입력 Evidence에 존재하는지 검증한 후 저장하고 표시한다.

## 3. 기존 기능과의 분리

- 신규 기능은 기존 `/api/scan` 파이프라인 및 요청·응답 모델과 완전히 분리한다.
- 신규 기능은 별도의 HTTP API 경로, 모델 및 Application Service를 사용한다.
- 기존 `/api/scan`의 동작과 UI는 유지한다.
- 저장소 취득이나 Java AST 처리 같은 일반 기술 요소는 독립성을 해치지 않는 범위에서 재사용할 수 있다.
- 신규 기능의 실패가 기존 기능 실행에 영향을 주어서는 안 된다.

## 4. 지원 범위

### 4.1 보장 범위

- Java, Spring Boot 3.x, Spring MVC
- `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`
- Bean Validation
- Controller에서 직접 호출하는 Service
- 일반적인 `if`, `switch`, 조건식 및 명시적 예외 발생
- 요청 DTO, 응답 타입, Validator 및 Exception 관련 코드

특정 저장소의 경로, 패키지, 클래스 또는 도메인을 하드코딩하지 않는다. `D:\workspace\real-estate\RealEstate`는 대표 검증 프로젝트일 뿐 기능의
전제조건이 아니다.

### 4.2 제외 범위

- Spring WebFlux, JAX-RS, Raw Servlet 직접 등록
- Reflection 또는 런타임 프록시 기반 동적 라우팅 분석
- 모든 Java 문법과 Java 웹 프레임워크 지원
- 데이터베이스 쿼리와 모든 외부 라이브러리의 의미 분석
- 그래프 데이터베이스
- 코드 그래프 UI 시각화
- 자동 테스트 코드 생성
- 데이터베이스 기반 분석 이력 관리

지원하지 않는 구조는 추측하지 않고 미지원 또는 분석 불가 사유를 남긴다.

## 5. 사용자 입력

신규 UI에서 Git 저장소 또는 로컬 프로젝트를 입력할 수 있어야 한다.

### 5.1 Git 저장소

- Git 저장소 URL은 필수다.
- branch, tag 또는 commit SHA 형식의 revision을 선택적으로 입력할 수 있다.
- revision을 생략하면 원격 저장소 기본 브랜치의 HEAD를 사용한다.
- clone 실패와 존재하지 않는 revision을 구분하여 표시한다.

### 5.2 로컬 프로젝트

- 로컬 프로젝트 디렉터리 경로는 필수다.
- 존재하지 않거나 읽을 수 없는 경로는 분석 전에 오류로 표시한다.

## 6. API 추출

입력 프로젝트에서 다음 정보를 추출한다.

- HTTP Method와 Path
- Controller 클래스와 Handler Method
- Request Parameter와 Request DTO
- Response Type

클래스 및 메서드 수준 Mapping annotation을 조합하여 경로를 해석한다.

## 7. 코드 탐색과 내부 코드 그래프

각 API Handler Method를 시작점으로 Controller, 직접 호출되는 Service, Validator, 요청·응답 DTO, Bean Validation, 명시적
조건식, 예외 발생 코드 및 반환 코드를 탐색한다.

최소 노드 종류:

- API, METHOD, TYPE, FIELD, VALIDATION, CONDITION, EXCEPTION

최소 간선 종류:

- DECLARES, CALLS, USES_TYPE, BINDS_REQUEST, VALIDATES, CHECKS, RETURNS, THROWS

코드 그래프는 UI에 표시하지 않지만 실행 결과 폴더에 JSON으로 저장한다.

## 8. Evidence

LLM에는 전체 저장소를 전달하지 않는다. API별 정규화 코드 그래프와 관련된 최소 소스 코드만 전달한다.

Evidence는 다음 정보를 포함한다.

- API 식별 정보와 호출 경로
- 조건식과 Validation annotation
- 예외 발생 코드와 반환 코드
- 원본 소스 파일 경로
- 정확한 시작 및 종료 라인
- 관련 소스 코드 조각

모든 Intelligence 항목은 하나 이상의 Evidence를 참조해야 한다. 근거가 없는 내용은 생성하지 않는다.

## 9. OpenAI 분석

- 초기 LLM 제공자는 OpenAI이며 기본 모델은 `gpt-4o-mini`다.
- 발견된 모든 API를 개수 제한 없이 자동 분석한다.
- 저장소 규모에 따라 호출 시간과 비용이 증가할 수 있음을 실행 전에 UI에 표시한다.
- 한 API의 LLM 호출 또는 응답 파싱 실패가 다른 API 분석을 중단시키지 않는다.
- 성공한 API와 실패한 API를 함께 포함하는 부분 결과를 반환한다.
- LLM 응답은 정의된 JSON Schema로 검증한다.
- 불확실한 결과는 confidence를 낮게 표시하며 기술적 조건과 비즈니스 규칙을 구분한다.

각 API에 대해 PreCondition, ResponseAssertion, BusinessRule, Exception, Additional Analysis를 생성한다. 각 항목은
`description`, `category`, `evidence`, `confidence`를 포함한다.

## 10. OpenAI 설정과 보안

- `application.yml`: 모델명, API endpoint, timeout, 재시도 횟수, 동시 호출 수 등 비민감 설정
- `application-local.yml`: 실제 API key. Git 추적에서 제외
- `application-local.example.yml`: API key가 비어 있는 예시
- API key가 없어도 웹 서버는 시작되어야 한다.
- API key가 없는 상태에서 분석을 요청하면 명확한 설정 오류를 반환한다.
- API key를 로그, UI, Raw JSON, 저장 JSON 또는 오류 응답에 노출하지 않는다.
- timeout, 재시도 횟수 및 동시 호출 수의 기본값은 Application Design에서 결정한다.

## 11. 결과 저장

- 데이터베이스나 서버 메모리에 조회 가능한 분석 이력을 영속화하지 않는다.
- 각 실행은 설정된 출력 루트 아래에 고유 실행 폴더를 생성한다.
- 실행 폴더에 API 목록 및 정적 분석 결과, 내부 코드 그래프, Evidence, 최종 API Intelligence, API별 진단을 JSON으로 저장한다.
- 폴더와 파일 이름은 Application Design에서 결정한다.
- 저장 JSON에 API key나 기타 비밀정보를 포함하지 않는다.
- 파일 저장 일부가 실패하면 UI와 실행 결과에 실패 사유를 표시한다.

## 12. UI

`gradle runWeb`의 기존 웹 애플리케이션에 신규 최상위 메뉴를 추가한다.

- 기존 Spec Scan 화면과 API Intelligence 화면을 명확히 분리한다.
- 신규 화면은 신규 HTTP API만 호출한다.
- 카드, 목록, 표 또는 그 조합으로 Raw JSON보다 읽기 쉽게 표현한다.
- 코드 그래프는 표시하지 않는다.

신규 UI에는 다음을 표시한다.

- API 목록
- API별 관련 소스 Evidence
- PreCondition, ResponseAssertion, BusinessRule, Exception, Additional Analysis
- API별 분석 상태와 실패 사유
- 전체 Raw JSON
- 저장된 실행 결과 폴더 위치

실행 중에는 진행 상태와 전체 API 수, 완료 수 및 실패 수를 확인할 수 있어야 한다.

## 13. 오류와 실패 격리

다음 오류를 구분하여 표시한다.

- 입력 검증 및 로컬 경로 접근 실패
- Git clone 및 revision 확인 실패
- 지원 가능한 API가 없거나 지원하지 않는 프로젝트 구조
- 정적 분석, 코드 그래프 생성 또는 Evidence 생성 실패
- OpenAI 설정 누락, 호출 실패 또는 timeout
- LLM 응답 JSON 검증 실패
- 결과 JSON 저장 실패

전체 실행을 계속할 수 없는 오류와 API 단위 오류를 구분한다. API 하나의 실패는 가능한 경우 다른 API의 분석과 저장을 중단시키지 않는다.

## 14. 합격 기준

1. 기존 `/api/scan`과 신규 파이프라인의 HTTP API, 모델 및 Application Service가 분리되어 있다.
2. 로컬 경로와 Git URL 입력이 각각 동작하며 Git revision을 지정할 수 있다.
3. revision 생략 시 원격 기본 브랜치의 HEAD를 분석한다.
4. `D:\workspace\real-estate\RealEstate`에서 Spring MVC API를 추출하고 분석할 수 있다.
5. RealEstate 전용 경로, 패키지, 클래스 또는 도메인 하드코딩이 없다.
6. 다른 구조의 Spring Boot 3.x + Spring MVC fixture 또는 저장소에서도 분석할 수 있다.
7. 지원 Mapping annotation의 대표 API를 추출한다.
8. 각 Evidence가 실제 소스 파일 경로와 정확한 라인 범위를 포함한다.
9. 발견된 모든 API에 대해 `gpt-4o-mini` 분석을 시도한다.
10. LLM 결과가 정의된 JSON Schema를 통과한다.
11. 모든 Intelligence 항목이 Evidence를 참조하며 근거 없는 항목을 허용하지 않는다.
12. 한 API의 LLM 실패에도 나머지 API 결과를 반환하고 저장한다.
13. API key 누락, 잘못된 Git revision 및 미지원 저장소 오류가 구분된다.
14. API key가 로그, UI, 오류, Raw JSON 및 저장 JSON에 노출되지 않는다.
15. UI에서 API 목록, Evidence, 모든 Intelligence 분류, 실패 사유 및 Raw JSON을 확인할 수 있다.
16. 내부 코드 그래프와 최종 결과를 실행별 폴더의 JSON으로 확인할 수 있다.
17. 기존 `/api/scan` 동작과 화면에 회귀가 없다.

## 15. 후속 단계

이 문서가 승인된 후 Application Design, 독립적으로 구현 가능한 Unit of Work, Construction 체크리스트, 구현과 검증 순서로 진행한다.
