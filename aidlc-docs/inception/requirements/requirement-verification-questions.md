# Requirements Clarification Questions

다음 질문은 `auto-oas.jar` 역공학 이후 다음 단계의 목표를 확정하기 위한 것입니다.
사용자 답변은 아래에 반영했습니다.

## Question 1
이번 작업의 1차 목표는 무엇입니까?

A) `auto-oas`의 구조와 동작을 정확히 이해하는 문서화가 목표다

B) `auto-oas`와 유사한 기능을 하는 대체 도구를 재구현하는 것이 목표다

C) `auto-oas`의 동작을 실제로 실행해 입력과 출력 결과를 검증하는 것이 목표다

D) 보안/오용 가능성 관점에서 분석하는 것이 목표다

X) Other (please describe after [Answer]: tag below)

[Answer]: X) GitHub Repository URL을 입력으로 받아 Spring 코드 기반 API Spec 및 Validation Condition 추출 PoC의 가능성을 검증하는 것이다. auto-oas의 구조와 동작은 참고하되, 최종 목표는 GitHub 주소를 통해 대상 프로젝트를 가져오고, Controller/DTO/Validator/Exception 후보를 분석해 구조화된 API Contract 모델을 만들 수 있는지 확인하는 것이다. 또한 다음 단계 산출물에는 전체 분석 워크플로와 LLM 호출 플랜을 반드시 포함한다.

## Question 2
재구현을 한다면 원하는 구현 수준은 어디까지입니까?

A) 핵심 개념만 재현하는 PoC면 충분하다

B) Spring 분석 기능만 우선 재현하면 된다

C) Spring과 JAX-RS를 모두 포함해 원본에 가깝게 재현하고 싶다

D) 아직 재구현까지는 아니고 설계안만 필요하다

X) Other (please describe after [Answer]: tag below)

[Answer]: B) Spring 분석 기능만 우선 재현하면 된다. 단, 단순 endpoint 추출에 그치지 않고 Spring MVC annotation, Bean Validation annotation, Custom ConstraintValidator, Spring Validator Bean, @InitBinder, BusinessException/ErrorCode 후보 추출까지 확장 가능한 구조로 설계한다. 정적 분석으로 추출한 후보를 LLM에 전달해 ApiCondition으로 정규화하는 흐름도 설계 범위에 포함한다.

## Question 3
재구현 언어 선호는 무엇입니까?

A) Java로 진행하고 싶다

B) Python으로 빠르게 PoC를 만들고 싶다

C) Java 본체와 Python 보조 스크립트를 함께 쓰고 싶다

D) 아직 결정하지 않았고 비교안을 보고 정하고 싶다

X) Other (please describe after [Answer]: tag below)

[Answer]: A) Java로 진행하고 싶다. Spring/Java 소스 분석이 핵심이고, GitHub Repository URL을 입력받아 임시 workspace에 clone한 뒤 JavaParser 또는 OpenRewrite 기반으로 Controller, DTO, Validator, Annotation, Type 정보를 분석하는 구조가 적합하다. LLM 호출은 정적 분석으로 추출한 validation candidate 단위로 수행하며, 본체는 Java로 구성한다.

## Question 4
원본 `auto-oas`와의 호환성 목표는 어느 수준입니까?

A) CLI 인자 형식과 출력 파일 구조까지 최대한 맞추고 싶다

B) 기능만 유사하면 되고 인터페이스는 달라도 된다

C) 핵심 엔드포인트 추출만 맞으면 된다

D) 아직 호환성 목표는 낮고 동작 원리 입증이 우선이다

X) Other (please describe after [Answer]: tag below)

[Answer]: B) 기능만 유사하면 되고 인터페이스는 달라도 된다. 원본 CLI 인자나 출력 파일 구조를 맞추는 것이 핵심이 아니다. 중요한 것은 GitHub Repository URL을 입력으로 받아 코드 기반 API endpoint, request/response schema, validation condition 후보를 추출하는 동작 원리를 검증하는 것이다.

## Question 5
다음 단계에서 실제 실행 검증 범위는 어디까지 필요합니까?

A) 문서와 설계만 있으면 된다

B) 샘플 Java 프로젝트 하나로 출력 OpenAPI를 확인하고 싶다

C) 원본 JAR와 재구현 결과를 비교하는 수준까지 원한다

D) Docker 이미지 내부 실행까지 포함해 재현하고 싶다

X) Other (please describe after [Answer]: tag below)

[Answer]: B) 샘플 Java 프로젝트 하나로 출력 OpenAPI를 확인하고 싶다. 단, 샘플 Java 프로젝트는 GitHub Repository 형태로 준비하고, 도구는 GitHub URL을 입력받아 repository를 clone한 뒤 분석한다. 출력은 OpenAPI뿐 아니라 내부 ApiCondition JSON도 함께 확인한다. 샘플 프로젝트에는 Controller, Request DTO, Bean Validation, Custom ConstraintValidator, Spring Validator Bean, @InitBinder, BusinessException 예시를 포함한다. 또한 실제 구현 전에 Source Ingestion → Static Scan → Candidate Extraction → LLM Normalization → JSON Validation → ApiCondition 저장까지의 워크플로 설계서를 작성한다.

## Question 6
이번 분석/재구현에서 가장 중요한 성공 기준은 무엇입니까?

A) 원본 동작과의 정확도

B) 빠른 실험과 구현 속도

C) 유지보수 가능한 구조

D) 향후 기능 확장 용이성

X) Other (please describe after [Answer]: tag below)

[Answer]: X) GitHub Repository URL만 입력해도 코드 기반 API Spec 추출 가능성과 Validation Condition 구조화 가능성을 증명하는 것이다. 단순히 원본과 동일한 출력을 만드는 것이 아니라, repository를 가져온 뒤 Spring Controller와 관련 DTO/Validator를 분석해 HEADER/PATH/QUERY/BODY/RESPONSE 위치별 조건을 ApiCondition 형태로 저장할 수 있어야 한다. 또한 정적 분석 결과와 LLM 보정 결과를 evidence, confidence, source trace와 함께 관리할 수 있어야 한다. 성공 기준에는 LLM 호출 단위, prompt input/output schema, 응답 파싱, JSON schema validation, 실패 처리, 캐싱 전략을 포함한 LLM 호출 플랜 작성도 포함한다.

## Question 7
Should security extension rules be enforced for this project?

A) Yes — enforce all SECURITY rules as blocking constraints

B) No — skip all SECURITY rules

X) Other (please describe after [Answer]: tag below)

[Answer]: B) No — skip all SECURITY rules. PoC 단계에서는 SECURITY extension rules를 blocking constraint로 강제하지 않는다. 다만 외부 GitHub Repository를 입력으로 받기 때문에 임의 코드 실행 없이 정적 분석 중심으로만 검증한다.

## Question 8
Should property-based testing (PBT) rules be enforced for this project?

A) Yes — enforce all PBT rules as blocking constraints

B) Partial — enforce PBT rules only for pure functions and serialization round-trips

C) No — skip all PBT rules

X) Other (please describe after [Answer]: tag below)

[Answer]: B) Partial — enforce PBT rules only for pure functions and serialization round-trips. Annotation-to-condition 매핑, operator 변환, JSON serialization/deserialization, OpenAPI 변환처럼 순수 함수에 가까운 영역에는 PBT를 적용한다. Git clone, JavaParser 기반 AST 분석, LLM 응답 해석처럼 외부 입력과 비결정성이 큰 영역에는 우선 일반 단위 테스트와 샘플 repository 기반 통합 테스트를 적용한다.
