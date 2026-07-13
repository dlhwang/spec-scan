# PoC 결과 보고: Git 소스 정적 분석 기반 API 테스트 지원 자동화

> **분석 대상**: `https://github.com/madvirus/ddd-start2.git` (DDD 기반 Spring Boot 프로젝트)
> **분석 방식**: Git URL 입력 → 소스코드 AST 정적 분석 → 구조화된 JSON 산출물 자동 생성

---

## 이번 PoC에서 검증한 것

Git URL 하나만 입력하면, 소스코드를 정적 분석하여 아래 두 가지를 자동으로 뽑아낼 수 있는지 검증했습니다.

```
Git URL 입력 → 소스코드 Clone → AST 정적 분석
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
             ① API 호출 스펙    ② 검증 조건 분류   ③ 대시보드 시각화
```

---

## ① 검증 1: API 호출에 필요한 기본 정보 자동 추출

소스코드의 Controller, DTO 클래스를 분석하여 **API를 호출하기 위해 필요한 모든 정보**를 자동으로 파싱했습니다.

### 실제 산출물 예시 — `POST /orders/orderConfirm` (주문 확인)

| 구분 | 추출 내용 |
|:---|:---|
| **Method / Path** | `POST /orders/orderConfirm` |
| **Controller** | `com.myshop.order.ui.OrderController` |
| **Content-Type** | `application/json` |

**Request Body 구조** (DTO 필드 자동 평탄화):

| 필드 경로 | 타입 |
|:---|:---|
| `$.orderProducts[*].productId` | string |
| `$.orderProducts[*].quantity` | integer |
| `$.ordererMemberId.id` | string |
| `$.shippingInfo.address.zipCode` | string |
| `$.shippingInfo.address.address1` | string |
| `$.shippingInfo.address.address2` | string |
| `$.shippingInfo.message` | string |
| `$.shippingInfo.receiver.name` | string |
| `$.shippingInfo.receiver.phone` | string |

**자동 생성된 Request Body Example**:
```json
{
  "orderProducts": [{ "productId": "", "quantity": 0 }],
  "ordererMemberId": { "id": "" },
  "shippingInfo": {
    "address": { "zipCode": "", "address1": "", "address2": "" },
    "message": "",
    "receiver": { "name": "", "phone": "" }
  }
}
```

> **→ 테스터가 소스를 읽지 않아도 이 API를 바로 호출해볼 수 있는 정보가 자동 제공됩니다.**

---

## ② 검증 2: API별 검증 조건 3분류 자동 수집

코드 내부의 Validation 로직, Service Guard, 도메인 규칙을 분석하여 **API마다 3가지 카테고리로 분류**했습니다.

| 분류 | 의미 | 테스트할 때 어떻게 쓰나 |
|:---|:---|:---|
| **사전 조건 (Preconditions)** | 이 API를 정상 호출하려면 충족해야 하는 입력 조건 | "이 필드를 안 보내면 실패하는구나" → 필수값 누락 테스트 |
| **성공 조건 (Assertions)** | API가 정상 처리되었을 때 기대하는 응답 | "200이 오면 성공이구나" → 자동 검증 기준 |
| **제외된 비즈니스 룰 (Excluded Rules)** | 코드에 존재하지만 단순 입력으로는 테스트 불가한 내부 조건 | "이 API는 주문 상태가 X일 때만 동작하는구나" → 시나리오 설계 힌트 |

### 실제 산출물 예시 1 — `POST /orders/orderConfirm`

**사전 조건 (Preconditions)** — 9건 수집됨:

| 위치 | 대상 필드 | 조건 |
|:---|:---|:---|
| BODY | `$.ordererMemberId` | REQUIRED |
| BODY | `$.orderProducts` | REQUIRED |
| BODY | `$.shippingInfo` | REQUIRED |
| BODY | `$.shippingInfo.receiver` | REQUIRED |
| BODY | `$.shippingInfo.receiver.name` | REQUIRED |
| BODY | `$.shippingInfo.receiver.phone` | REQUIRED |
| BODY | `$.shippingInfo.address` | REQUIRED |
| BODY | `$.shippingInfo.address.zipCode` | REQUIRED |
| BODY | `$.shippingInfo.address.address1` | REQUIRED |

> **→ 테스터는 이 표만 보고 "어떤 필드를 빼면 실패하는지" 바로 테스트 케이스를 만들 수 있습니다.**

### 실제 산출물 예시 2 — `POST /admin/orders/{orderNo}/shipping` (배송 시작)

**제외된 비즈니스 룰 (Excluded Rules)** — 2건 수집됨:

| 위치 | 대상 | 조건 | 기대값 |
|:---|:---|:---|:---|
| QUERY | `$.version` | OPTIMISTIC_LOCK_MATCH | 현재 리소스 버전과 일치해야 함 |
| RESOURCE | `$.order.state` | STATE_IN | `PAYMENT_WAITING, PREPARING` |

> **→ "배송 시작은 주문 상태가 결제 대기나 준비 중일 때만 가능하구나"** 라는 비즈니스 맥락을 코드를 읽지 않고도 파악할 수 있습니다.

### 실제 산출물 예시 3 — `GET /my/orders/{orderNo}/cancel` (주문 취소)

**제외된 비즈니스 룰 (Excluded Rules)** — 2건 수집됨:

| 위치 | 대상 | 조건 | 기대값 |
|:---|:---|:---|:---|
| AUTH | `$.currentUser` | HAS_CANCELLATION_PERMISSION | `orderer or ROLE_ADMIN` |
| RESOURCE | `$.order.state` | STATE_IN | `PAYMENT_WAITING, PREPARING` |

> **→ "취소는 본인 또는 관리자만 가능하고, 주문 상태도 배송 전이어야 한다"** 라는 조건을 한눈에 파악할 수 있습니다.

---

## 전체 분석 결과 요약 (12개 API)

| # | Method | Path | 사전조건 | 성공조건 | 제외 비즈니스 룰 |
|:--|:---|:---|:---:|:---:|:---:|
| 1 | GET | `/admin/orders` | 0 | 1 | 0 |
| 2 | GET | `/admin/orders/{orderNo}` | 0 | 1 | 0 |
| 3 | POST | `/admin/orders/{orderNo}/shipping` | 0 | 1 | **2** |
| 4 | GET | `/categories` | 0 | 1 | 0 |
| 5 | GET | `/categories/{categoryId}` | 0 | 1 | 0 |
| 6 | GET | `/products/{productId}` | 0 | 1 | 0 |
| 7 | GET | `/api/events` | 0 | 1 | 0 |
| 8 | GET | `/my/orders/{orderNo}/cancel` | 0 | 1 | **2** |
| 9 | GET | `/my/orders` | 0 | 1 | 0 |
| 10 | GET | `/my/orders/{orderNo}` | 0 | 1 | 0 |
| 11 | POST | `/orders/orderConfirm` | **9** | 1 | 0 |
| 12 | POST | `/orders/order` | **9** | 1 | 0 |
| | | **합계** | **18** | **12** | **4** |

---

## 정리: 이 PoC가 제품 기획에 주는 시사점

### PoC로 확인된 것
1. **Git URL만 있으면** 개발자 추가 작업 없이 API 호출 스펙을 자동 추출할 수 있다
2. 코드 내부의 검증 로직을 분석하여 **테스트에 필요한 사전/사후 조건을 자동 분류**할 수 있다
3. 단순 입력 검증을 넘어 **도메인 비즈니스 규칙(권한, 상태값 가드 등)까지 감지**하여 테스트 시나리오 설계에 힌트를 줄 수 있다

### 향후 제품화 시 고려 포인트
- 이 산출물(JSON)을 **테스트 자동화 도구**(RestAssured, Postman, Playwright 등)에 바인딩하면 테스트 케이스 자동 생성까지 확장 가능
- CI/CD 파이프라인에 연동하면 **코드 변경 시 테스트 계약 자동 갱신** 가능
- 현재는 Spring MVC 기반 Java 프로젝트만 지원 → 타 프레임워크 확장 필요
