# API 추출 및 4관점 분석 방법론

Spring MVC 코드베이스에서 API를 추출하고, API별로 **Request Precondition / Response Assertion / Business Logic / Others**를 찾아내는 절차. ddd-start2 분석에 실제 사용한 방법 그대로 기록한다.

## 전체 흐름

```
1. 진입점 인벤토리 → 2. 전역 규칙 수집 → 3. 엔드포인트별 호출 체인 추적 → 4. 4관점 분류 → 5. 교차 검증
```

---

## 1단계. 진입점(엔드포인트) 인벤토리

컨트롤러 파일을 하나씩 열기 전에, grep으로 전체 지도를 먼저 만든다.

```bash
# 컨트롤러 클래스 목록
grep -rln -E "@(Rest)?Controller" src/main/java

# 모든 매핑 어노테이션 → 이것이 곧 API 목록
grep -rn -E "@(Request|Get|Post|Put|Delete|Patch)Mapping" src/main/java
```

추가로 놓치기 쉬운 진입점을 확인한다:

- `WebMvcConfigurer.addViewControllers` — 코드 없는 뷰 전용 라우트 (`/home`, `/login` 등)
- Spring Security의 `formLogin()`/`logout()` — 설정으로 생기는 `/login` POST, `/logout`
- `@ControllerAdvice`, `@ExceptionHandler` — 응답을 바꾸는 숨은 진입점

산출물: **URL × HTTP 메서드 × 핸들러 메서드** 표. 이 표가 이후 모든 단계의 체크리스트가 된다.

## 2단계. 전역 규칙 수집 (모든 API에 공통 적용되는 전제)

개별 API를 보기 전에 전역 설정을 먼저 읽어야 한다. Precondition의 절반은 여기서 나온다.

| 확인 대상 | 찾는 방법 | 얻는 정보 |
|---|---|---|
| 시큐리티 설정 | `EnableWebSecurity`, `SecurityFilterChain`, `WebSecurityConfigurerAdapter` grep | URL 패턴별 인증/인가 규칙, permitAll, 역할 요구, **`web.ignoring()`(필터 완전 우회)**, CSRF 여부 |
| 인증 방식 | `SecurityContextRepository`, `AuthenticationSuccessHandler` 구현체 | 세션/쿠키/토큰 방식, 인증 주체가 컨트롤러에 어떻게 전달되는지 |
| 전역 예외 처리 | `@ControllerAdvice` grep | 예외 → HTTP 상태 매핑 |
| 이벤트 인프라 | `@EventListener`, `@TransactionalEventListener` grep | 도메인 이벤트의 공통 부수효과(이벤트 스토어 저장 등) |

주의: `web.ignoring()`에 걸린 패턴은 `authorizeRequests()` 규칙과 무관하게 **완전 무인증**이다. ddd-start2에서 `/api/**`가 여기에 해당했다 — 설정 두 곳을 겹쳐 봐야 잡히는 유형.

## 3단계. 엔드포인트별 호출 체인 추적

각 핸들러 메서드에서 시작해 **컨트롤러 → 응용 서비스 → 도메인(애그리거트) → 이벤트 핸들러**까지 세로로 내려간다. 계층마다 나오는 정보가 다르다:

```
Controller        : 파라미터 바인딩, 인증 주체 추출, 뷰/상태코드 결정, try-catch → Response Assertion 재료
Application Svc   : @Transactional 경계, 명시적 Validator, 존재성 검사(orElseThrow), 권한 정책 호출 → Precondition 재료
Domain(Aggregate) : 생성자/메서드의 불변식(verify*, IllegalArgumentException), 상태 전이 규칙, Events.raise → Business Logic 재료
Event Handler     : AFTER_COMMIT/@Async 여부, 후속 부수효과(환불, 이벤트 저장) → Business Logic(부수효과) 재료
```

인터페이스를 만나면 구현체를 찾는다 (`grep -rln "implements Xxx"`). 정책 객체(CancelPolicy 등)는 구현체에 실제 권한 규칙이 있다.

## 4단계. 4관점 분류 기준

읽으면서 발견한 사실을 아래 기준으로 배분한다.

### Request Precondition — "이 요청이 성공하려면 사전에 무엇이 참이어야 하는가"
- 인증/인가 (2단계 전역 규칙 + 메서드 내 권한 검사 + 정책 객체)
- 파라미터 필수 여부·타입 (`@RequestParam(required)`, `@PathVariable`, 폼 바인딩 필드)
- 명시적 검증 로직 (Bean Validation 또는 수동 Validator의 조건 전부)
- 리소스 존재성 (`orElseThrow`, `Optional.isPresent` 분기)
- 상태 전제조건 (도메인의 `verify*` 메서드 — "미출고 상태여야 취소 가능" 등)
- 동시성 토큰 (version 파라미터, `@Version` 필드)

### Response Assertion — "응답으로 무엇이 보장되는가"
- 성공 시: 뷰 이름 + 모델 속성 / JSON 스키마 + 정렬·건수 보장
- 각 실패 경로별 응답: `@ExceptionHandler`·try-catch가 잡는 것은 그 결과(뷰/상태코드), 안 잡는 예외는 500
- 명시적 상태코드 (`response.sendError(404)` 등)
- 화면 앱은 "HTTP는 200인데 뷰가 다른" 패턴이 많으므로 상태코드가 아니라 **뷰 이름 분기**로 판정

### Business Logic — "요청과 응답 사이에서 시스템이 하는 결정과 변화"
- 상태 전이와 그 규칙 (PAYMENT_WAITING → CANCELED 등)
- 서버 권위 데이터 (클라이언트 값을 무시하고 서버가 다시 계산하는 것: 가격, 주문자 ID)
- 파생 계산 (총액, 페이징 변환)
- 트랜잭션 경계와 채번
- 발행되는 도메인 이벤트와 그 후속 처리 (동기/비동기, 커밋 전/후)

### Others — "위 셋에 안 들어가지만 스펙·품질에 영향 주는 것"
- 버그 의심 (조건 역전, NPE 경로 — 코드를 "의도"가 아니라 "실행 결과"로 읽어야 발견됨)
- HTTP 시맨틱 위반 (GET으로 상태 변경, method 미지정 매핑)
- 보안 관찰 (무인증 API, CSRF off, 평문 비밀번호)
- 에러 처리 공백 (도메인 예외가 500으로 새는 경로)

## 5단계. 교차 검증

- 1단계 인벤토리 표와 4단계 결과를 대조해 누락 엔드포인트가 없는지 확인
- 예외 클래스 grep(`grep -rn "class .*Exception"`)으로, 분석에 등장하지 않은 예외가 어느 API에서 던져지는지 역추적
- 테스트 코드(src/test)가 있으면 Precondition/Assertion의 근거로 대조 (기대 상태코드·검증 메시지)

## 실전 팁

1. **파일을 열기 전에 grep으로 지도부터** — 컨트롤러 6개를 순서대로 읽는 것보다, 매핑 12개 목록을 먼저 확보하면 누락이 없다.
2. **Precondition은 코드에 흩어져 있다** — 시큐리티 설정(전역) + Validator(응용) + orElseThrow(응용) + verify*(도메인) 4곳을 다 모아야 완성된다.
3. **Response Assertion은 실패 경로 개수만큼 있다** — 성공 1개 + catch/ExceptionHandler 개수 + "잡히지 않는 예외 → 500" 1개.
4. **부수효과는 이벤트 핸들러까지 따라가야 한다** — `Events.raise` 한 줄 뒤에 환불·이벤트 저장 같은 실제 비즈니스가 숨어 있다. `AFTER_COMMIT`/`@Async`면 "응답 성공 ≠ 부수효과 완료"라는 것도 스펙이다.
5. **조건문은 의도가 아니라 결과로 읽는다** — `if (matchVersion) throw Conflict`처럼 그럴듯해 보이는 역전 버그는 "이 조건이 참인 실제 상황"을 시뮬레이션해야 잡힌다.
