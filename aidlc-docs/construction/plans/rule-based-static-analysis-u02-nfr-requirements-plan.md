# Unit 02 후보와 해석 모델 NFR Requirements 계획

## Metadata

- **Unit**: `rule-based-static-analysis-u02`
- **Stage**: NFR Requirements
- **Created**: `2026-07-14T12:52:37+09:00`
- **Status**: NFR Requirements 생성 완료, 승인 대기

## 단계 계획

- [x] Functional Design 승인 기록
- [x] cross-cutting 및 Unit 01 NFR 대조
- [x] 성능·신뢰성·결정성·안전성 적용성 평가
- [x] PBT 부분 적용 대상 확인
- [x] 기술 스택 변경 필요성 평가
- [x] `nfr-requirements.md` 생성
- [x] `tech-stack-decisions.md` 생성
- [ ] NFR Requirements 승인

## 적용성 평가

| NFR 영역 | 적용 여부 | 근거 |
|:---|:---|:---|
| Performance | 제한 적용 | 후보 생성은 Fact node와 Rule 평가 수에 대해 선형이어야 함 |
| Scalability | 제한 적용 | 복수 Rule 후보를 보존하되 중복이나 비결정적 증폭을 방지해야 함 |
| Reliability | 적용 | 잘못된 상태 조합, evidence 누락, 부분 실패를 격리해야 함 |
| Determinism | 적용 | 동일 Fact와 Rule 결과에서 동일 candidate ID가 필요함 |
| Traceability | 적용 | 모든 후보가 Fact node evidence로 역추적 가능해야 함 |
| Security | cross-cutting 적용 | 대상 코드를 실행하지 않고 source snippet을 최소 보존 |
| Availability | N/A | 로컬 CLI PoC이며 서비스 uptime 요구 없음 |
| Infrastructure | N/A | 신규 배포 또는 외부 인프라 없음 |
| Property-Based Testing | 부분 적용 | ID와 상태 조합 invariant는 순수 도메인 검증에 적합 |

## NFR 단계 실행 결정

Unit 02 고유 상태 조합과 참조 무결성은 Functional Design만으로 구현 수용 기준을 충분히 고정할 수 없으므로 NFR Requirements를 실행한다. 신규 tech stack 선택은 없으며 Unit 01에서 승인된 테스트 도구를 재사용한다.
