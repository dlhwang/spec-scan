# API Intelligence Code Graph Unit of Work

각 UoW는 문서 하나만 읽고 구현할 수 있도록 Goal, 범위, 계약, 허용 파일, 절차, 테스트 및 완료 조건을 포함한다. 선행 UoW가 완료되지 않았다면 후속 작업을 시작하지
않는다.

## 실행 순서

1. [UOW-01 독립 도메인 모델](UOW-01-domain-model.md)
2. [UOW-02 설정과 비밀정보](UOW-02-configuration-security.md)
3. [UOW-03 Source Workspace](UOW-03-source-workspace.md)
4. [UOW-04 API 발견](UOW-04-api-discovery.md)
5. [UOW-05 Code Graph](UOW-05-code-graph.md)
6. [UOW-06 Evidence](UOW-06-evidence.md)
7. [UOW-07 Artifact](UOW-07-artifacts.md)
8. [UOW-08 OpenAI](UOW-08-openai.md)
9. [UOW-09 Run Registry](UOW-09-run-registry.md)
10. [UOW-10 Orchestration](UOW-10-orchestration.md)
11. [UOW-11 HTTP와 Bootstrap](UOW-11-http-bootstrap.md)
12. [UOW-12 UI](UOW-12-ui.md)
13. [UOW-13 E2E와 Construction](UOW-13-e2e-construction.md)

```text
01
├─ 02 ───────────────┐
├─ 03 → 04 → 05 → 06 ├→ 10 → 11 → 12 → 13
├─ 07 (01+02) ───────┤
├─ 08 (01+02+06) ────┤
└─ 09 ───────────────┘
```

UOW-03~06, UOW-07, UOW-09는 선행 계약 완료 후 병렬화할 수 있다. UOW-08은 Evidence 계약이 필요하며 UOW-10부터는 순차 통합한다.

## 공통 완료 규칙

- 허용 파일 밖의 변경이 필요하면 구현보다 문서를 먼저 변경한다.
- 기존 `io.atworks.specscan` 모델과 `/api/scan` 파이프라인에 결합하지 않는다.
- focused test와 `git diff --check`를 통과한다.
- secret, provider body 및 stack trace를 artifact나 HTTP 응답에 노출하지 않는다.
- 완료 증거를 `construction.md`의 해당 UoW에 기록한다.

