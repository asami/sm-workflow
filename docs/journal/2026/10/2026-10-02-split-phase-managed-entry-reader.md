# SplitPhase managed entry record reads

Goal State: IMPLEMENT, same sm-workflow Phase 2 and original completion scope.
Previous turn progressed: independent selection reader, actual managed reads,
expired call denial and 224 passing tests in invocation 695. Continue retained
user-authorized parent-direct limited recovery. Parent runtime model/effort
are not directly observable; standard active-interface mode, no agent used.
The explicit user recovery overrides the skill's delegation/conversion stop
only, not serial SBT, platform review, original authority, independent review
or full Phase completion. No new Phase, commit or publication is authorized.

## Frozen implementation manifest

This slice owns SplitPhaseEntryAssessment (call-scoped Port only),
SplitPhaseStartServiceOperation (current-call entry view only),
SplitPhaseManagedStartAssembly (host-derived read adapter convenience binding),
ManagedHostRecordRead (closed entry-purpose read seam), new
SplitPhaseHostEntryReadPolicy, SplitPhaseEntryRecordJsonV1,
SplitPhaseEntryDataStorePort, their new policy/codec specification, and the
bounded new scenarios in SplitPhaseStartCurrentCallSpec and
SplitPhaseManagedStartAssemblySpec. All existing touched target programs were
read completely. Preserve unrelated dirty changes. Consumer edges are Start
assessment, canonical Factory initialization and managed DataStore leases;
existing non-call-scoped fixture ports must retain their behavior.

Host entry declarations use resolved typed configuration only:
`sm-workflow.split-phase.entry-records.store-name`, `source-collection`,
`proposal-collection`. The collections must be distinct, use the same Workflow
and selection store, and alias neither selection nor instance/continuation
collections. They restrict reads, never grant physical storage or human
authority. Closed source/proposal purposes are rechecked on the actual current
call. No arbitrary admission callback, private provider, pool, file fallback
or public writer is added.

Source records are keyed by source Phase identity (the existing Port lookup),
and contain the existing source snapshot identity, Phase, authority, revision
and digest projection. Proposal candidates are keyed by existing proposal
reference and retain exact Phase/authority/source digest/proposal digest.
Use schema-tagged closed JSON in separate typed storage payloads; reject
missing/extra fields, wrong primitive types, unknown schema and invalid
existing projection values. Validate lookup identity and authority again.
Digests are read as existing domain evidence only: no digest calculation or
new hash-derived control/instance identity is introduced. Do not run a positive
Start path invoking the existing fixture's hash-derived instance/causation
mechanics in this slice.

CallScopedPort refuses context-free source/proposal access and exposes only
explicit current-call methods. Start adapts it with the exact incoming core,
just as selection is adapted, while keeping source reads after selection
admission. Host binding constructs the actual read adapters from the owning
Subsystem snapshot inside the existing post-resolution composition path.

Executable authoring gate: AnyWordSpec/Matchers/GivenWhenThen, clauses adjacent
to every new setup/action/expectation. Prove exact current-call forwarding after
test-only selection admission, no datastore reads from context-free/expired
calls, independent host declarations and disjointness, closed payload decoding,
and real source/proposal record readback inside canonical dispatch. Any record
installer is private test-only source/proposal fixture setup, never a human
selection installer or a transport Operation. Start remains denied in the real
host scenario, with no instance creation. Focused validation uses the same
SplitPhase and GoalPhase managed accumulator as 695, with fresh permission.
No source changes while SBT is live; at most three bounded pre-review mechanical
repairs in this unit. Stop on source/authority/ownership or public behavior
expansion; do not claim independent review or Phase acceptance from these tests.

## First executable verification and restart proof extension

Invocation 696 compiled the seven main changes, prepared the canonical runtime,
packaged the JAR and passed all 230 tests in 32 suites. Both exits zero and lock
released; complete summary and new scenario logs were read at
`/tmp/skill.cncf.d/a-58af4540-81b7-40c8-85f7-77e6f6296d12/79623-20261001T180439Z.summary.json`.
There was no validation failure or repair. The production factory boundary is
the public ComponentCreate.configuration accessor, not private subsystem access.

The real host scenario proved exact recorded source/proposal reads, source
assessment, missing rows, foreign lookup identity/authority, missing payload,
stale snapshot and expired call refusal. It deliberately did not create any
human selection or Workflow instance. Extend that same manifest-owned scenario
to shut down the host and reopen the exact owned temporary database with only
read adapters. The existing helper receives an explicitly selected optional
fixture path; no global config, production path or provider changes. Require
the same recorded projections and assessment without any second installer.
This is record durability evidence, not a full positive Start/restart claim.

## Final-tree evidence and remaining requirements

Fresh invocation 697 repeated the exact focused command on the extended tree.
All 230 tests in 32 suites succeeded, with zero failed, aborted, canceled,
ignored or pending cases. Both exits zero and the lock released. Full summary
and the new reopen scenario log were read at
`/tmp/skill.cncf.d/a-2db596af-9cdc-4dcc-93d4-e6386dcea622/80429-20261001T180651Z.summary.json`.
The same database reopened after shutdown; only read adapters recovered the
exact source and proposal assessment, without an installer or conversation
map. The absent selection still denied Start, and escaped reads still failed.
No sources changed while SBT was live, no repair was needed, and final
`git diff --check` passed. The complete consumed-ID ledger retains 696 and 697.

This closes the read-only entry adapter slice, not the Phase. The real input
assessment port now participates in the current managed Operation. Initial
human-selection ingress, hash-free production Start identity/idempotency,
atomic positive Start, proposal inventory/draft/validation/boundary readers,
all-profile standard factory and launcher exposure, actual semantic completion
and restart/transport/concurrency acceptance remain incomplete. File-backed
catalog and memory fixtures are not promoted into production authority.
Independent Step/full Phase review, original-base admission, acceptance
commits, full release validation and Phase release are also not claimed.

Next bind proposal assessment's actual call and its managed source inventory,
draft and validation evidence while preserving the existing deterministic
duration/partition checks. Do not choose a human-approval ingress or publish
the new source/proposal record schemas as a public writer without separately
admitted authority. No new digest algorithm or hashing control was introduced
by this slice; unchanged existing fixture/catalog algorithms are not certified
as removed merely because their accumulator tests passed.
