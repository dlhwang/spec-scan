# Validation Condition 개선 계획

`auto-oas`의 `ValidationEvidenceGraph`가 이미 수집한 서비스/도메인 규칙을 최종 `validationConditions`로 승격하지 못하는 문제를 개선하기 위한 분석 및 실행 계획이다. 이번 계획은 `ddd-start2` 수동 정답지와 `auto-oas` 산출물 비교 결과를 기준으로 작성했다.

## 범위

- 대상 문제:
  - evidence graph에는 남아 있는 서비스/도메인 규칙이 최종 `validationConditions`에 반영되지 않음
  - MVC 엔드포인트의 request/response semantic 분류가 부정확함
  - nested body path, resource invariant, 권한 규칙이 exporter 단계에서 탈락함
- 이번 계획의 직접 타깃:
  - `ValidationExtractionService`
  - `ValidationEvidenceGraphBuilder`
  - `RuleBasedConditionNormalizer`
  - `NormalizationService`
  - `ExecutionSpecExporter`
  - 관련 회귀 테스트

## 현재 관찰 요약

- [x] 수동 정답지와 `auto-oas` 결과의 차이를 비교하였다.
- [x] `POST /admin/orders/{orderNo}/shipping`에서 evidence graph와 final `validationConditions`의 단절을 확인하였다.
- [x] 현재 코드에서 graph는 존재하지만 endpoint별 최종 조건으로 정규화되지 않는 지점을 식별하였다.
- [x] 개선 설계에 따라 코드 변경 계획을 확정한다.
- [x] Phase 1 최소 구현을 수행하고 회귀 테스트를 통과시켰다.

## 현재 구현 상태

- [x] `ApiCondition`에 `ConditionLocation`을 추가하여 exporter가 위치를 늦게 추측하지 않도록 변경
- [x] `ExecutionSpecExporter`에서 body 미존재 시 조건을 비우던 조기 반환 제거
- [x] `RuleBasedConditionNormalizer`에서 endpoint 스코프 기반 위치 해석 추가
- [x] `@ModelAttribute` 복합 객체를 `BODY` 바인딩으로 분류하도록 보강
- [x] 관련 단위 테스트 및 스캔 테스트 보강 후 `./gradlew test` 통과
- [x] service -> domain 1-hop 다단 추적 확장 및 관련 graph 테스트 추가
- [x] MVC view response vs body response 구분 및 framework parameter 필터링 보강
- [x] 재스캔 결과 `POST /admin/orders/{orderNo}/shipping`에 non-empty validationConditions가 반영되는 것을 확인
- [ ] `RESOURCE` 조건의 graph 기반 추론 강화
- [ ] MVC response semantic 분류 개선
- [ ] `/orders/order`의 nested validator/helper 기반 규칙 추출 강화
- [ ] `/my/orders/{orderNo}/cancel`의 permission/state 규칙을 final condition으로 승격
- [ ] `/orders/order`와 `/orders/orderConfirm`의 endpoint 분리 및 nested rule 승격

## 핵심 갭 분석

### 1. `ValidationExtractionService`는 직접 호출 위주 후보만 만든다

파일: [ValidationExtractionService.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/ValidationExtractionService.java)

- 컨트롤러 메서드 내부의 `MethodCallExpr`를 순회하여 서비스 필드 타입과 메서드명을 찾는 수준에서 멈춘다.
- 서비스 메서드 내부에서 다시 호출되는 도메인 메서드, helper 메서드, repository 조회/존재성 체크는 후보로 충분히 승격되지 않는다.
- 결과적으로 endpoint -> service 까지는 연결되지만 service -> domain -> rule 체인이 후보군 레벨에서 손실된다.

### 2. `ValidationEvidenceGraphBuilder`는 graph를 만들지만 projection 정보가 부족하다

파일: [ValidationEvidenceGraphBuilder.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/ValidationEvidenceGraphBuilder.java)

- `IfStmt` 안 `ThrowStmt`는 잘 잡지만, 그 조건이 어떤 request field 또는 resource state와 연결되는지 해석하지 않는다.
- 서비스 메서드에서 호출한 도메인 메서드(`order.startShipping()`, `order.cancel()`) 내부의 상태 검증 체인을 graph로 확장하지 않는다.
- `NoOrderException`, `VersionConflictException`, `NoCancellablePermission` 같은 예외는 노드로 남지만, 이를 `PATH orderNo exists`, `QUERY version optimistic lock`, `RESOURCE order.state in ...` 같은 조건으로 바꾸는 metadata가 부족하다.

### 3. `RuleBasedConditionNormalizer`는 graph를 거의 활용하지 않는다

파일: [RuleBasedConditionNormalizer.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/RuleBasedConditionNormalizer.java)

- `hasServiceRuleEvidence()`는 “서비스 호출이 있었는지” 정도만 확인한다.
- 실제 정규화는 문자열 패턴 기반 비교(`==`, `!=`, `.isEmpty()`, `throw new ...`) 중심이라 resource invariant와 권한 규칙을 분류하지 못한다.
- `BUSINESS_CONSTRAINT` 같은 포괄 연산자로 떨어져 exporter 단계에서 매핑 근거가 약해진다.
- endpoint node 탐색의 초기값이 `"ENDPOINT:POST:" + endpointPath`에 치우쳐 있어 메서드별 처리 안정성도 약하다.

### 4. `ExecutionSpecExporter`가 조건을 너무 일찍 버린다

파일: [ExecutionSpecExporter.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/ExecutionSpecExporter.java)

- `resolveCondition()`은 path/query/header/body top-level 필드명에 매핑되지 않으면 조건을 버린다.
- nested body path (`$.shippingInfo.receiver.name`)는 top-level property 기준 필터에 걸려 손실된다.
- resource invariant (`$.order.state`)나 auth predicate (`$.currentUser`)는 표현 위치가 없어 전부 탈락한다.
- body가 없는 endpoint는 request-level validation이 거의 비워진다.

### 5. `NormalizationService`에는 graph-backed scoring/projection 단계가 없다

파일: [NormalizationService.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/NormalizationService.java)

- 청크 생성 -> rule-based normalization 흐름은 있으나, graph 근거를 사용해 candidate를 endpoint별로 재배치하거나 target location을 보정하는 단계가 없다.
- 따라서 evidence graph는 디버그 artifact로만 남고, 실제 최종 spec 품질을 끌어올리는 엔진 역할을 못 하고 있다.

## 개선 방향

### 방향 1. Graph를 “디버그 산출물”이 아니라 “정규화 입력”으로 승격

- endpoint -> service -> domain -> exception 체인을 bounded-depth로 탐색한다.
- 각 rule node에 다음 metadata를 붙인다.
  - `ruleKind`: existence, optimistic_lock, permission, state_guard, dto_field_check
  - `targetHint`: `orderNo`, `version`, `order.state`, `currentUser`, `shippingInfo.receiver.name`
  - `targetLocationHint`: `PATH`, `QUERY`, `BODY`, `RESOURCE`, `AUTH`
- 이 metadata를 `ApiCondition` 생성 전에 활용한다.

### 방향 2. exporter 전에 “endpoint rule projection” 단계를 추가

- graph와 candidate를 읽어서 endpoint별 `ProjectedRule` 목록으로 변환하는 단계를 신설한다.
- 이 단계에서:
  - nested body path 유지
  - resource/auth 조건 유지
  - existence/permission/state rule을 drop하지 않고 구조화
- `ExecutionSpecExporter`는 이미 정리된 `ProjectedRule`을 출력만 하도록 단순화한다.

### 방향 3. `validationConditions` 위치 모델을 확장

- 지금의 `PATH`, `QUERY`, `BODY`만으로는 부족하다.
- 최소 아래 location을 허용해야 한다.
  - `PATH`
  - `QUERY`
  - `BODY`
  - `HEADER`
  - `RESOURCE`
  - `AUTH`
- 이렇게 해야 `order.state`, `currentUser role`, `resource owner match`를 보존할 수 있다.

### 방향 4. MVC semantic 분류를 별도 처리

- `String` 반환 + `@Controller`인 경우 무조건 JSON response로 보지 말아야 한다.
- `ModelMap`, `BindingResult`, `HttpServletResponse` 등 framework parameter는 caller input에서 제외해야 한다.
- `@ModelAttribute` 복합 객체는 body-like form binding 또는 별도 `bindingStyle`로 유지해야 한다.

## 우선순위별 실행 계획

### Phase 1. Endpoint Rule Projection 도입

목표:
- graph와 후보군을 endpoint별 최종 규칙으로 연결하는 중간 계층 도입

대상 파일:
- [NormalizationService.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/NormalizationService.java)
- [RuleBasedConditionNormalizer.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/RuleBasedConditionNormalizer.java)
- 신규 support class 추가 가능

작업:
- `ProjectedRule` 또는 동등 개념 도입
- graph node/edge를 읽어 `targetLocationHint`, `targetPathHint` 추론
- `VersionConflictException`, `NoOrderException`, `NoCancellablePermission` 패턴을 명시적으로 분류

완료 기준:
- `/admin/orders/{orderNo}/shipping`에서 최소 `orderNo`, `version`, `order.state` 관련 규칙이 final condition으로 살아남음

### Phase 2. Graph 확장

목표:
- 서비스 메서드 내부에서 도메인 메서드까지 다단 추적

대상 파일:
- [ValidationEvidenceGraphBuilder.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/ValidationEvidenceGraphBuilder.java)
- [ValidationExtractionService.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/ValidationExtractionService.java)

작업:
- service method 내부의 method call을 따라 domain/helper method로 1~2 hop 추가 추적
- `verifyNotYetShipped`, `verifyNotCanceled`, `hasCancellationPermission` 같은 guard method를 business rule node로 노출
- exception handler와 직접 throw 외에 state guard failure도 rule chain에 포함

완료 기준:
- `/my/orders/{orderNo}/cancel`에서 permission/state guard가 graph와 final condition 양쪽에 반영됨

### Phase 3. Exporter와 request/response semantic 개선

목표:
- 최종 output에서 nested body/resource/auth 조건을 버리지 않음

대상 파일:
- [ExecutionSpecExporter.java](D:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/ExecutionSpecExporter.java)
- endpoint 추출/response 분류 관련 support classes

작업:
- `resolveCondition()`의 top-level body field 제한 제거 또는 완화
- `RESOURCE`, `AUTH` location 출력 지원
- framework parameter 제외
- MVC view response와 JSON response 분리

완료 기준:
- `/orders/order`, `/orders/orderConfirm`의 `@ModelAttribute OrderRequest`가 실제 구조로 출력되고, framework parameter가 query param에 노출되지 않음

### Phase 4. 회귀 테스트 확장

대상 파일:
- [OpenApiAssemblyServiceTest.java](D:/workspace/auto-oas/src/test/java/io/atworks/specscan/analysis/OpenApiAssemblyServiceTest.java)
- 관련 extractor/exporter 테스트

필수 시나리오:
- `/admin/orders/{orderNo}/shipping`
  - `PATH $.orderNo -> EXISTS_IN_REPOSITORY`
  - `QUERY $.version -> OPTIMISTIC_LOCK_MATCH`
  - `RESOURCE $.order.state -> STATE_IN`
- `/my/orders/{orderNo}/cancel`
  - `AUTH $.currentUser -> HAS_CANCELLATION_PERMISSION`
  - `RESOURCE $.order.state -> STATE_IN`
- `/orders/order`
  - nested body required path 유지
  - `@ModelAttribute` body 구조 확장
- MVC endpoint response classification
  - view response vs JSON response 구분

## 권장 구현 순서

1. `RuleBasedConditionNormalizer`와 `NormalizationService`에 projection 계층을 먼저 넣는다.
2. 그 다음 `ValidationEvidenceGraphBuilder`를 확장하여 resource/auth/domain guard metadata를 보강한다.
3. 마지막으로 `ExecutionSpecExporter`에서 drop 규칙을 줄이고 semantic 출력을 보정한다.
4. 각 단계마다 `ddd-start2`를 regression fixture로 삼아 비교한다.

## 이번 기준에서 바로 손대면 효과가 큰 포인트

- `ExecutionSpecExporter.resolveCondition()`의 top-level body field 필터
- `RuleBasedConditionNormalizer`의 예외명 기반 business rule 분류
- `ValidationEvidenceGraphBuilder`의 service -> domain method hop 추가
- framework parameter 제거 규칙

## 검증 전략

### 자동 검증

```powershell
./gradlew test
```

### 수동 검증

- [ddd-start2-demp-manual-spec.json](D:/workspace/auto-oas/ddd-start2-demp-manual-spec.json) 과 [ddd-start2-demp-auto-oas.json](D:/workspace/auto-oas/ddd-start2-demp-auto-oas.json) 을 비교한다.
- 특히 아래 엔드포인트를 우선 확인한다.
  - `POST /admin/orders/{orderNo}/shipping`
  - `GET /my/orders/{orderNo}/cancel`
  - `POST /orders/order`

## 승인 후 다음 액션

- 계획 승인 시 Phase 1부터 순서대로 구현한다.
- 구현 전에는 대상 파일 목록과 각 변경 이유를 다시 명시한다.
