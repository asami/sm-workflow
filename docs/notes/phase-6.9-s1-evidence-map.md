# Phase 6.9 S1: clean RepositorySync connection and evidence

Date: 2026-10-28. Status: authored implementation and executable specification;
compile, lint, selected suite and installed qualification remain runtime-owned.
No P69 Phase condition is marked satisfied by this note.

## Reuse and actual evidence boundaries

| Delivered mechanism / evidence | S1 use | Remaining boundary |
| --- | --- | --- |
| CNCF `cncf.runtime-process-execution.v5` `gitRepositories`; local canonical producer `goldenport-cncf` `RuntimeProcessExecutionPlanCodec._git_repository` and `RuntimeProcessExecutionPlan` executable resolution | Reuse configured repository declaration, default Git on absolute PATH, managed workspace/component capabilities and typed `GitOperation` through UoW. The existing CAR assembly already declares this shape. | No demonstrated producer API gap; no producer work proposed. The source and configured template are evidence of available contracts, not installed execution of this candidate. |
| `RepositorySyncManagedFetch`, `RepositorySyncNativeIntegration`, `RepositorySyncNativeProgressStore` and `RepositorySyncNativeConvergence`; covering `ThreeWorkflowRepositoryOperationSpec` | Reuse advertised-tip observation, fetch intent/result collection, relation, fast-forward, non-force push and actual local/tracking/remote convergence. | Older fixtures prepare explicit selection/effect records and run selected product tests. They do not qualify the new abbreviated standard entry or its no-product-test behavior. |
| [6.5 delivery journal](../journal/2026/10/2026-10-24-phase-65-delivery-and-phase-7-handoff.md), Session `e89e8ba92d074262e4c8a10ef11d1183`, Plan4 | Reuse conditional assessment and meaningful input/coverage applicability concepts; the journal records eight accepted Steps and whole validation, with remaining Release closure work at that snapshot. Clean integration has no semantic merge candidate, so deterministic structural Git checks suffice. | No whole-SHA certificate, fresh AI review or repeated FULL check is introduced. Merge applicability, text access and program-impact selection remain S2/S3. This note does not upgrade the journal snapshot to final Phase acceptance. |
| [6.8 evidence map](phase-6.8-c1c2-evidence-map.md), [caller-delivery handoff](../journal/2026/10/2026-10-27-phase-6.8-caller-delivery-handoff.md), existing native intent/recovery owners | Keep retained effects and actual outcomes; never retry an unresolved push/fetch merely because its response is missing. Keep legacy native Starts on their existing policy/evidence path. | The parent reports completed Plan2 with P68-16 UNVERIFIED as prior evidence. That does not add a new S1 acceptance obligation. Broader active-Session adoption and installed caller qualification remain S5. |
| CBD repository resolved locally as `textus-cbd-support`; journal `docs/journal/2026/10/2026-10-26-phase-15-provider-acceptance.md` records validations317/328 passing1088 cases and deferred repository synchronization | Confirms Phase validation and remote convergence are distinct. [6.9 handoff](../journal/2026/10/2026-10-27-phase-6.9-repository-sync-handoff.md) records the subsequent caller-preparation/repeated-check/oversized-diff motivation. | Neither document proves final CBD convergence or equivalence of all consumed dependencies. Retained CBD task results were not executed or adopted by S1. |

## Connected standard entry

The normal `RepositorySyncService.startRepositorySync` accepts an `input` record
with `repositoryIdentity` and explicit `syncMode: FULL_STATE`. Configuration owns
the single `sm-workflow-repository` workspace and Git target. Optional supplied
tracking fields must agree with that configuration; an optional endpoint identity
must equal the configured endpoint. Missing intent, malformed fields, absent or
ambiguous target/workspace and mismatched tracking return precise failures.
The existing full business record remains usable when its fields agree.

The current authenticated principal and canonical Session generate a runtime
selection. Its origin is `Workflow`, not a fabricated Human answer. A dedicated
runtime admission checks this actual call; the legacy independent Human-selection
gate is unchanged. The existing native Start persists correlation and participant.
No caller selection import, per-effect grant row or new authorization ledger is
required. Clean Push/FinalFetch authorization projects only that retained request,
selected tracking and the same principal/Session. CNCF still admits actual Git
workspace/capabilities/arguments. CommitPending is outside this clean authority.
Changing the configured endpoint or tracking cannot silently extend the request.

Equal and fast-forward record structural index validation and observe final actual
convergence without Push/FinalFetch intents. Local-ahead reuses the existing typed
non-force Push and final fetch, with retained intent collection. A later remote
movement remains an actual convergence failure/uncertainty. There is no clean-sync
product test, AI review, merge commit or completion-record commit. Staged,
unstaged and untracked work return unfinished local preparation before the old
checkpoint path; files and staging remain intact. Purpose commits are S4.

Legacy Starts are identified by their retained correlation, keep their earlier
validation/effect contract and receive no private-store migration. New clean
selection does not supply merge/dirty acceptance or allow a pending Human
suspension to be ignored. Public active-Session adoption remains S5.

## Authored executable evidence and limits

`org.simplemodeling.textus.smworkflow.admission.Phase69CleanRepositorySyncSpec`
creates actual isolated local/bare/peer repositories, invokes normal standard
public Operations without selection/effect provisioning, and checks all three
relations, structural-only receipts, actual clean equal tips, effect intent
absence/presence and reopened terminal collection. Its process plan uses the CAR
`gitRepositories` template without per-operation Git executable declarations and
has no SBT bindings, so an accidental product-check dispatch cannot pass. Setup
commands belong solely to the controlled repository fixture. Negative normal
Operation cases cover missing intent, mismatched tracking and preservation of
untracked work without a checkpoint intent.

These are authored cases, not observed PASS results. The parent runtime owns
contract-compile, scala-lint and clean-sync-route, publication/activation and a
separate installed public entry observation. The parent reported Start/first
Advance on installed image `1d8c2491-fda5-4da6-8c89-63645062c58a`; that identifies
the owning development workflow, not installed qualification of this S1 candidate.
There is no new installed-success claim, reviewer evidence, Submit acceptance or
Phase completion here. S2-S5, public help/schema simplification and the integrated
English Phase handoff retain their selected scope.

## Minimal installed observation input (not executed here)

Use the parent's normal installed Operation transport and the selected candidate
component. With a controlled repository already configured through the existing
standard process/storage profile, invoke `RepositorySyncService.startRepositorySync`
with this ordinary input record:

```json
{"repositoryIdentity":"sm-workflow","syncMode":"FULL_STATE"}
```

Retain its actual `sessionId`, then invoke
`RepositorySyncService.advanceRepositorySync` with that `sessionId` alone. Check
the returned native convergence and the actual controlled local/tracking/bare
remote tips. Run distinct Equal, local-ahead and remote-ahead repositories;
no per-effect evidence import or repository selection row is part of this route.
Optional tracking fields and endpoint identity must match the configured target.
Use no SBT bindings for this observation to make an accidental product check an
observable failure. No new sm-workflow operation, process program, executable
registration or ABI generation is required for this runtime input normalization.
The existing public full-record schema/help still describes tracking fields;
its simplification and packaged skill alignment remain S5, not a claim made by
this authored source connection. Parent runtime publication and installation
selection are required before attributing any result to the candidate.
