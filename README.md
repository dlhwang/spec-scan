# spec-scan

Spring 프로젝트(git URL 또는 로컬 경로)를 입력받아 **코드를 그래프로 분석**하고,
스웨거 수준으로 실행 가능한 **API 스펙 + 사전 조건(PreConditions) + 응답 어서션(ResponseAssertions) + 도메인 규칙(Others)** 을
JSON으로 추출하는 정적 분석 CLI입니다. 대상 프로젝트를 빌드하거나 실행하지 않고 소스만 읽습니다.

## 실행

```bash
./gradlew installDist
./build/install/spec-scan/bin/spec-scan https://github.com/dlhwang/RealEstate.git -o out
# 또는 로컬 경로
./build/install/spec-scan/bin/spec-scan ~/projects/my-service -o out
```

출력:

| 파일 | 내용 |
|---|---|
| `out/api-spec.json` | 엔드포인트별 스펙 + 조건 목록 |
| `out/code-graph.json` | 코드 그래프 (CLASS/METHOD/FIELD/ENDPOINT 노드, CALLS/DECLARES/EXTENDS/HANDLED_BY 엣지) |

## API 스펙에 담기는 것

엔드포인트마다: HTTP 메서드 · 경로 · consumes/produces · 성공 상태코드 ·
헤더(`@RequestHeader`) · 경로변수(`@PathVariable`) · 쿼리스트링(`@RequestParam`, `@ModelAttribute` POJO 평탄화, `Pageable`) ·
요청 바디 스키마(제네릭·중첩·enum·상속 해석, `@Schema` 설명/예시 포함) · 응답 바디 스키마
(선언 타입이 아닌 **성공 경로에서 실제로 생성되는 타입**으로 교정 — 예: 선언은 `ErrorResponse`지만 실제 `DataResponse` 반환).

## 조건 추출

모든 조건은 `{path, operator, expected}` 형태입니다.

```json
{ "path": "$.password", "operator": "goe", "expected": 10,
  "location": "BODY", "source": "if-throw",
  "description": "IllegalArgumentException: 비밀번호는 최소 10자입니다.",
  "at": "AuthService.java:12" }
```

### preConditions — 요청 입력에 대한 사전 조건
- **Bean Validation**: `@NotNull/@NotBlank/@Size/@Min/@Max/@Pattern/@Email/...` → `not_null / goe / loe / matches / format ...`
- **enum 타입 제약**: enum 필드/파라미터 → `{operator: in, expected: [...]}`
- **if-throw**: 컨트롤러→서비스→도메인 엔티티/VO 생성자까지 호출 그래프를 따라가며
  요청 필드로 역추적되는 가드 절을 부정 변환. `super(...)`/`this(...)` 위임, 빌더, Lombok 생성자, DTO→도메인 매퍼(`to()`) 통과.
- **에러 누적 Validator**: `if (cond) errors.add(ValidationError.of("field","code"))` /
  `bindingResult.rejectValue(...)` 패턴 → 조건 부정 변환 (DDD 스타일 커스텀 Validator 지원)
- **orElseThrow**: `repo.findById($.propertyId).orElseThrow(...)` → `{path: $.propertyId, operator: exists}`
  — 로컬 변수·래퍼 객체(`findById(new OrderNo($.orderNo))`)를 거쳐도 추적
- **Assert / requireNonNull / Preconditions**: 스프링·구아바 단언 유틸 인식
- 교차 필드 규칙: `if (start.compareTo(end) > 0) throw` → `{path: $.priceStart, operator: loe, expected: "$.priceEnd"}`
- 컬렉션 순회: `for (OrderProduct op : req.getOrderProducts())` 내부 조건 → `$.orderProducts[*].productId`

### responseAssertions — 정상 응답에서 기대되는 값
성공 반환 경로(서비스 호출→정적 팩토리→빌더/생성자)를 따라가 실제로 채워지는 필드를 찾습니다.
- 문자열 필드 ← 비리터럴 값: `{path: $.token, operator: not_empty}`
- 리터럴 대입: `{path: $.success, operator: eq, expected: true}`
- 객체/컬렉션: `not_null`
- **뷰 컨트롤러**(`@Controller` + String 반환): `{path: "$view", operator: eq|in, expected: 뷰이름들}`,
  `produces: text/html`, POST 폼 바인딩이면 `consumes: application/x-www-form-urlencoded`

### others — 입력/출력에 직접 매핑되지 않는 비즈니스·도메인 규칙
- 성공 분기 가드(`if (passwordEncoder.matches(...)) return ok;`) → `{path: $.password, operator: matches, expected: "userDetails.getPassword()"}`
- 단일 비교로 분해 불가한 식은 `operator: expr` 로 원문 보존

`location`은 `BODY | QUERY | PATH | HEADER | DOMAIN`, `source`는
`bean-validation | type-constraint | if-throw | or-else-throw | assert | if-return | response-construction` 입니다.

## 아키텍처

```
source/ProjectResolver   git clone 또는 로컬 경로 해석
parse/ProjectParser      JavaParser + SymbolSolver (src/main/java 전체)
graph/GraphBuilder       JGraphT 코드 그래프 (호출/선언/상속/엔드포인트)
extract/
  EndpointExtractor      @RestController 매핑 → ApiEndpoint 조립
  SchemaBuilder          타입 → JSON 스키마 (제네릭 치환, 상속, enum, Page/Optional/ResponseEntity)
  ValidationExtractor    Bean Validation 어노테이션 → Condition
  FlowAnalyzer           심볼릭 요청값 추적 + 가드절 → Condition (인터프로시저럴, depth 8)
  ResponseAssertionExtractor  성공 반환 경로의 객체 구성 → Condition
```

Lombok 프로젝트도 동작합니다: 게터/생성자가 소스에 없으면 이름 규약·필드 순서 기반 휴리스틱으로 보완합니다.

## 한계
- 정적 분석 특성상 리플렉션·AOP·런타임 프록시로 주입되는 검증(커스텀 Validator 빈 등)은 보지 못합니다.
- 조건은 **발견 기반**입니다: 목록에 없다고 조건이 없다는 보장은 아닙니다.
- Kotlin 소스는 아직 미지원(Java만 파싱).
