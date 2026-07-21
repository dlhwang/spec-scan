# UOW-07 안전한 실행 Artifact 저장

## Goal

실행별 JSON을 output root containment와 원자적 최종화 규칙에 맞게 저장한다.

## 선행 조건

UOW-01과 UOW-02 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/port/out/RunArtifactPort.java`
- `src/main/java/io/atworks/apiintelligence/adapter/file/**`
- artifact DTO와 해당 테스트

## 출력 계약

```text
<output-root>/<yyyyMMdd-HHmmss>-<runId>/
├─ run.json
├─ apis.json
├─ result.json
└─ api/<safe-api-id>/
   ├─ graph.json
   ├─ evidence.json
   ├─ intelligence.json
   └─ diagnostics.json
```

- directory는 `CREATE_NEW`; 충돌 시 새 ID로 제한 재시도
- output root가 source 내부이면 거부
- normalized real path containment를 사용하고 root 밖 symlink/`..`를 거부
- UTF-8 pretty JSON을 staging 파일에 쓴 뒤 atomic replace
- graph/evidence는 OpenAI 호출 전에 저장 가능해야 함
- `intelligence.json`은 검증 성공 시에만 저장
- `result.json`은 실제 성공 artifact만 참조
- terminal `run.json`은 항상 마지막에 기록하며 artifact set의 권위 상태
- key, Authorization, provider body, prompt/request envelope 저장 금지

## 구현 절차

1. safe run/API directory resolver를 만든다.
2. generic atomic UTF-8 JSON writer를 만든다.
3. artifact별 명시적 write method를 구현한다.
4. aggregate result와 terminal manifest 최종화 순서를 구현한다.
5. allowlist DTO만 직렬화한다.

## 테스트와 완료 조건

- exact directory tree, collision, traversal, symlink
- source 내부 output 거부
- write/atomic move/finalization 실패 주입
- 실패 시 거짓 COMPLETED manifest가 없음
- secret/provider/prompt 문자열 artifact 미포함

