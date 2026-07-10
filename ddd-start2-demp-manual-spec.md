# ddd-start2-demp Manual Spec

- Source repo: `D:\workspace\ddd-start2-demp`
- Origin: `https://github.com/madvirus/ddd-start2.git`
- Basis: manual reading of controller, DTO, validator, service, and selected domain code
- Output intent: ground-truth reference for comparing against `auto-oas`

## Summary

- Total endpoints inspected: `12`
- MVC view endpoints: `11`
- JSON REST endpoints: `1`
- Important characteristic: many business validations are expressed in service/domain logic, not only annotations

## Endpoints

### `GET /admin/orders`

- Controller: `com.myshop.admin.ui.AdminOrderController`
- Response: view `admin/adminOrders`
- Request:
  - Query `p:int`, optional, default `1`
- Validation:
  - none explicitly found

### `GET /admin/orders/{orderNo}`

- Controller: `com.myshop.admin.ui.AdminOrderController`
- Response:
  - success view `admin/adminOrderDetail`
  - fallback view `admin/noOrder`
- Request:
  - Path `orderNo:string`
- Validation:
  - `PATH $.orderNo -> EXISTS_IN_REPOSITORY true`

### `POST /admin/orders/{orderNo}/shipping`

- Controller: `com.myshop.admin.ui.AdminOrderController`
- Response:
  - success view `admin/adminOrderShipped`
  - conflict view `admin/adminOrderLockFail`
- Request:
  - Path `orderNo:string`
  - Query `version:long`, required
- Validation:
  - `PATH $.orderNo -> EXISTS_IN_REPOSITORY true`
  - `QUERY $.version -> REQUIRED true`
  - `QUERY $.version -> OPTIMISTIC_LOCK_MATCH`
  - `RESOURCE $.order.state -> STATE_IN [PAYMENT_WAITING, PREPARING]`
  - `RESOURCE $.order.state -> NOT_EQUALS CANCELED`
- Warning:
  - implementation appears inverted around `matchVersion()`

### `GET /categories`

- Controller: `com.myshop.catalog.ui.ProductController`
- Response: view `category/categoryList`
- Validation:
  - none explicitly found

### `GET /categories/{categoryId}`

- Controller: `com.myshop.catalog.ui.ProductController`
- Response: view `category/productList`
- Request:
  - Path `categoryId:long`
  - Query `page:int`, optional, default `1`
- Validation:
  - `PATH $.categoryId -> EXISTS_IN_REPOSITORY true`

### `GET /products/{productId}`

- Controller: `com.myshop.catalog.ui.ProductController`
- Response:
  - success view `category/productDetail`
  - failure HTTP `404`
- Request:
  - Path `productId:string`
- Validation:
  - `PATH $.productId -> EXISTS_IN_REPOSITORY true`

### `GET /api/events`

- Controller: `com.myshop.eventstore.ui.EventApi`
- Response: JSON `EventEntry[]`
- Request:
  - Query `offset:long`, required
  - Query `limit:long`, required
- Validation:
  - `QUERY $.offset -> REQUIRED true`
  - `QUERY $.limit -> REQUIRED true`

### `GET /my/orders`

- Controller: `com.myshop.order.ui.MyOrderController`
- Response: view `my/orders`
- Validation:
  - `AUTH $.currentUser -> AUTHENTICATED true`

### `GET /my/orders/{orderNo}`

- Controller: `com.myshop.order.ui.MyOrderController`
- Response:
  - success view `my/orderDetail`
  - other-user view `my/notYourOrder`
  - not-found view `my/noOrder`
- Request:
  - Path `orderNo:string`
- Validation:
  - `PATH $.orderNo -> EXISTS_IN_REPOSITORY true`
  - `AUTH $.currentUser -> EQUALS_RESOURCE_OWNER true`

### `GET /my/orders/{orderNo}/cancel`

- Controller: `com.myshop.order.ui.CancelOrderController`
- Response: view `my/orderCanceled`
- Request:
  - Path `orderNo:string`
- Validation:
  - `PATH $.orderNo -> EXISTS_IN_REPOSITORY true`
  - `AUTH $.currentUser -> HAS_CANCELLATION_PERMISSION (orderer or ROLE_ADMIN)`
  - `RESOURCE $.order.state -> STATE_IN [PAYMENT_WAITING, PREPARING]`
- Warning:
  - state-changing action is exposed as `GET`

### `POST /orders/orderConfirm`

- Controller: `com.myshop.order.ui.OrderController`
- Response: view `order/confirm`
- Request binding:
  - `@ModelAttribute OrderRequest`
- Body shape:
  - `orderProducts[] { productId, quantity }`
  - `ordererMemberId { id }`
  - `shippingInfo { address { zipCode, address1, address2 }, message, receiver { name, phone } }`
- Validation:
  - `AUTH $.ordererMemberId -> OVERWRITTEN_BY_AUTHENTICATED_USER true`

### `POST /orders/order`

- Controller: `com.myshop.order.ui.OrderController`
- Response:
  - success view `order/orderComplete`
  - validation failure view `order/confirm`
  - product missing view `order/noProduct`
- Request binding:
  - `@ModelAttribute OrderRequest`
- Body shape:
  - `orderProducts[] { productId, quantity }`
  - `ordererMemberId { id }`
  - `shippingInfo { address { zipCode, address1, address2 }, message, receiver { name, phone } }`
- Validation:
  - `AUTH $.ordererMemberId -> OVERWRITTEN_BY_AUTHENTICATED_USER true`
  - `BODY $.orderProducts -> REQUIRED true`
  - `BODY $.orderProducts -> NON_EMPTY true`
  - `BODY $.shippingInfo -> REQUIRED true`
  - `BODY $.shippingInfo.receiver -> REQUIRED true`
  - `BODY $.shippingInfo.receiver.name -> REQUIRED true`
  - `BODY $.shippingInfo.receiver.phone -> REQUIRED true`
  - `BODY $.shippingInfo.address -> REQUIRED true`
  - `BODY $.shippingInfo.address.zipCode -> REQUIRED true`
  - `BODY $.shippingInfo.address.address1 -> REQUIRED true`
  - `BODY $.shippingInfo.address.address2 -> REQUIRED true`
  - `BODY $.orderProducts[*].productId -> REQUIRED true` from `OrderRequestValidator4Spring`, but not wired in inspected controller path
  - `BODY $.orderProducts[*].quantity -> GREATER_THAN 0` from `OrderRequestValidator4Spring`, but not wired in inspected controller path
  - `BODY $.orderProducts[*].productId -> EXISTS_IN_REPOSITORY true`
- Warnings:
  - `OrderRequestValidator` can throw NPE when `orderProducts == null`
  - `OrderRequestValidator4Spring` exists but was not found to be registered from the inspected controller

## Comparison Notes Against auto-oas

- `auto-oas` detected the endpoint count correctly for this repo.
- `auto-oas` currently misclassifies many MVC view endpoints as JSON string responses.
- `auto-oas` currently treats framework parameters such as `ModelMap`, `BindingResult`, and `HttpServletResponse` as user input.
- `auto-oas` currently misses the real `@ModelAttribute OrderRequest` shape for `/orders/order` and `/orders/orderConfirm`.
- `auto-oas` also under-extracts service/domain validation rules such as ownership, cancellation permission, existence checks, and state checks.
