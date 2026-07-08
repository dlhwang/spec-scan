# Component Methods

## Repository Ingestion

### `RepositoryIngestionService`
- `RepositorySource ingest(String repositoryUrl)`
  - Validates input URL, prepares workspace, returns repository metadata

### `RepositoryFetcherPort`
- `FetchedRepository fetch(String repositoryUrl, Path workingDirectory)`

### `WorkspacePreparerPort`
- `RepositorySource prepare(FetchedRepository fetchedRepository)`

## Spring Static Scan

### `SpringStaticScanService`
- `StaticScanResult scan(RepositorySource repositorySource)`
  - Orchestrates endpoint extraction and type inventory creation

### `EndpointExtractor`
- `List<ApiEndpoint> extractEndpoints(RepositorySource repositorySource)`
- `ResolvedRequestMapping resolveRequestMapping(MethodDeclaration methodDeclaration, ClassOrInterfaceDeclaration controllerDeclaration)`
- `List<RequestBinding> extractRequestBindings(MethodDeclaration methodDeclaration)`
- `ResponseBinding extractResponseBinding(MethodDeclaration methodDeclaration)`

### `TypeResolver`
- `ResolvedTypeDescriptor resolveType(Type type, ResolutionContext context)`
- `List<ResolvedFieldDescriptor> resolveFields(ResolvedTypeDescriptor typeDescriptor)`
- `Optional<ResolvedTypeDescriptor> resolveGenericArgument(ResolvedTypeDescriptor typeDescriptor, int index)`

### `SourceTraceResolver`
- `SourceTrace resolve(Node node)`
- `SourceTrace merge(SourceTrace primary, SourceTrace secondary)`

## Validation Extraction

### `ValidationExtractionService`
- `ValidationExtractionResult extract(StaticScanResult staticScanResult)`
  - Coordinates annotation, validator, and service/domain extraction

### `AnnotationConditionExtractor`
- `List<ApiConditionDraft> extractDirectConditions(ApiEndpoint endpoint, ResolvedTypeDescriptor requestType)`
- `List<ValidationCandidate> extractUnsupportedAnnotations(ApiEndpoint endpoint, ResolvedTypeDescriptor requestType)`
- `Optional<ApiConditionDraft> mapAnnotation(FieldAnnotationDescriptor annotation, FieldContext fieldContext)`

### `ValidatorCandidateExtractor`
- `List<ValidationCandidate> extractValidatorCandidates(StaticScanResult staticScanResult)`
- `List<ValidationCandidate> extractConstraintValidatorCandidates(ClassOrInterfaceDeclaration validatorClass)`
- `List<ValidationCandidate> extractSpringValidatorCandidates(ClassOrInterfaceDeclaration validatorClass)`
- `List<ValidatorBinding> resolveInitBinderBindings(ClassOrInterfaceDeclaration controllerClass)`

### `ServiceHintExtractor`
- `List<ValidationCandidate> extractServiceHints(StaticScanResult staticScanResult)`
- `List<ValidationCandidate> extractConditionalHints(MethodDeclaration methodDeclaration, EndpointContext endpointContext)`
- `List<ValidationCandidate> extractExceptionHints(MethodDeclaration methodDeclaration, EndpointContext endpointContext)`

## Candidate Chunk Generation

### `CandidateChunkGenerationService`
- `ChunkGenerationResult generate(ChunkGenerationCommand command)`
  - Builds chunks and filters invalid candidates before LLM stage

### `CandidateChunkGenerator`
- `List<CandidateChunk> generateChunks(List<ValidationCandidate> candidates, EndpointCatalog endpointCatalog)`
- `CandidateChunk toChunk(ValidationCandidate candidate, EndpointContext endpointContext)`

### `CandidateChunkValidator`
- `List<InvalidCandidateRecord> validate(List<CandidateChunk> chunks)`
- `void assertUniqueCandidateIds(List<CandidateChunk> chunks)`

### `CandidateIdGenerator`
- `String generate(ValidationCandidate candidate, EndpointContext endpointContext)`

## LLM Normalization

### `NormalizationService`
- `NormalizationBatchResult normalize(List<CandidateChunk> chunks)`
  - Orchestrates prompt build, adapter call, response validation, retry/reject routing

### `PromptBuilder`
- `NormalizationPrompt build(CandidateChunk chunk)`
- `PromptEnvelope buildBatch(List<CandidateChunk> chunks)`

### `LlmNormalizationAdapter`
- `RawLlmResponse normalize(NormalizationPrompt prompt)`

### `LlmResponseValidator`
- `ValidatedNormalizationResult validate(RawLlmResponse response, CandidateChunk chunk)`
- `List<ValidationFailure> validateSchema(String rawJson)`
- `List<ValidationFailure> validateSemanticRules(ValidatedNormalizationResult result, CandidateChunk chunk)`

## Contract Assembly

### `ContractAssemblyService`
- `AssemblyResult assemble(AssemblyCommand command)`
  - Produces final `ApiCondition` inventory and OpenAPI contract output

### `ApiConditionAssembler`
- `List<ApiCondition> assembleConditions(List<ApiConditionDraft> directConditions, List<ValidatedNormalizationResult> normalizedResults)`
- `ApiCondition toCondition(ApiConditionDraft draft)`
- `ApiCondition toCondition(ValidatedNormalizationResult normalizedResult, ValidationCandidate sourceCandidate)`

### `OpenApiAssembler`
- `OpenApiContract assemble(List<ApiEndpoint> endpoints, List<ApiCondition> conditions)`

## Output

### `OutputService`
- `OutputManifest write(AssemblyResult assemblyResult)`

### `ResultStorePort`
- `StoredArtifact store(StoredArtifactRequest request)`
- `OutputManifest storeAll(List<StoredArtifactRequest> requests)`

### `FileResultStoreAdapter`
- `StoredArtifact store(StoredArtifactRequest request)`
- `OutputManifest storeAll(List<StoredArtifactRequest> requests)`
