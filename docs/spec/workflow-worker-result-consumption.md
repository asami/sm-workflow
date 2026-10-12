# Worker result conversion and consuming completion

## Current Phase5.3 completion boundary

The normal fifteen-Operation interface is the
[common business loop](common-workflow-operation-loop.md). Named results/answers
become native completion data using actual issued state, not a caller-provided
control envelope. Existing public CNCF completion owns selection/commit. After
commit, the actual business result produces structured sessionId, accepted,
outcome and message; no eager Advance, new model call or unchanged-copy read is
performed for acknowledgement. Direct consumers use generated ValueReader on
RecordResponse rather than decode native Wait JSON.

The dated R38 sections below retain historical evidence and limitations. They
are not new Phase5.3 shared-infrastructure/replay/compatibility requirements.
Current generation or compilation is not behavioral or Phase acceptance.

## Historical R38 evidence and rules

Status: bounded R38 conversion/consumption scenarios verified on 2026-10-09.
The revised source-access correction was accepted by normal platform review;
CNCF92 ran193/193. Local E1-E4 passed individually in95, whose aggregate remains
FAILED111/106/5. Connected existing-spec corrections then passed54/54 in96;
bounded independent source closures passed. Do not synthesize a combined
same-run aggregate or Phase acceptance. Original refusals and failed91/94/95
remain history. Wider adopted Phase5 gates and real executor/cost work stay OPEN.

## R1 — Select a worker using the requirements actually consumed

Capability/risk/review compatibility chooses a usable profile. Repetition of a
required capability adds no new capability. Selection does not prove execution,
freeze result diagnostics or grant progression permission. Whole projection
encode/decode equality is not a worker compatibility condition.

The changed WorkflowWorkerMappingSpec cases exercise compatibility, harmless
repetition and selection independent of unused projection/codec presentation.
The policy-table correction is bounded-verified by Actual97:27/27 in four whole
connected suites and independent read-only SOURCE_PASS. Keep the existing
ordered first-reasoning-match lookup, without a whole-table validator. Version,
unused rows, repeated rows and profile/model diagnostic names do not determine
compatibility. The selected row must actually supply the required capabilities,
risk and review support; an unusable first match does not fall through to a
later profile. Missing table/requirement/profile data fails when lookup consumes
it. No execution, completion permission or policy certificate is produced.
The whole-suite examples cover unused/duplicate rows and diagnostics versus
unavailable selections and a selected incompatible row. This test-only mapping
bridge remains separate from the CNCF execution resolver and real AI execution.

## R2 — Normalize data without completion admission

Returned profile/model/worker/policy diagnostics are preserved, not compared with
the selected policy. Native target/type/condition values are transported to the
consumer, not rejected by translation. Missing inputs needed to access/convert
data produce a Consequence failure; no new validated mark is returned.

WorkflowWorkerMappingSpec's corrected conversion cases exercise diagnostics,
stale native values and policy replacement. E1/E2 in
WorkflowWorkerResultConsumptionSpec connect normalizer conversion to the real
GoalPhase submitStepReview Operation through an existing in-memory host fixture.
No CLI command is used. These no-disposition review cases are legacy-compatible
transport coverage, not actual executor/provenance or managed storage coverage.

## R3 — Result conversion and storage reads must not restore irrelevant gates

Duplicate or unused extra references, blank diagnostics, copied diagnostic
requirements and harmless extra annotations must survive representational result
encoding/decoding. E1 sends each variation and a combined variation through the
actual completion Operation, observing one completed Step/commit in the fixture.
Unknown annotation acceptance does not let JSON select an Operation or Workflow.
Required typed conversion failures remain failures; semantic type applicability
belongs to the consuming declared Operation, not a copy-comparison codec gate.

Reopened V1/V2 managed storage, real event preparation/delivery, malformed codec
failure cases and the full producer/consumer read path remain required R38 work.
These in-memory examples do not substitute for those remaining scenarios.

E4 reads V1 and V2 passive frames through the public completion-record codec,
preserving blank worker/model diagnostics and annotations at result/evidence/
semantic/outer layers. Unsupported schema, malformed JSON and missing result
representation fail through Consequence; sample fixture work is not completed.
E4 passed in Actual95 with bounded source review, not full independent acceptance. It does
not reopen a provider or prove managed native type/status/scope/CAS admission.
The accepted correction removes passive V1/V2 exact-field-set gates. Closing
value checks and application retained-body gates remain subject to the broader
Phase5 assessment. Applying source alone does not satisfy this case.

## R4 — Prevent actual harm at its consuming boundary

E2 transports wrong run/continuation/instance/revision/model/type and semantic
review candidate/evidence inputs; the actual Operation refuses them without a
history append/commit or consuming the assigned claim. The correct result still
completes afterward. E3 calls the public native loose completion entry using
actual runtime persistence and a claimed source. Native required facts/evidence
must be checked before reduction loses the vectors. Actual issued disposition
relations must prevent inline/parent work being accepted as independent work.
Empty executor identity must not pass merely because it differs from origin.
The unused profile diagnostic does not establish an executor ran.

E3 observes no fresh UoW on rejection, one actual UoW completion and replay refusal.
Its executor observation is fixture-owned matching data, not a real AI execution
receipt. Managed Available/claimed joint CAS, owner/collection permissions,
events and durable reopen need their owning existing native storage specs.
No common verifier, comparison ledger or new repair frame is introduced.

## R5 — Render completion acknowledgement from the actual commit

GoalPhase ordered work and RepositorySync conflict/review completion receive the
committed InstanceRecord from managed completion in the same owning call. Render
the nonterminal acknowledgement from that record and the submitted semantic
result. Do not retain an application copy of the selected result/next instance,
compare it after commit, revalidate the completed record, or reload the stored
result merely to certify unchanged content. These response checks cannot prevent
an already committed effect and can turn a successful completion into a false
failure. No new response verifier or completion mark replaces them.

The response needs a committed record to obtain its actual handle/frontier; an
absent record is an indeterminate acknowledgement, not a successful completion
or permission to retry. Actual issued-target/type/semantic result consumption,
native transition selection, grants and atomic update conflict handling remain
at their effect boundaries, separately subject to the necessity-first audit.
Retained recovery reads are not this same-call acknowledgement path.

The existing whole GoalPhaseNativeRunSpec and RepositorySyncManagedFetchSpec
exercise actual SQLite completion/reopen, rejected crossed or unenrolled work,
nonterminal result responses and subsequent managed effects. These are current
validation targets. Fresh Actual98 ran those suites and
ThreeWorkflowRepositoryRecoveryOperationSpec,86/86,SBT0wrapper0lockreleased;
independent read-only SOURCE_PASS covered all1159primary lines. This establishes
only bounded acknowledgement correction/regression evidence, not real AI
execution or full Phase acceptance.
