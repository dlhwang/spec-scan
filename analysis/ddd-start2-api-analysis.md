# ddd-start2 API 분석 결과

- 대상: https://github.com/madvirus/ddd-start2.git (Spring Boot + Spring MVC + Spring Security, DDD 예제 쇼핑몰)
- 추출된 엔드포인트: 컨트롤러 6개 / 엔드포인트 12개 (+ 뷰 전용 정적 라우트)
- 각 API를 **Request Precondition / Response Assertion / Business Logic / Others** 4개 관점으로 분해

## 전역 컨텍스트 (모든 API에 공통 적용)

`WebSecurityConfig` 기준:

| 규칙 | 내용 |
|---|---|
| 인증 기본값 | `anyRequest().authenticated()` — 아래 예외 빼고 전부 로그인 필요 |
| permitAll | `/`, `/home`, `/categories/**`, `/products/**`, `/login`, `/logout` |
| ROLE_ADMIN | `/admin/**` |
| 시큐리티 완전 우회 | `/api/**` (`web.ignoring()` — 필터 체인 자체를 안 탐) |
| 인증 방식 | `AUTH` 쿠키(평문 member_id) → `CookieSecurityContextRepository`가 매 요청 복원 |
| CSRF | **비활성화** |
| 도메인 이벤트 | 모든 `Events.raise()` 이벤트는 `EventStoreHandler`가 `evententry` 테이블에 JSON으로 저장 |

---

## 1. POST /orders/orderConfirm — 주문 확인 화면

`OrderController.orderConfirm` (order/ui/OrderController.java:42)

| 관점 | 내용 |
|---|---|
| **Request Precondition** | 인증 필수(anyRequest). 폼 바인딩 `OrderRequest`(orderProducts[i].productId, quantity, shippingInfo.*). 모든 productId가 실제 상품으로 존재해야 함 — 없으면 `NoOrderProductException` |
| **Response Assertion** | 성공: 뷰 `order/confirm` + model에 `products`, `totalAmounts`, `orderReq`. 상품 없음: `@ExceptionHandler` → 뷰 `order/noProduct` (200) |
| **Business Logic** | ordererMemberId를 클라이언트 입력이 아닌 SecurityContext의 로그인 사용자로 강제 세팅. 총액 = Σ(수량 × 상품 조회가) — 서버 조회 가격으로 계산 |
| **Others** | 상태 변경 없는 조회성인데 POST(폼 전달 목적). `@InitBinder`로 필드 직접 접근 바인딩 |

## 2. POST /orders/order — 주문 생성

`OrderController.order` (order/ui/OrderController.java:73) → `PlaceOrderService.placeOrder`

| 관점 | 내용 |
|---|---|
| **Request Precondition** | 인증 필수. `OrderRequestValidator` 검증: ordererMemberId·orderProducts(1개 이상)·shippingInfo.receiver.name/phone·address.zipCode/address1/address2 모두 필수. 각 productId 존재 필수(`NoOrderProductException`). 주문자 member 존재 필수(`OrdererServiceImpl` → MemberQueryService) |
| **Response Assertion** | 성공: 뷰 `order/orderComplete` + model `orderNo`. 검증 실패: `ValidationErrorException` → BindingResult에 필드별 에러 리버스 매핑 후 뷰 `order/confirm` 재표시 |
| **Business Logic** | 가격은 클라이언트 값 무시, `ProductRepository` 조회가로 `OrderLine` 구성. `orderRepository.nextOrderNo()`로 주문번호 채번. 초기 상태 `PAYMENT_WAITING`. Order 생성자 불변식: number/orderer/shippingInfo not null, orderLine 1개 이상, 총액 자동 계산. 생성 시 `OrderPlacedEvent` 발행 → 이벤트 스토어 저장. `@Transactional` |
| **Others** | 검증이 Bean Validation이 아니라 수동 Validator. **버그 의심**: Validator에서 `orderProducts == null`일 때 에러 추가 후 곧바로 `.isEmpty()` 호출 → NPE 가능 (OrderRequestValidator.java:19-21) |

## 3. GET /my/orders — 내 주문 목록

`MyOrderController.orders` (order/ui/MyOrderController.java:26)

| 관점 | 내용 |
|---|---|
| **Request Precondition** | 인증 필수. 파라미터 없음 |
| **Response Assertion** | 뷰 `my/orders` + model `orders` (로그인 사용자의 `OrderSummary` 목록) |
| **Business Logic** | `orderSummaryDao.findByOrdererId(로그인ID)` — 조회 조건에 사용자 ID를 박아서 소유권 필터링 (CQRS 조회 모델) |
| **Others** | `@RequestMapping`에 method 미지정 → 모든 HTTP 메서드 허용. 페이징 없음 |

## 4. GET /my/orders/{orderNo} — 내 주문 상세

`MyOrderController.orderDetail` (order/ui/MyOrderController.java:33)

| 관점 | 내용 |
|---|---|
| **Request Precondition** | 인증 필수. path `orderNo`. 주문 존재 + **주문자 본인** 이어야 상세 노출 |
| **Response Assertion** | 성공: 뷰 `my/orderDetail` + model `order`. 타인 주문: 뷰 `my/notYourOrder`. 미존재: 뷰 `my/noOrder` (모두 200) |
| **Business Logic** | 소유권 검사를 컨트롤러에서 수행: `orderDetail.orderer.memberId == 로그인ID`. `OrderDetailService`가 Order 애그리거트 + 상품 조회 모델을 조합해 `OrderDetail` DTO 구성 (상품 미존재 시 null 허용) |
| **Others** | 조회 후 검사 방식이라 존재 여부가 타인에게도 구분됨(noOrder vs notYourOrder — 정보 노출은 경미) |

## 5. GET /my/orders/{orderNo}/cancel — 주문 취소

`CancelOrderController` (order/ui/CancelOrderController.java:21) → `CancelOrderService.cancel`

| 관점 | 내용 |
|---|---|
| **Request Precondition** | 인증 필수. 주문 존재(`NoOrderException`). 취소 권한: 주문자 본인 또는 ROLE_ADMIN (`SecurityCancelPolicy`, 아니면 `NoCancellablePermission`). 주문 상태가 `PAYMENT_WAITING` 또는 `PREPARING` (`AlreadyShippedException`) |
| **Response Assertion** | 성공: 뷰 `my/orderCanceled`. 실패 예외들은 핸들러 없음 → 500 |
| **Business Logic** | 상태 전이 `→ CANCELED`는 애그리거트 `Order.cancel()`이 불변식(`verifyNotYetShipped`)과 함께 수행. `OrderCanceledEvent` 발행 → ①이벤트 스토어 저장 ②`OrderCanceledEventHandler`가 **AFTER_COMMIT + @Async**로 `RefundService.refund()` 호출(환불은 별도 트랜잭션·비동기) |
| **Others** | **상태 변경인데 GET + method 미지정** — CSRF도 꺼져 있어 링크 클릭만으로 취소 가능. 권한 정책을 도메인 서비스(CancelPolicy)로 분리한 게 특징 |

## 6. GET /categories — 카테고리 목록

`ProductController.categories` (catalog/ui/ProductController.java:30)

| 관점 | 내용 |
|---|---|
| **Request Precondition** | 없음 (permitAll) |
| **Response Assertion** | 뷰 `category/categoryList` + model `categories` (전체 목록) |
| **Business Logic** | 단순 전체 조회 |
| **Others** | method 미지정 |

## 7. GET /categories/{categoryId} — 카테고리별 상품 목록

`ProductController.list` (catalog/ui/ProductController.java:37)

| 관점 | 내용 |
|---|---|
| **Request Precondition** | permitAll. path `categoryId`(Long), query `page`(기본 1). 카테고리 존재 필수 — 없으면 `NoCategoryException` |
| **Response Assertion** | 성공: 뷰 `category/productList` + model `productInCategory`(페이지 메타 포함). 카테고리 미존재: 핸들러 없음 → 500 |
| **Business Logic** | 페이지 크기 10 고정, 1-base page를 0-base로 변환해 조회, `ProductSummary`로 축약 매핑 |
| **Others** | `page=0` 입력 시 내부 page=-1 → 예외. 존재하지 않는 카테고리가 404가 아닌 500 |

## 8. GET /products/{productId} — 상품 상세

`ProductController.detail` (catalog/ui/ProductController.java:46)

| 관점 | 내용 |
|---|---|
| **Request Precondition** | permitAll. path `productId` |
| **Response Assertion** | 성공: 뷰 `category/productDetail` + model `product`. 미존재: **HTTP 404** (`response.sendError`) |
| **Business Logic** | 조회 모델 단순 조회 |
| **Others** | 이 앱에서 유일하게 명시적 HTTP 상태코드로 실패를 표현하는 화면 API |

## 9. GET /admin/orders — 관리자 주문 목록

`AdminOrderController.orders` (admin/ui/AdminOrderController.java:35)

| 관점 | 내용 |
|---|---|
| **Request Precondition** | **ROLE_ADMIN**. query `p`(기본 1) |
| **Response Assertion** | 뷰 `admin/adminOrders` + model `orderPage`, `pagination`(5칸 페이지 내비) |
| **Business Logic** | 페이지 크기 20 고정, 주문번호 내림차순 정렬 (`OrderViewListService`) |
| **Others** | — |

## 10. GET /admin/orders/{orderNo} — 관리자 주문 상세

`AdminOrderController.orderDetail` (admin/ui/AdminOrderController.java:51)

| 관점 | 내용 |
|---|---|
| **Request Precondition** | ROLE_ADMIN. path `orderNo` |
| **Response Assertion** | 성공: 뷰 `admin/adminOrderDetail` + model `order`(버전 포함 → 낙관적 잠금 폼에 사용). 미존재: 뷰 `admin/noOrder` |
| **Business Logic** | 4번과 동일한 `OrderDetailService` 재사용, 소유권 검사만 없음(관리자) |
| **Others** | — |

## 11. POST /admin/orders/{orderNo}/shipping — 출고 시작

`AdminOrderController.startShippingOrder` (admin/ui/AdminOrderController.java:62) → `StartShippingService.startShipping`

| 관점 | 내용 |
|---|---|
| **Request Precondition** | ROLE_ADMIN. path `orderNo`, query/form `version`(long, 필수 — 낙관적 잠금 토큰). 주문 존재(`NoOrderException`). 상태: 미출고(`PAYMENT_WAITING`/`PREPARING`) && 미취소 |
| **Response Assertion** | 성공: 뷰 `admin/adminOrderShipped`. `OptimisticLockingFailureException`: 뷰 `admin/adminOrderLockFail`. `VersionConflictException`·기타: 핸들러 없음 → 500 |
| **Business Logic** | 이중 동시성 제어: ①요청 시점 version 비교(사용자가 본 화면 기준) ②JPA `@Version` 커밋 시점 잠금. 상태 전이 `→ SHIPPED`, `ShippingStartedEvent` 발행 |
| **Others** | **버그 의심**: `if (order.matchVersion(req.getVersion())) throw VersionConflictException` — 버전이 **일치할 때** 충돌 예외를 던짐. 원 의도는 `!matchVersion`일 것 (StartShippingService.java:26). 현재 코드로는 정상 요청이 항상 실패하고, 낡은 버전 요청이 통과함 |

## 12. GET /api/events — 이벤트 스토어 조회 (REST)

`EventApi.list` (eventstore/ui/EventApi.java:20)

| 관점 | 내용 |
|---|---|
| **Request Precondition** | query `offset`, `limit` (Long, **필수** — 없으면 400). 인증 없음 |
| **Response Assertion** | JSON 배열 `EventEntry[] {id, type, contentType, payload(JSON string), timestamp}`, id 오름차순, 최대 limit건 |
| **Business Logic** | `evententry` 테이블 offset/limit 조회. 외부 시스템 연동용 폴링 API (`EventForwarder`/`OffsetStore`와 세트) |
| **Others** | **`/api/**`는 `web.ignoring()`이라 시큐리티 필터를 완전히 우회** — 모든 도메인 이벤트(주문자·배송지 개인정보 포함 payload)가 무인증 공개됨. 예제니까 넘어가지만 실서비스면 최우선 지적 사항 |

---

## 발견된 이슈 요약 (Others 종합)

1. **출고 버전 검사 역전 의심** — `StartShippingService.java:26` `matchVersion` 앞에 `!` 누락 추정
2. **Validator NPE** — `OrderRequestValidator.java:19-21` orderProducts가 null이면 NPE
3. **GET으로 상태 변경** — `/my/orders/{orderNo}/cancel` (CSRF off와 결합 시 CSRF 공격에 그대로 노출)
4. **이벤트 API 무인증** — `/api/events`가 시큐리티 체인 밖
5. **비밀번호 평문** — `NoOpPasswordEncoder`, AUTH 쿠키에 평문 ID (예제 한정 허용 수준)
6. 대부분의 도메인 예외(NoOrderException, NoCategoryException 등)에 예외 핸들러가 없어 500으로 노출
