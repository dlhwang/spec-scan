# Work Unit 07: 전달 순서와 통합 게이트

## 목적

Unit 00~06의 결과를 안전하게 전달하고, 신규 출력 경로 전환과 legacy 제거를 서로 다른 승인 단계로 관리한다.

## 전달 순서

```text
Unit 00 기준선 → Unit 01 Fact Code Graph → Unit 02 Candidate 모델
→ Unit 03 Rule Engine → Unit 04 Initial Rule Pack
→ Unit 05 정규화 및 출력 마이그레이션 → Unit 06 품질 평가
→ Unit 07 전달 판정: COMPARE 유지 / NEW_ONLY 전환 / legacy 제거 승인
```

## 모드 계약

| 모드 | 용도 | 외부 출력 |
|---|---|---|
| `LEGACY_ONLY` | 장애 시 즉시 rollback | legacy 경로 |
| `COMPARE` | 기본값, 차이 수집 | legacy 경로 + 비교 보고서 |
| `NEW_ONLY` | 전달 게이트 통과 후 전환 | 신규 분류 출력 |

`specscan.output.migration-mode`가 없거나 잘못된 값이면 `COMPARE`를 사용한다. 기본값 변경은 코드 배포와 분리한다.

## `COMPARE → NEW_ONLY` 게이트

다음 증빙이 모두 있어야 `NEW_ONLY` 전환 준비 완료로 판정한다.

1. Unit 06 품질 게이트 통과
2. 실제 프로젝트 회귀 테스트 통과
3. 출력 소비자 호환성 확인
4. legacy/new 차이 검토 완료
5. `LEGACY_ONLY` rollback 검증 완료

하나라도 없으면 권장 모드는 `COMPARE`다. `DeliveryReadinessService`가 이 조건을 코드와 테스트로 고정한다.

## legacy 제거 게이트

`NEW_ONLY` 전환 가능 판정만으로 legacy 코드를 제거하지 않는다. `NEW_ONLY` 실제 실행을 한 번 이상 성공적으로 관찰하고 legacy 제거를 명시적으로 승인해야 한다. 전환 직후에도 `LEGACY_ONLY` rollback 경로를 보존한다.

## 실행 명령

```powershell
.\gradlew.bat ruleEvaluation
.\gradlew.bat deliveryVerification
.\gradlew.bat test
```

## 현재 판정

- Unit 06 synthetic/holdout 품질 게이트: 통과
- 실제 프로젝트 회귀 증빙: 미확보
- 출력 소비자 호환성 증빙: 미확보
- 권장 모드: `COMPARE`
- `NEW_ONLY` 기본 전환 및 legacy 제거: 보류

## 완료 정의

- 잘못된 설정값은 안전하게 `COMPARE`로 복귀한다.
- 명시적 `LEGACY_ONLY` rollback이 유지된다.
- 실제 프로젝트 및 소비자 증빙 없이는 `NEW_ONLY`를 권장하지 않는다.
- `NEW_ONLY` 관찰과 명시적 승인 없이는 legacy 제거를 허용하지 않는다.
- Unit 06 평가와 Unit 07 전달 검증을 독립적으로 재실행할 수 있다.
