# Phase 3: Resolved Failure Model for AI Implementation

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
