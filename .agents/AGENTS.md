# AI-DLC Agent Kit 지침

## 적용 범위

- 소프트웨어 개발 작업에서는 `.agents/.aidlc/aidlc-rules/aws-aidlc-rules/core-workflow.md`를 따른다.
- `core-workflow.md`는 변경하지 않는다. 이 문서는 에이전트 선택과 위임에 관한 보완 지침이다.
- 에이전트 선택 정책의 단일 원본은 `.agents/agent-selector.toml`이다. 이 문서에 선택 지점, 후보 목록, 출력 형식 등의 세부 정책을 복제하지 않는다.
- `.agents/skills/CATALOG.md`가 존재하면 작업에 적합한 스킬을 확인하고, 선택한 스킬의 `SKILL.md`를 따른다.
- `aidlc-docs/inception/reverse-engineering/`가 존재하면 아키텍처 및 도메인 분석 시 해당 산출물을 참고한다.

## 필수 연동

1. 워크플로 시작 시 `core-workflow.md`와 `.agents/agent-selector.toml`을 읽는다.
2. `agent-selector.toml`의 `phase0.trigger`가 지정한 시점마다 메인 Codex가 Agent Selector Phase 0를 먼저 수행한다.
3. 특히 모든 Unit of Work Generation Plan 단계와 task-specific subagent 생성 전에 selector 결정을 완료한다.
4. Agent Selector는 구현 subagent로 생성하지 않는다. 메인 Codex가 TOML의 `developer_instructions`를 적용하여 선택 JSON을 만든다.
5. Agent Selector Phase 0는 실제 subagent 생성 권한과 무관하게 readytoagent 카탈로그를 검색하고 현재 단계에 적합한 후보를 평가한다.
6. 계획 및 분석 단계에서는 read-only 에이전트만 허용한다. workspace-write 에이전트는 관련 계획에 대한 사용자의 명시적 승인 후, 승인된 범위에서만 허용한다.
7. Unit of Work 관련 세부 규칙 파일은 `core-workflow.md`의 **MANDATORY: Rule Details Loading** 절에서 결정한 rule details 디렉터리를 기준으로 해석한다.
8. task-specific subagent는 selector JSON의 `selected_agents`에 포함된 카탈로그 에이전트만 생성한다. 적합한 에이전트가 없으면 임의의 에이전트를 만들지 않는다.
9. 실제 spawn이 허용되고 selected subagent가 생성된 경우 해당 작업을 완료한 후에만 워크플로 단계를 계속한다. Spawn이 허용되지 않은 경우에는 메인 Codex가 selector 결과를 참고하여 해당 단계를 수행한다.

## 설정 파일 오류 처리

- `.agents/agent-selector.toml`을 읽을 수 없거나 필수 필드를 해석할 수 없는 경우 task-specific subagent를 생성하지 않는다.
- 이 경우 메인 Codex는 read-only 범위에서만 현재 단계를 수행하고, 차단 사유와 필요한 사용자 승인을 기록한다.

## 선택과 Spawn 분리

- `selected_agents`는 현재 작업에 적합한 후보 선택 결과이며 실제 spawn 여부를 의미하지 않는다.
- Spawn 권한이 없다는 이유만으로 `selected_agents`를 비우지 않는다.
- `selected_agents`를 빈 배열로 반환할 수 있는 경우는 카탈로그를 평가했지만 적합한 agent가 없거나 agent가 필요하지 않은 경우뿐이다. 이때 `selection_notes`에 구체적인 근거를 기록한다.
- 선택된 agent가 있지만 상위 지침 또는 사용자 권한 때문에 spawn할 수 없으면 후보는 `selected_agents`에 유지하고 `unit_of_work_generation.ready_for_selected_subagents=false`로 기록한다.
- Spawn 차단 사유, 필요한 사용자 권한과 메인 Codex 대체 수행 여부를 `selection_notes`에 기록한다.
- `blocked_workspace_write_agents`는 planning/analysis 단계의 mode 제한을 기록한다. Spawn 권한 부재와 agent 적합성 판단을 혼합하지 않는다.
- Planning/analysis 단계에서는 read-only 후보를 선택한다. Generation 단계에서는 관련 계획 승인 후 workspace-write 후보도 선택할 수 있지만 실제 spawn 권한은 별도로 확인한다.
- 각 Phase 0 기록에는 최소한 `후보 선택 결과`, `spawn 허용 여부`, `실제 생성 여부`, `메인 Codex 대체 수행 여부`를 구분해 남긴다.

## 충돌 처리

- 단계 실행, 승인, 감사 기록 및 산출물 규칙은 `core-workflow.md`를 따른다.
- 에이전트 선택, 권한 모드, 카탈로그 제한 및 task brief 규칙은 `.agents/agent-selector.toml`을 따른다.
- 선택 결과와 spawn 권한이 충돌하면 선택 결과는 보존하되 task-specific subagent를 생성하지 않고 메인 Codex가 해당 단계를 수행하며, 충돌 내용을 사용자에게 알린다.
