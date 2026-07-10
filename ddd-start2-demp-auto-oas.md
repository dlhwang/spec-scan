# ddd-start2-demp auto-oas Output

- Source scan target: `https://github.com/madvirus/ddd-start2.git`
- Trigger: `POST http://localhost:8088/api/scan`
- Saved raw output: [ddd-start2-demp-auto-oas.json](D:/workspace/auto-oas/ddd-start2-demp-auto-oas.json)

## Summary

- Detected operations: `12`
- Validation evidence graph nodes: `31`
- Validation evidence graph edges: `25`
- Operations with non-empty `validationConditions`: `0`

## Detected Operations

1. `GET /admin/orders`
2. `GET /admin/orders/{orderNo}`
3. `POST /admin/orders/{orderNo}/shipping`
4. `GET /categories`
5. `GET /categories/{categoryId}`
6. `GET /products/{productId}`
7. `GET /api/events`
8. `GET /my/orders/{orderNo}/cancel`
9. `GET /my/orders`
10. `GET /my/orders/{orderNo}`
11. `POST /orders/orderConfirm`
12. `POST /orders/order`

## Observed Characteristics

- Endpoint count is correct for the inspected repo subset.
- The raw output treats many MVC view endpoints as if they were JSON endpoints with `response200.schema.type = string`.
- Framework parameters such as `ModelMap`, `BindingResult`, and `HttpServletResponse` are surfaced as request inputs even though they are not caller-supplied API parameters.
- `@ModelAttribute OrderRequest` is not expanded into the actual nested request shape for `/orders/orderConfirm` and `/orders/order`.
- The evidence graph contains service and business-rule traces, but they are not normalized into final `validationConditions` for this repository run.

## Comparison Pointers

- Manual reference: [ddd-start2-demp-manual-spec.json](D:/workspace/auto-oas/ddd-start2-demp-manual-spec.json)
- Human-readable manual note: [ddd-start2-demp-manual-spec.md](D:/workspace/auto-oas/ddd-start2-demp-manual-spec.md)
