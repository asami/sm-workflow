# Integration responsibility choices

Related notes: [server application](../../../notes/sm-workflow-server-application.md),
[service-bus events](../../../notes/service-bus-development-events.md),
[security governance](../../../notes/textus-security-development-governance.md).

## Operational orchestration and work orchestration are orthogonal

Control Center manages subsystem health, startup and operational recovery.
An orchestrator coordinates accepted work and participants. Neither relationship
is a hierarchy: an orchestrator can request authorized management while Control
Center observes the orchestrator itself. Making one a mandatory gateway for
every action would unnecessarily stop independent EAI/sm-workflow activity when
that gateway is unavailable.

This distinction also explains why a monitoring display is not a new workflow
authority. The owning subsystem retains its state and result; an event or UI
projection does not manufacture completion or permission.

## Development tooling is not product scope

Maintaining development-workstation's Python/TOML or skill bundle does not
justify adding those special checks to ordinary repository synchronization.
The selected Scala/CAR use case can require its normal SBT acceptance without
becoming an arbitrary-language execution system. Similarly, a registered SBT
program does not grant unrelated File/HTTP/database capabilities.

## A handoff slice should break a circular dependency

Waiting for both producer and consumer Phases to finish before either can
accept a connection creates a cycle. An accepted producer slice and its actual
consumer result are a smaller handoff boundary. The same reasoning keeps later
multi-project integration and CBD review adoption from becoming retrospective
prerequisites for already accepted single-repository use.

Review preparation, issuing work and accepting a returned assessment also have
distinct owners. Re-running another AI review at Submit would duplicate work
and misattribute its result. Provider metadata must describe the actual
execution. The owning plans retain adoption status; this explanation starts no
deferred provider or successor Phase.
