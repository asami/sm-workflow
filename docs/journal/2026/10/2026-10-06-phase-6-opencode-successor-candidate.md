# Phase 6 successor candidate: OpenCode Execution Provider

Date: 2026-10-06
Status: future candidate / not Phase 6 scope

OpenCode was reviewed after comparing sm-workflow with VirtusLab Orca. Orca demonstrates that OpenCode can serve as a practical agent backend and can front local/self-hosted model environments as well as cloud providers.

The architectural fit with sm-workflow is good: Phase 6 already aims to separate semantic WorkClassification and logical ExecutionRequirement from concrete Provider Selection. OpenCode can therefore be added later as one provider adapter rather than becoming a Workflow concept.

Decision: do not add OpenCode implementation to Phase 6. Phase 6 should close after the generic ExecutionRequirement / Provider Selection / ExecutionEvidence contract is proven. A successor Phase may implement OpenCode as a concrete provider and use Ollama/local-LLM operation as an important driver scenario.

Codex remains a parallel provider. A possible future routing policy can send bounded/simple implementation or TEST_FIX work to a local model through OpenCode while retaining stronger cloud providers for work whose resolved execution/reasoning requirement demands them. Such mapping belongs to environment configuration/provider resolution, not Workflow transition semantics.

VirtusLab Orca's OpenCode backend should be used as implementation research for server/session lifecycle, streaming, tool/result handling, model selection and evidence/cost capture. Its concrete architecture is not copied into sm-workflow; the CNCF/sm-workflow generic execution contracts remain authoritative.

This sequencing keeps Phase 6 focused while giving its abstraction an immediate post-closure validation target.
