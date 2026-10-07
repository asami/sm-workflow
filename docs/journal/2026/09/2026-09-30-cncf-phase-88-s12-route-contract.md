# Phase 88 S12 route-first correction

Date: 2026-09-30
Status: route contract plus focused default-deny/host-policy candidate validated; complete S12 executable acceptance not yet implemented

The S12 implementation order is Workflow → `DataStoreSpace` → one physical
provider. The prior same-owner memory staging remains an internal candidate,
not a public capability. A direct SQLite candidate was started but withdrawn
before any SBT test: it would have bypassed the still-undefined Workflow and
`DataStoreSpace` authority boundary. The exploratory SQL additions were
removed; the focused-287 memory source and test bytes were preserved.

The CNCF route contract in
`docs/design/workflow-multi-guard-transaction.md` defines default-deny
host-installed exact owner/collection permissions, same-provider-object
resolution for root, successor, all extra guards and side Creates, opt-in
provider capability, one physical commit/rollback, and post-acknowledgement
readback before Workflow success projection. The host binding must come from
trusted runtime/component assembly, not from the pure plan, an owner label,
skill, or AI action.

Next executable specifications must call the Workflow adapter and
`DataStoreSpace` operation, first proving rejection before provider
invocation with a counting fake and then proving memory/SQLite success,
rollback, independent-writer exclusivity, and missing/divergent readback.
No such S12 route spec has passed yet. No new SBT attempt, P2-U15 acceptance,
artifact refresh, commit or publication is claimed by this correction.

## Follow-up: host admission source

Source inspection found that `DataStoreSpace` permits provider mutation and
thread-local rebinding, while `WorkflowInstancePersistenceBinding` acquires a
fresh managed provider inside the `Subsystem` lease. An immutable grant on
the mutable global space alone would not establish a stable transaction
domain. The design note now requires a call-scoped admitted view, constructed
by trusted component assembly inside the lease from canonical component
identities and exact collections, with one provider object throughout the
call. A path match between two SQL wrappers is insufficient. This is a
contract clarification, not an executable implementation or validation.

## Follow-up: fail-closed entry candidate

`DataStoreSpace.conditionalMultiGuard` now revalidates the S12 plan and
rejects with `host-admission-required` before resolving any provider. Two
examples were added to the pure-plan spec to check valid cross-owner and null
plan rejection without a provider lookup. This preserves the existing
two-record operation and keeps the internal memory candidate separate. These
source changes have only passed `git diff --check`; no SBT receipt or public
capability acceptance is claimed. The positive host-admitted route remains
the next required implementation slice.

## Follow-up: comparison separated from authority

The candidate `DataStoreConditionalMultiGuardHostPolicy` now performs only
deterministic exact owner/collection comparison. Its spec covers accepted
comparison and wrong Workflow owner/root, guard owner, and side-Create
collection. A matching policy is deliberately not accepted by the
`DataStoreSpace` operation: it is not an authority token. Source inspection
also confirmed that the current `EventStore` is held by `Subsystem` and has
no canonical datastore declaration comparable to Workflow persistence. Host
assembly must define that trusted EventStore authority source before a
positive cross-owner route can be issued. At this point in the sequence,
SBT validation had not yet run and S12 acceptance was not claimed.

## Focused validation of the candidate

Attempt `SMWF-P2-CNCF-P88-S12-ROUTE-FOCUSED-20260930-288` reached SBT but
stopped before tests because the Cozy runtime classpath was stale
(`sbt_exit=1`, lock released). Cozy attempt
`SMWF-P2-COZY-P88-S12-CLASSPATH-20260930-289` completed `compile` and
`cozyExportRuntimeClasspath` (`sbt_exit=0`, lock released). CNCF attempt
`SMWF-P2-CNCF-P88-S12-ROUTE-RETEST-20260930-290` then passed the S12 plan
spec and existing conditional-update spec: **20 tests, 20 passed**, with
`sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. The accepted receipt is
`/tmp/skill.cncf.d/cncf-command-execution-61b121a7218af5694e3283a50f7cc73811620aafb59ad4924edd2c237b1acdbd-68f6eca9fab5ac6fad50273f090886d8/command-execution-sha256-6461970528301115aa250ac8ad7e7f144aedc2095cd0169ae9ec9d03df6edfb9`.

This establishes only the fail-closed route and deterministic comparison
candidate, not a host-issued admission, cross-owner atomicity, SQLite S12,
post-commit readback, P2-U15 acceptance, commit or publication.

## Authority-source audit after focused-290

Read-only source inspection found `Subsystem._event_store` is a fixed
`EventStore.inMemory`; `ComponentFactoryWorkflowPart` passes that service to
components. There is no canonical persisted EventStore declaration from which
the host can derive cursor/event/delivery-index collection permissions.
Workflow persistence declares only its own instance collection. The design
note now records the two viable models and recommends an opt-in
Subsystem-owned EventStore persistence declaration, preserving the current
in-memory default. No positive admission implementation or new SBT run was
performed in this audit; P2-U15 remains open pending the authority-source
decision and route executable specs.

## Subsystem host candidate and selected-event correction

The Subsystem-owned service option was selected. Its typed declaration and
managed-lease host view are now a compiled, focused-tested candidate, with
legacy/unknown grants denied and separate physical managed resources rejected.
One admitted shared-resource view still rejects unsupported multi-guard
capability; it is not a completed positive provider route. An escaped view
also rejects use after lease release.

The user's selected-event correction is recorded in the
[selection journal](2026-09-30-event-persistence-selection.md): the declaration
admits destinations/permissions only, Workflow evaluates exact versioned kind
rules, and transient delivery remains pending until verified success. Rollback
drops pending delivery; uncertainty suppresses it without automatic retry.
Pure policy/selection and host-separation specs plus the selected regression
passed focused-293 (45/45 in five suites) after Cozy prerequisite-292.
Public S12 provider, actual rollback/dispatch integration and P2-U15/P2-U16
remain open. No acceptance commit or publication occurred.
