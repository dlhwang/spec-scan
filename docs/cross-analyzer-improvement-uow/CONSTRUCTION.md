# Construction Log

| Unit | 상태 | 현재 목표 | 검증 |
|---:|---|---|---|
| UoW-10 | 완료 | corpus 결함을 독립 fixture로 특성화 | binding/Optional/password/response fixture GREEN |
| UoW-11 | 완료 | binding wire name을 graph와 semantic target에 보존 | `id → $.Authorization` GREEN |
| UoW-12 | 완료 | custom repository Optional 의미 분류 | repository와 JDK Optional 구분 GREEN |
| UoW-13 | 완료 | 외부 상태 prerequisite 출력 경계 | additive 출력과 기존 excluded 호환 GREEN |
| UoW-14 | 완료 | delegated PasswordEncoder 전파 | depth 2와 failure evidence polarity GREEN |
| UoW-15 | 완료 | generic/Jackson/factory response contract | generic 치환·`JsonIgnore`·factory 상수/example GREEN |
| UoW-16 | 완료 | canonical project graph와 endpoint projection | canonical node/edge membership 및 RuleOutput projection GREEN |
| UoW-17 | 완료 | comparative golden quality gate | response coverage/unresolved/wire target/prerequisite metrics GREEN |

## 최종 검증

- `./gradlew test`: 203 tests, 1 skipped, BUILD SUCCESSFUL
- response factory 상수는 모든 정상 return 경로에서 동일할 때만 assertion으로 승격한다.
- `spec-scan`에는 쓰기 작업을 수행하지 않았다.

## 작업 규칙

- 각 Unit은 RED test가 먼저 실패하는 것을 확인한다.
- 최소 구현 후 해당 Unit 테스트와 관련 회귀를 실행한다.
- 전체 테스트가 통과한 뒤에만 완료로 표시한다.
- 실제 외부 repository를 테스트에서 clone하거나 직접 참조하지 않는다.
