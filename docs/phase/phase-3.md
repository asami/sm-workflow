# Phase 3: Resolved Failure Model for AI Implementation

## Delivery sequence (2026-10-05)

Follow the [incremental-use plan](README.md): a bounded practical Phase 2,
then the Phase 5 runtime foundation and Phase 6 thinking-mode/skill integration.
Gradual real use starts after Phase 6 acceptance and continues through Phase 7,
followed by Phase 8 closure.
Phase 3 is an orthogonal failure-model extension, not a prerequisite for that
initial use sequence. Schedule it according to real modeled-development needs;
its CNCF/Cozy dependencies and own completion condition remain unchanged.

## Goal

Make ResolvedFailureModel a first-class implementation condition passed by sm-workflow to AI workers.

## Scope

- Add ResolvedFailureModel to implementation context/protocol.
- Load the resolved model from CNCF/CAR metadata for modeled CAR development.
- Ensure AI prompts/invocations receive the resolved contract.
- Prohibit AI-side implicit expansion of the Failure Model.
- Treat OUT_OF_SCOPE failures as constraints against speculative defensive code.
- Define FailureModelGap as the handoff when an unmodeled failure appears materially relevant.
- Connect FailureModelGap to upstream model revision/admission rather than local defensive implementation.
- Add deterministic fixtures and executable specifications proving the resolved model reaches the worker unchanged.

## Dependencies

- CNCF Phase 91: Execution Model / Failure Model / resolution semantics.
- Cozy Phase 72: CML authoring and CAR metadata generation.

## Completion

An implementation workflow for a CAR can demonstrate CML-defined failure semantics flowing through CNCF resolution into sm-workflow and the AI implementation request.
