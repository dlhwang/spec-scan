# 컴포넌트 인벤토리

## 애플리케이션 패키지

- `io.atworks.specscan`: CLI, Demo, Web 서버, Git 실행 facade.
- `ingestion.application/port/adapter/domain`: 수집 use case, 포트, JGit/임시 workspace 어댑터와 계약.
- `analysis.application`: scan, extraction, normalization, migration, assembly.
- `analysis.domain`: 공통 API 분석 모델.
- `analysis.domain.candidate/fact/rule`: 후보, 사실 그래프, 규칙 계약.
- `analysis.domain.output/evaluation/delivery`: 출력, 품질 지표, 전달 준비 상태.
- `analysis.support`: AST 추출, export, evidence, normalization 구현.
- `analysis.support.candidate/fact/rule/output`: 도메인별 구현체.
- `analysis.support.rule.pack/yaml`: 내장 및 사용자 YAML 규칙 팩.
- `analysis.support.evaluation/delivery`: 평가 보고서와 release gate.

## 인벤토리 집계

- 운영 Java 소스: 189개.
- 테스트 Java 소스: 44개.
- Web 리소스: 3개.
- 평가/회귀 fixture: 7개.
- 인프라 패키지: 0개.

## 테스트 패키지

ingestion, endpoint/extraction, evidence, fact, candidate, rule, YAML, output migration, evaluation 및 delivery 계층을 단위·통합·속성 테스트로 검증한다.
