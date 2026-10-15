# Safety Sandbox adoption as a development driver

Date: 2026-10-15
Status: planned after basic workflow operation

## Decision

sm-workflow will adopt CNCF Safety Sandbox after the basic workflow functions are operational. Sandbox implementation must not delay the initial command-driven workflow foundation.

The adoption is intentionally a development driver for the shared platform rather than a private sm-workflow protection layer.

## Scope model

CNCF ExecutionContext carries the resolved Effective Sandbox. sm-workflow maps the generic CNCF scopes as follows:

- Server Sandbox: maximum authority of the future resident sm-workflow server. During initial CLI use this is a local/runtime deployment default.
- Client Sandbox: one command/request/project invocation and its admitted repository/worktree authority.
- Operation Sandbox: one workflow Operation, Step or Worker role and the minimum effects it needs.

CNCF resolves Server / Client / Operation by intersection before execution. A lower scope cannot widen an upper scope.

## Adoption sequence

1. Complete and exercise the basic command-driven sm-workflow path.
2. Introduce Client/Command and Operation sandbox definitions on that real path.
3. Use actual Git, SBT, worktree, source-editing and provider operations to discover missing CNCF managed-effect boundaries and CBD lint/review rules.
4. Fix generic gaps in goldenport-cncf and textus-cbd-support rather than introducing sm-workflow-specific escape routes.
5. When resident-server operation is introduced, add Server Sandbox as the outer scope while retaining the same Client and Operation semantics.

## Development-driver role

sm-workflow is expected to stress the Safety Sandbox more strongly than an ordinary business CAR because AI-driven development legitimately needs powerful effects. This makes it a useful proving ground for ResourceTree/worktree write boundaries, registered Git/SBT/process execution, arbitrary shell rejection, cross-repository authority, provider/network authority, CAR Loading Admission classifications, CAR lint/CBD Review diagnostics, and Sandbox violation observability/model-quality feedback.

The target is not to make AI incapable of development work. The target is to give each execution only the effects admitted for that server/client/operation context and to make unexpected techniques fail deterministically.

## Ownership

- CNCF owns SandboxDefinition/Resolver/Context, ExecutionContext binding, managed-effect enforcement and CAR Loading Admission.
- CBD Support owns deterministic lint/review consumption and human interpretation.
- sm-workflow owns its server/client/operation profiles and workflow-specific mapping of repository/worktree/command authority.
- Docker/process isolation owns hostile-code containment when required.
