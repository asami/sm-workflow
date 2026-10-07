# SplitPhase AI-completion checkpoint: bounded parent repair plan

Date: 2026-10-03 (Asia/Tokyo)
Scope: same original sm-workflow Phase 2, Goal createdAt 1790766223
Status: parent repair validated; independent read-only closure diagnostic PASS

## Source, ownership and finding admission

Use the actual SEALED_DIAGNOSTIC result from
`/root/completion_checkpoint_readonly_diagnostic`, retained in the
[independent diagnostic record](2026-10-03-split-phase-ai-checkpoint-independent-diagnostic.md).
Admit only CB-AICC-001 and CB-AICC-002. The current human direction assigns
implementation to the parent, tests to runners, and permits independent
read-only agents. It overrides the ordinary delegated editing preference, not
validation, review, security, scope or release assurance.

cncf-review-fix supplies the bounded finding and M0/M1/M2 classification rules.
The total batch is M2: complete current-call purpose provenance crosses several
private modules and requires a behavioral native-call regression and independent
read-only closure inspection. M2 does not require another developer approval.
No HYG-AICC-001/002/003 or Development Candidate cleanup joins the batch.

The same current technical frontier has used zero of its three byte-changing
repair attempts. Reserve its first bounded attempt for implementation; do not
consume it in this source-inspection/design turn. Preserve all earlier exhausted
and separately directed budgets. These technical counts are not reconstructed
ordinary Step/Phase review-repair counts or a new accepted PLAN epoch.

## New source evidence determining the correction

SplitPhaseTypedStartReadPolicy, SplitPhasePartitionContextReadPolicy,
SplitPhaseHostSelectionReadPolicy and SplitPhasePlanningInputReadPolicy expose
existing validated Declaration types and decodeC functions. Their declarations
contain only store/collection metadata. Input decoding establishes the complete
finite Purpose set; compare that entire set, not only seven currently exposed
counterexamples or raw configuration strings.

SplitPhasePartitionEnrichmentStorage.bindC, already called by U.bindTargetsC,
decodes all four declarations and requires fifteen distinct same-store targets.
Thus retaining their metadata does not add a previously absent configuration
requirement at completion or checkpoint binding. No new read-policy accessor,
upstream API, storage grant or schema is necessary. Reuse existing decoders,
rather than inventing a second parser or partial validation policy.

The AI-origin static observeForCallC is consumed only by the completion Binding,
its current-call committed readback, and the managed checkpoint Port's explicit
AI-origin advance query. All consumers are in already owned program paths.
Preserve the unrelated mechanical path; do not bundle a separate mechanical
reader redesign into this finding.

## Exact implementation boundary

All ten paths are under src/{main,test}/scala/org/simplemodeling/textus/smworkflow/admission.
They are a subset of the retained twelve-path frontier; no repository/path
expansion is selected.

Main programs:

1. SplitPhasePartitionCompletionCheckpointDataStorePort.scala:
   introduce an immutable privately constructed ReadPurposes value composed of
   the four existing typed declarations; capture it through their existing
   decoders. Structural comparison covers every declaration store name,
   Start/context/selection collection, and all Purpose values' collections.
   Expose only the metadata and comparison needed by owned callers. Retain no
   ResolvedConfiguration, pool, provider, lease, action Core or executable program.
   The freshly bound port retains its captured ReadPurposes alongside existing
   reader metadata. Rename requiredC to _required_c and its local references.
2. SplitPhaseManagedTypedPartitionCompletion.scala:
   capture original ReadPurposes once at bindHostC and carry it through Binding,
   operationC, current-call wrapped operation, closing staging and committed
   readback. observeForCallC takes the bound purpose metadata explicitly;
   currentReadsC compares the fresh port's purposes with it together with the
   existing publication/effective/native targets. Never compare freshly decoded
   metadata with another value derived from the same current call as the sole
   expected basis. Do not reconstruct a Plan or alter native transaction policy.
3. SplitPhaseManagedPlanningCheckpoint.scala:
   retain original ReadPurposes at Port binding and pass that original selection
   to its AI-origin observation. Keep the existing mechanical reader path and
   its behavior unchanged; no fallback from one origin to another.
4. SplitPhasePartitionCompletionCheckpointPreparation.scala:
   rename private invalid/guarded and their references to _invalid/_guarded.
5. SplitPhasePartitionCompletionCheckpointJsonV1.scala:
   rename private envelope/invalid/guarded and references to
   _envelope/_invalid/_guarded. Preserve all persisted keys, versions and frames.
6. SplitPhasePartitionCompletionCheckpointObservation.scala:
   rename private invalid and references to _invalid; behavior unchanged.

Executable specs:

7. SplitPhaseManagedTypedStartOperationSpec.scala:
   add semantic Given/When/Then counterexamples at the existing actual completed
   native-call seam, covering Start/context/selection/source/inventory/boundary/
   partition-policy declarations. For each alternate metadata selection, keep
   the publication/effective/native targets unchanged and current original data
   valid; compare a binding selected from that alternate host with the actual
   current owning Core. Both direct AI observation and advance's managed Port
   query must fail; the original binding must still return the same raw result,
   current history and wire without writes or human verifier calls. Reuse the
   existing probe and physical store; do not add another pool or public test
   ingress. Run these bounded comparisons in the initial service case rather
   than duplicating the whole lengthy lifecycle fixture for every purpose.
   Existing physical reopen, candidate/reply naming-policy removal/change,
   DecisionNeeded, rollback, malformed data and expired-call scenarios remain.
8. SplitPhasePartitionCompletionCheckpointPreparationSpec.scala:
   rename private fixture prefix vals ip/cp/sp/tp/ep to _ip/_cp/_sp/_tp/_ep;
   rename only the four camelCase capacity/payload locals to flatcase. Keep
   fixture public labels and behavior intact.
9. SplitPhasePartitionCompletionCheckpointJsonV1Spec.scala:
   rename local helper frame and references to _frame_; preserve damaged frames
   and expectations exactly.
10. SplitPhasePartitionCompletionCheckpointObservationSpec.scala:
    rename private original/checkpoint helpers and references to
    _original/_checkpoint; preserve comparisons and assertions.

New members and locals follow the existing CNCF naming rule. Serialized fields,
public/protected Operations and command schemas are not renamed. Existing
unproven naming debt, obsolete helper removal, scenario extraction and header
policy adoption remain outside this repair.

## Preserved behavior and stops

Preserve actual original issued/result/evidence/before/next data; mixed Built
documents and DecisionNeeded lineage; unique combined Creates in the original
closing UOW; atomic rollback; whole independent facts/history comparison; the
single Subsystem pool and current native permissions. Naming allocation and
reply policies are not storage-purpose metadata and remain irrelevant to
passive recovery after removal/change. No new locks, claims, retries, management
hashes, latest selection, process invocation or persistence format is admitted.

Stop before a required new upstream/public API, persisted schema or additional
source path, a material ambiguous choice or exhausted/stalled repair. An exact
in-scope compile/spec correction consumes the same bounded technical budget,
not a new identity resetting the earlier histories. Parent owns implementation;
do not spawn a fix worker under the superseded editing preference.

## Verification and next operation

After the one parent implementation pass, inspect its exact delta relative to
the retained pre-diagnostic PathState evidence, preserving all other dirty work.
Use bounded naming/reference checks and git diff --check. No source edit may
overlap a live SBT process.

Representative validation is the same four-suite testOnly selection used by 824:
SplitPhasePartitionCompletionCheckpointPreparationSpec,
SplitPhasePartitionCompletionCheckpointJsonV1Spec,
SplitPhasePartitionCompletionCheckpointObservationSpec,
SplitPhaseManagedTypedStartOperationSpec.
Then use the same ten-suite affected accumulator as 825 on the unchanged repair
tree, including SplitPhaseManagedPlanningCheckpointSpec. Each top-level SBT
command runs through the registered cncf_command_runner, the current receipt-
first command contract and run-sbt-serial.sh, never directly or concurrently.
Allocate fresh command IDs only at execution. Do not rerun standalone compile,
generation, package, publication or full Phase tests here.

Require actual successful terminal results and released locks before a fresh
independent read-only closure diagnostic of both finding IDs, the complete repair
delta and relevant consumers. This bounded M2 closure check cannot substitute for
ordinary typed reassessment, Step acceptance or Phase full review/validation.
CAR lint/runtime routes remain unverified; this repair is not a release gate.

At plan creation the next technical operation was parent IMPLEMENT of this
exact ten-path plan, not another general admission audit or a request for
identical authorization. The following sections record its actual progress.
No runtime source, executable spec, SBT command, review cycle, Git index or
commit changed in this planning turn. The original Goal remains active with
all original Phase deliverables and formal recovery/acceptance still outstanding.

## Parent implementation completed on the following Goal turn

The preceding turn was an unexecuted plan, not implementation progress. The
following turn performed the frozen parent-owned ten-path repair without an
implementation/fix agent. All retained agents were terminal before source edits.

ReadPurposes is privately constructed from the existing four validated typed
declarations. It holds only inert declaration data. sameAs compares each
store/collection and every finite planning-input Purpose; it compares complete
structural data, not declaration object identity or a management hash.
Completion Binding captures its original metadata at host binding and carries
it through the current-call wrapper, closing staging, committed readback and
direct recovery. The managed checkpoint Port likewise carries its original
metadata into the AI-origin observation. The natural currentReadsC boundary
compares the fresh port with that retained expectation. No mechanical-path
redesign, provider/pool retention, serialized frame change or public Operation
change was made.

The native spec now exercises seven alternate original declarations on both
direct completion observation and actual owning advance. Its Given clauses
construct valid earlier metadata and prove unchanged publication/effective/
native targets; When invokes the read on the actual current Core; Then requires
refusal and verifies that the correctly bound read still returns identical
record, current history and wire without human verification. These are bounded
subcases in the existing initial-service lifecycle example, not seven newly
counted ScalaTest examples. Existing physical reopen and naming-policy
removal/change cases remain unchanged.

The seven new-file naming repairs changed only internal identifiers and their
references. String literals, serialized fields, existing public labels, override
names, assertion semantics and unproven old naming debt were preserved.

The parent captured the complete pre-diagnostic 943-path file/index boundary
again and rendered the exact repair delta at
`/private/tmp/smwf-reassessment.rMVMG4/bounded-repair-delta.patch`;
post-repair exact states are retained in
`/private/tmp/smwf-reassessment.rMVMG4/bounded-repair-exact-state.json`.
Only the ten frozen source/spec files differ; all modes and index entries are
unchanged, and the other 933 captured paths are unchanged. This comparison does
not exclude or reset the additional diagnostic/design journal records created
after the original capture.

The full rendered delta was inspected. Bounded reference/naming searches found
no remaining admitted private/local identifier violations; the public codec
fields storageField and maximumPayloadBytes remain intentionally camelCase.
git diff --check returned 0. Because these program files are untracked, each of
the ten files was additionally checked with git diff --no-index --check against
/dev/null: each returned the expected difference exit 1 with no whitespace
diagnostics. That check supplies no compilation or behavior proof.

The first byte-changing technical repair pass is now consumed: current frontier
1/3. Preserve every older budget, all 494 consumed SBT identities through 825 and
the original Goal. No new test command, permission, review-repair count, Git
staging/commit or acceptance record was allocated. Results 824 and 825 describe
the pre-repair tree only; they are not validation of the new tree.

Next is runner validation of the unchanged repair tree: the frozen four-suite
representative selection, then its ten-suite affected accumulator. Use fresh
typed receipt-first requests and the registered serialized runner. After actual
successful terminal results, perform a fresh read-only closure diagnostic. Do
not repeat planning, ask for the same authorization, restore an old source tree,
or claim ordinary reassessment/Step/Phase acceptance from this implementation.

## Actual validation and independent closure

Fresh registered repaired-tree attempts 826 and 827 passed 11/11 representative
and 49/49 affected examples, with relevant compilation, SBT/wrapper exit0 and
serial lock released. Exact 943-path repaired state remained unchanged before,
between and after these runs. The command ledger is now 496 through 827;
technical byte-changing repairs remain 1/3, with all earlier budgets preserved.
See [validation](2026-10-03-split-phase-ai-checkpoint-repair-validation.md).

The fresh read-only agent /root/completion_checkpoint_repair_readonly_closure
returned SEALED_DIAGNOSTIC PASS: CB-AICC-001 and CB-AICC-002 CLOSED, no introduced
regression, HYG-AICC-001/002/003 preserved. The parent repeated the exact state
check after its final source: 943/943 unchanged. See
[independent closure](2026-10-03-split-phase-ai-checkpoint-repair-closure.md).
This closes the bounded technical repair diagnostic only, not ordinary PLAN,
Step/Phase acceptance, full validation or release. The same Goal remains active.
