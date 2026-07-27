# YAML Rule Engine PoC — U02 Configuration & Schema Summary

## Result

U02의 세 YAML source를 UTF-8로 읽고 strict validation 후 immutable `CompiledRecipePlan`으로 compile하는 evaluation-only bootstrap 경계를 구현했다. 기존 정상 Java scan과 기존 `analysis.support.rule.yaml` loader는 변경하지 않았다.

## Implemented

- `engine-config.yaml`, `semantic-recipes.yaml`, `framework-catalog.yaml` 책임별 source model
- default traversal budget `15 / 500 / 10000` 및 positive override 검증
- schema version, unknown field, duplicate ID, missing field, invalid value 검증
- `expression`, `custom_expression`, `script`, `callback`, `reflection`, `java_code` 등 forbidden field 거부
- primitive descriptor registry와 recipe step binding 이름/타입 검증
- unknown primitive, input arity, forward/undefined binding, type mismatch compile 거부
- deterministic recipe/catalog ordering과 canonical SHA-256 content digest
- immutable compiled collections
- YAML read/syntax diagnostic 보존
- `RecipePlanBootstrapService`를 evaluation-only service로 격리

## Evidence

- U02 bootstrap tests: valid compile/digest immutability, invalid budget/unknown/forbidden/duplicate, unknown primitive, missing YAML
- 전체 Gradle test: `BUILD SUCCESSFUL`
- 정상 scan 경로와 YAML bootstrap 사이 production dependency 추가 없음

## Boundary

U02는 schema/compile contract만 소유한다. graph traversal, primitive execution, semantic result 생성은 U03 이후 Unit의 책임이다. U02가 만드는 plan은 정상 scan의 입력이 아니다.
