# sm-workflow: network host terminology and development handoff

Date: 2026-10-23
Status: specification and development plan updated; implementation pending
Owner: sm-workflow development chat

## Adopted decision

At the user's direction, reserve `host` for a network destination hostname/address.
Do not use it to name a caller, runtime, configuration directory, composition or
evidence issuer. The authoritative contract is the
[terminology specification](../../../spec/runtime-caller-terminology.md).
Implementation order and acceptance criteria are defined in the
[N-01 through N-04 adoption plan](../../../notes/runtime-caller-terminology-adoption-plan.md).

This is a project terminology convention. Broader industry uses such as
"plugin host" or "runtime host" are valid in their own contexts; the purpose here
is to distinguish responsibilities within sm-workflow. Use Endpoint for a full
connection endpoint that also includes protocol, port or path.

| Existing responsibility and representative name | Target name |
| --- | --- |
| Caller execution bridge `NativeWorkflowHostExecution` | `NativeWorkflowCallerExecution` / CallerIntegration |
| Composition `ThreeWorkflowLauncherAssembly.Host` | `RuntimeComposition` |
| Configuration `ThreeWorkflowHostParameters` / `sm-workflow.host.definition` | `ThreeWorkflowRuntimeParameters` / `sm-workflow.runtime.definition` |
| Configuration and execution directory `--host` | `--runtime-root` / `runtimeRoot` |
| Passive preparation `NativeHostPreparationService` | `NativeWorkflowPreparationService` |
| Evidence with issuer attribution `NativeHostEvidence` | `NativeExternalEvidence` |
| Review/Human `hostContext` | `reviewContext` / `humanDecisionContext` |
| Input selection `host-records.*` | `input-records.*` |

Align types, CML/generated DTOs, wire fields/schema labels, configuration keys,
CLI/help and distributed skills. Do not blindly replace every occurrence of Host.
Use the specification's mapping and actual responsibilities for remaining names.
Preserve genuine network host names and externally defined APIs such as `hostId`.

## Instructions for the development chat

1. Continue current work. In N-01, identify affected declarations, consumers and
   persisted inputs. Start with names on the main path of
   [caller information return CI-01 through CI-04](../../../notes/codex-caller-information-adoption-plan.md).
   Do not start a new Phase/Goal or reopen Phase 6.4/6.5 acceptance.
2. In N-02, align both ends of caller request → returned observation → same-Session
   continuation. Update runtime root/composition, preparation, normal launchers and
   distribution configuration within that connection. Use normal CML generation
   and compilation to detect stale references. Retain no old CLI flags,
   configuration keys, type aliases, forwarders or dual readers. Do not introduce
   blanket unknown-key rejection.
3. In N-03, align review/Human/authority contexts, input records, external evidence
   and current documentation. Classify remaining non-network host references by
   responsibility; exclude historical records and external API names.
4. In N-04, convert only genuinely affected persisted inputs once, outside normal
   readers. Retain original records and the conversion mapping in the existing
   evidence location. Preserve Session, continuation and Step identities, repair
   counts and business values. Leave unaffected records untouched. Let existing
   calls finish before switching related configuration and runtime together.
   Renaming alone must not move directories or evidence storage.

Validate changed connections with focused tests, normal generation/compilation,
adoption fixtures and one representative continuation round trip. Reuse existing
validation within its actual scope. Renaming alone must not trigger renewed
approval, all reviews or all tests. Returned observations do not constitute Human
consent or permission to recover or Advance.

## Preserved work and validation boundaries

At the time of this handoff, the
[adoption status record](2026-10-23-codex-caller-information-adoption-status.md)
states that the caller-return responsibility change is accepted, while the minimal
CI-01 request/result ingress is incomplete. This document update establishes
neither implementation completion nor successful adoption into a live Session.

- Preserve Session `96c294d73be3d48067f3721d8a2abf33`, Goal, Plan, histories,
  repair counts and accepted results.
- Preserve CNCF's 42 passing tests and local publication, and existing
  protocol/packaging/caller validation, within their original scope.
- Retain sm-focused01/02 failures, sm-focused03's 27 tests with 8 passes and
  19 failures, and findings and repairs from reviews01 through 03. Do not count
  obsolete provider validation as success for the new caller path, or resume
  repairs solely to qualify the obsolete provider.
- Do not interrupt or reissue already-issued work or a separately requested
  runtime refresh solely for this rename. Do not change configuration underneath
  active calls; adopt the change at an appropriate switch boundary.

`HostSourceRead` / `host_source_read` remain withdrawn proposals. Do not revive
them as a renamed generic read DSL. Obtain Codex information by returning work
to the existing caller. `process_exec_text` remains a development candidate.
Do not rewrite historical evidence bodies, signatures or hashes to present them
as evidence produced by the new interface. Removing compatibility must not erase
history.

## Scope and verification of this update

Only specification, design, development-plan and handoff documents were changed.
Implementation, deployment, persisted Sessions and active processes were not
changed. Implementation validation remains in N-02 through N-04; checks for this
update cover document differences and local links.

Local links in the new specification, plan and this handoff resolved, and the
selected diff passed whitespace checks. Across 14 related documents, one existing
relative link to `registered-sbt-host-execution.md` and three old Phase 5.2 work
evidence links were unresolved. The SBT specification exists in CNCF/shared skills,
but not at that relative destination. These links were not introduced by the
rename; existing record references were preserved.

## Journal language

At the user's direction, journal entries authored in this conversation are written
in English. This handoff and the related terminology addendum have been translated;
the preservation and adoption requirements remain unchanged.
