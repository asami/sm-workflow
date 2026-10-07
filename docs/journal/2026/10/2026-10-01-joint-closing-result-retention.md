# Joint retention of submitted and actual closing results

Status: internal result/native binding verified; production Phase 2 acceptance
remains open. Previous goal turn progressed with closing-program preparation.
This turn closes the specific memory-only actual Action-result gap identified
there, without substituting this internal boundary for the full Phase goal.

## Contract and implementation

The submitted semantic `ContinuationResult[R]` and the actual closing
`StateMachineOperationResult` are distinct. Neither overwrites nor impersonates
the other. `WorkflowCompletionRecordJsonV2` stores a strict outer V2 envelope
containing the exact existing V1 semantic completion and the actual closing
result's type/context identity/revision. Records without a closing result retain
the original V1 bytes. The common loader supports both versions. Unknown,
extra/missing fields, malformed closing values and recursive V2 semantic
envelopes reject rather than dropping the closing result.

The retained Scala Record gains a defaulted optional closingResult. Existing
two-argument construction still compiles; binary compatibility is not claimed.
This is no sender option or authority grant. The managed internal entry obtains
the actual result only from immutable `WorkflowClosingProgramPreparation`.

`prepareClosingHostParticipantC` belongs to the typed Completion persistence
adapter. It derives both the retained closing result and fresh local intent
projections from that same preparation and prepares the existing guarded pair.
The previous generic `hostParticipantFromClosingC`, which consumed only local
intents and discarded the actual result, was removed. No caller success flag,
replacement Action-result parameter or separate postcommit result write exists
on this entry.

Both results are encoded in the existing completionPayload on the claimed
Continuation, committed together with Workflow history/claim/local effects and
selected durable event facts. Existing exact authoritative readback comparison
now covers the V2 bytes. Native uncertainty still cannot admit a Completion or
delivery. Standard declarations, EventStore selection, central UoW commit state
and public Operations are unchanged.

## Executable evidence

- All five ordinary event modes (empty/transient/retained/new/mixed) commit the
  actual prepared closing program, local guard/Create post-images and exact
  semantic/closing results in the same native transaction before test delivery.
- Strict codec round-trip preserves both results; V1 output is unchanged for
  the corresponding no-closing record. Malformed/extra/missing/null/recursive
  V2 cases reject.
- Managed SQLite shuts down, a newly constructed provider reopens the same
  database, and a new Completion adapter recovers both exact results. The
  actual program was interpreted once, not re-executed during recovery.
- Release/reclaim after joint preparation leaves current storage untouched,
  retains no completion result, and suppresses delivery. Conservative native
  failure classification is preserved rather than asserted as proven abort.

Focused-389 compiled the source and passed 323/323 tests in 34 suites.
Focused-390 compiled the added shutdown/reopen and stale-claim specs and passed
**325/325 tests in 34 suites**, no failed, aborted, canceled, ignored or pending.
Both SBT/wrapper exits were 0/0 with lock=released; summaries and log outputs
were inspected. The unchanged focused-386 suite argv, mandatory serial wrapper
and ordinary build caches were used. `git diff --check` passed in both roots.
No repository-full validation is claimed.

389 evidence: `/tmp/skill.cncf.d/cncf-sbt-bac4d63b6a2ebdd7c640321d8f0d231009146b7d13594e2cb91a301b8ca69416-30fac47b735a90bc8f27605e26f9c73e/86717-20260930T182207Z.log`
and adjacent `86717-20260930T182207Z.summary.json`.

390 evidence: `/tmp/skill.cncf.d/cncf-sbt-5114a5a1e94f87d6d9716417d1c6bcd8624c66ad058965d03f9fd331d7dd2448-ab0f7522bd5c6575b5741c3718a4ae57/87761-20260930T182454Z.log`
and adjacent `87761-20260930T182454Z.summary.json`.

Final source SHA-256:

- `WorkflowCompletionRecordJsonV1.scala`: `d2622bc8beef36e0dfa730808781c8a261a32151d488999881b34038f6da2286`.
- `WorkflowCompletionRecordJsonV2.scala`: `a7b8b865f332d7b7821d84e138db942c1a63fad5b0fed56331b72fc0f490e0e1`.
- `WorkflowCompletionDataStorePersistence.scala`: `16555a2b005290c894506c0f9ea11527a666a6f078cf389be3400e29934d2c7c`.
- `WorkflowPreparedDataStoreTransition.scala`: `07efa5f6fe3328e786a6cd755d92291ceb1bc7f955ef5dbdd7fb3524d9caf523`.
- `WorkflowCompletionDataStorePersistenceSpec.scala`: `a51af8142d8b93c532e48bec3d75d27a5c0045306b6702240a56920e457c36cb`.

## Remaining boundary

The closing program is still authored by trusted test code. Admitted CML/
StateMachine Action selection, expected closing-result contract validation,
Operation/security admission, production local declarations and actual active
UoW enlistment/uncertainty handling remain open. The stored ContextReference is
not proof of referenced context payload retention or access authorization.
Delivery is still a test port, not the production EventEngine bus or finalized
event projection. No transient replay/recovery acceptance is inferred.

S12/P2-U05/P2-U15/P2-U16, application runtime/assembly, launcher/CLI/MCP,
management and distribution acceptance remain unchecked. No acceptance commit,
release, push, publication, sandbox relaxation or new human approval occurred.
