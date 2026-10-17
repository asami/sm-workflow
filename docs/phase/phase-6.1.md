# Phase 6.1: Runtime Commands, Live Phase Plans and Step Admission

status=in-progress
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-6.3
aggregate_validation_sequence=["PHASE-6","PHASE-6.1","PHASE-6.2","PHASE-6.3"]
depends_on=phase-6.md
Planned: 2026-10-13. Split from Phase 6.
Owner: sm-workflow. Predecessor: [6](phase-6.md). Successor: [6.2](phase-6.2.md).

## Current delivery — 2026-10-17

Current status: P61-PLAN accepted; P61-COMMAND accepted through independent Step review and focused repair closure, local acceptance commit pending; P61-ADMISSION not started.
Update rule: update from actual validation and the owning Step/Phase acceptance; retain historical allocations and unfinished work.

The existing Phase 6.1 Goal, original base, PLAN epoch and repair history remain
active. Deliver the following connected Steps in order:

1. **P61-PLAN — accepted:** P6-PS01..04, supported Phase/checklist import,
   live dispatch refresh, retained issued context and current-document progress
   writeback. Complete protected Step review and mechanical conformance closure
   are accepted; its local acceptance commit is `8c48c329`.
2. **P61-COMMAND — accepted:** P6-EX01..03 and P6-HR01/04, actual commands,
   semantic correction/revalidation and configured deterministic maintenance.
   Source-history handling follows the `none`/`goldenport` selection below;
   rules belong to the configured external provider. Its complete protected
   independent Step review and focused repair closure are accepted; the local
   acceptance commit remains pending.
3. **P61-ADMISSION — not started:** P6-SQ01, real scoped producer/provider
   review, typed Evidence/Admission and local Step commit.

Actual COMMAND qualification covers A/A2 focused consumer/codec checks (2 suites,
17 tests), normal owning-UoW Compile/configured SBT source generation and real
compiler failure (1 suite, 2 tests). Existing normal ProductCheck supplies a
representative terminating application acceptance command through the same typed
SBT path; it starts no service, so service readiness/cleanup is not applicable.
B4 preparation plus the existing accumulator passed 3 suites/4 tests; genuine
selected medium semantic repair then passed the normal consumer (1 suite/1 test):
runtime-owned failed Test -> semantic repair -> fresh Test -> pending independent
Review. B5 preparation passed 2 suites/2 tests; the genuine selected medium
conflict provider supplied a proposal only, then the normal consumer passed
1 suite/1 test through runtime materialization, typed Git stage, structural
checks and fresh Test to pending independent merge Review. Requested provider
configuration and parent-observed completion are distinct evidence, without
runtime/model attestation; semantic workers ran no SBT/Git/process commands.
Local fixture Git setup grants no real remote permission or commit/push/convergence
acceptance.

C1/C2 pure rules/current configuration/File UoW driver passed 3 suites/54 tests.
C3 normal CLI passed 2 suites/6 tests (five history scenarios and the existing
repair test), with production/test compilation and both exits 0/lock released.
C4 ordinary preparation passed 1 suite/1 test; genuine selected medium ImplementStep
changed only `HistoryExample.value` to 42 without manual history. Normal
recordExecution/Submit/Advance applied the configured header through File UoW,
then typed stage/Test reached pending independent Review. The normal consumer
passed 1 suite/1 test, both exits 0/lock released; repeated Advance added no SBT.
Parent inspection observed the generated header and preserved semantic value.
This route's independent review, CAM Evidence/Admission and local commit are
unfinished P61-ADMISSION work. These focused results establish neither full
external-tool coverage nor installed-host acceptance. P6-EX01..03/P6-HR01/04
are accepted through the complete COMMAND Step review and focused closure;
P6-SQ01 remains incomplete until the real producer/provider Admission route closes.

The COMMAND review's two blockers are resolved. The source-history interpreter
preserves nested interpolation literals, including backslashes before escaped
or actual interpolated dollars, while changing only genuine history comments.
The final focused checks passed 4 suites/91 tests and then the affected rule
suite's 39 tests; independent focused review accepted the repairs. The two
nonblocking historical Hygiene findings retain their recorded dispositions.
COMMAND repair history is 5; Phase repair history remains 0.

The sole Phase 6.1 comprehensive review and final local release remain open.
Repository-full validation is deferred-not-run to PHASE-6.3. Phase 6 acceptance
and its repair history remain closed and preserved; no Phase 6.2 or Phase 7
execution follows from this record. Active execution evidence stays in ignored
local workflow storage while this Phase is active.

## Goal and connected implementation order

Extend the accepted actual skill/client driver through runtime-owned command
execution, live plan synchronization and real scoped Step quality Admission/local
commit. Consume Phase 6's frozen mode/context/result/session/provider/Boundary
contract and applicable evidence; do not introduce another client/workflow engine.

### NICT delivery-friction observations — adopted 2026-10-17

This is clarification of existing requirements from the
[NICT incident handoff](../journal/2026/10/2026-10-17-nict-skill-failures-phase-6-family-handoff.md),
not a new item ID, acceptance gate or retrospective reopening. Preserve current
issued work, in-flight results and applicable validation/review evidence. The
owning development chat adopts it at the next ordinary planning/dispatch boundary;
this document edit does not mutate live plans, requests, counters or Goal state.

Under existing P6-EX01..03 and deterministic history/tool handling P6-HR01/04,
runtime owns selected command invocation and result capture. A saved intent is
not proof that the external command started. Report known non-start, actual
product/check failure, unknown external effect and actual platform denial
according to observed facts. A pre-command failure may leave intent saved;
neither "command ran" nor "nothing changed" follows from it. A source-edit
approval rejection is not an SBT execution-permission denial.

The NICT lost-verifier-result incident motivates deterministic result handoff,
not AI storage-key transcription. Keep one normal route; incident-specific skill
recovery clauses must not become an execution whitelist. If this requires changing
result types/propagation, the owner bounds and reviews that implementation in the
existing command work before dispatch; this note does not change those types.
Reuse existing evidence and focused boundary observations. No generic recovery,
blind retry, new ledger or exhaustive interruption matrix is requested.

### Command execution ownership — 2026-10-12

Runtime selects CNCF typed Git/SBT operations and prepares admitted inputs.
CNCF adapters own cwd, argv and environment; other tools require command-name
opt-in and scoped registered definitions. Runtime starts supported commands, waits/readies services,
collects actual exit/diagnostics, owns cleanup and selects the next transition.
AI owns semantic edits/diagnosis/conflict resolution; it does not run command
work orders, choose each next command or transcribe logs. Missing tools and unknown
completion remain actual problems, never success or permission for blind effect retry.
Long-lived processes retain owned readiness/shutdown; unrelated processes are safe.
Reuse existing tools/configuration/execution/effect-authority boundaries; no new
shell/compiler/Git framework or product machine-wide mutex. Development SBT obeys
the separate external harness serialization even when product policy differs.

Consume the Phase 6 P6-SBX01..05 foundation: plan and conflict-file access use
the protected CNCF File Workspace DSL; ordinary resource reads use normal
`read_resource` / `read_resource_text` with runtime limits, never a safety-variant
API, ProcessExec or a private main wrapper. Reuse Phase 6's logical workspace
binding and admitted root authority. Routine
startup uses the prepared launcher; SBT runs only required build/test tasks.
Runtime/JAR preparation is attached only to tests needing those artifacts.
The extended P6-EX/PS lifecycle remains owned here without repeating the producer
policy suite or the already accepted Phase 6 foundation.

### Phase documents and live plan synchronization — 2026-10-12

Start encodes supported fields from the adopted Phase/checklist documents under
stable item IDs. Parse declared fields deterministically; bounded unresolved
meaning follows semantic planning/developer adoption, not executable arbitrary prose.
Refresh relevant plans before next dispatch, including after Wait/results, retain
execution facts and issued-work context, and resolve actual acceptance contradictions
through the existing decision route. Missing/duplicate IDs or invalid dependencies
are ordinary input errors. Do not discard an in-flight result or silently cancel work.

At write time read the current target and update only owned progress/result fields,
preserving human/AI plan/narrative/order edits. Report unresolved overlapping edits;
never write an old whole serialization over the document. A checkbox is not test/
review/release evidence. Preserve Goal/base/epoch/history/counts and applicable
results; changed requirements affect their scope, not an automatic complete rerun.
AI handles narrative/journal/replanning; deterministic structured progress remains
runtime work. No snapshots/hashes/TTL/integrity ledger or abandoned-run recovery.

### Staged quality review across projects — 2026-10-13

Own P6-SQ01: real Cozy CML metadata -> scoped lint/independent semantic review ->
typed Evidence/Admission -> actual local Step commit through the existing runtime.
`STEP` is sm-workflow lifecycle meaning, not generic CNCF ABI. Reuse existing
CAM and request only needed owner capabilities: CNCF106 REA, Cozy81 RMS,
cbd-support15 SQR. Their bounded owner handoffs close independently of the whole
consumer family; do not wait for unrelated roadmaps or invent circular closure.
Use the shared adopted integration scope for needed producer work. This is an
actual producer/provider connection, not mocked output or a complete-provider claim.
Phase 6.3 owns P6-SQ02..05 and the PHASE/FINAL connected route.

### Security evidence at Step admission — adopted 2026-10-14

Apply the [security review profile](phase-6-security-review-profile.md) as
additional content of P6-SQ01. Request a focused check only when an explicitly
adopted security contract is affected by this change and applicable existing
evidence is insufficient. Public exposure, DataStore access, rendering, input
binding and explicit escapes are possible affected areas, not automatic test
triggers. Step completion itself requires no additional security scan. Consume
actual CAR lint/CBD Review results through the existing typed Evidence/Admission
route; sm-workflow does not own security rule semantics.

Reuse CNCF/Cozy guarantees for ordinary use meeting the delivered contract's
conditions; do not rerun SQL parameter-binding, generated escaping or other
producer security tests per application/Step. Check only an uncovered changed
application boundary. Unaffected and already-covered scenarios must continue
without extra security execution or a provider wait. Establish this behavior
within the existing connection acceptance, not a new recurring verification gate.

CNCF107/Cozy83/CBD16 are bounded producer/provider handoffs for this content,
in addition to the staged-review owners above. A locally imported plan is not
an available provider or passing evidence. Missing required coverage remains
an explicit gap for this connection while independent work can proceed.
Use the existing Step review and commit sequence, retaining applicable evidence;
do not add another mandatory security review cycle or expand full-suite ownership.

### Review and maintenance responsibilities — 2026-10-12

Own deterministic source-history maintenance P6-HR01/04: apply the configured
changed-path rule before applicable validation/review/commit, preserve required
origin/authorship and return real tool/configuration errors. No AI date/header
chore or independent review of deterministic history edits. Phase 6.2 owns
meaning-dependent GWT/names/comments and adopted semantic correction P6-HR02/03.

## Closure and successor handoff

Own P6-EX01..03, P6-PS01..04, P6-SQ01 and P6-HR01/04 in the
[single checklist](managed-test-suites-phase-checklist.md#phase-6-family--aiskill-connection).
Prove real build/test and local Git, failure -> AI correction -> runtime validation,
Git conflict/resolution, a representative configured tool/acceptance command and
owned service lifecycle where needed. Prove a between-step plan edit affects
dispatch/writeback without losing previous facts, and actual Step review/Admission/
commit. Use the same driver and bounded branch specs, not a tool/provider matrix.
Hand 6.2 accepted command/plan/writeback/maintenance/Step-Evidence interfaces,
owner handoff receipts, installation configuration and applicable actual results.
The full [pre-split specification](../notes/phase-6-pre-split-plan-20261013.md)
is historical detail; current requirement ownership is [the split map](phase-6.md#exhaustive-unfinished-requirement-ownership).

## Shared scope and adopted integration direction

sm-workflow owns this Phase under the shared bounded integration scope. Consume the accepted Phase
5.3 [business Operation contract](phase-5.3-operation-contract-handoff.md): one
session per execution, named inputs/results/answers and explicit Advance.
Keep actual effect permissions and the existing configuration/resource owners.
Do not restore full-envelope echoes, target discovery, hashes, TTL, copied-state
proofs, compatibility routes or a second workflow/configuration engine.

The earlier 2026-10-13 CNCF/Cozy wait-only instruction is historical, superseded
for selected existing main checkouts by the [Phase 6 adopted integration direction](phase-6.md#shared-scope-and-adopted-integration-direction).
The 2026-10-15 correction additionally selects the bounded CNCF110 producer and
CBD16 laundering rule slice. Consume Phase 6's accepted typed Git/SBT, resource
and process/HTTP opt-in contracts and evidence; do not rebuild P6-SBX01..05 here.
Preserve this child's existing delivery IDs and add only affected missing checks.
No whole upstream Phase activation/closure or unrelated repository work follows
from this dependency. Missing generic behavior belongs to its producer, never
a sm-workflow bypass. Reuse standing adopted authority rather than asking for it
again; new out-of-scope work still follows the existing scope-decision route.

The ordinary-use scope is unchanged: distinct executions have distinct sessions;
after abandonment start a NEW execution. No same-execution winner, identical
replay, exhaustive history or interrupted-effect recovery is promised. Actual
file scope/non-overwrite and truthful execution results remain required.

## Validation and closure obligations

Implement connected Executable Specifications with the owned behavior. Each
member retains proportional focused compile/behavior and affected-consumer
validation, independent Step acceptance review, Step commits, one comprehensive
review of its own Phase scope, bounded repair/focused re-review, recorded finding
dispositions, a closure ledger and its own local Phase-release commit.
Reuse actual applicable evidence; no acceptance/review cycle per helper or finding.

The generated top metadata is the reciprocal aggregate declaration. Phases 6,
6.1 and 6.2 omit only repository-full validation and truthfully record
`deferred-not-run`; Phase 6.3 owns `sbt --batch test` once for the entire accepted
serial sequence at its final release tree. No bootstrap exception applies:
`build.sbt` already exists. Normal development SBT still uses the registered
runner and machine-wide serial wrapper required by AGENTS.md.
This split records no test/review success or release acceptance.

## Non-goals

No Phase 7 workspace/overlay delivery, Phase 8 optional transport/provider breadth,
Phase 9 convergence/provider switching, OpenCode/local-LLM installation or new
universal agent framework. Manual local-LLM trials remain reference activity.
This planning update starts no product Goal, child execution, upstream work,
test, publication, push, deployment or commit.

## Phase Plan Gate

Phase Plan Gate: PROCEED
- target: 6h expected-duration target; 8h expected-duration ceiling
- estimate_calibration: no comparable planned/observed duration pair; no multiplier
- planning_demand: protected-decision
- recommended_parent_profile: gpt-6.1-sol / high
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: process lifecycle/live-plan writeback/Step Evidence ownership
- frozen_profile_transition_handoff: Phase 6 accepted client/mode/context/result/session contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: expected 6h; range 5–8h; centered on target and below ceiling; low confidence
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none; no child below 4h
- rebalance_attempts_for_every_sub_5h_child: none; no child below 5h
- adjacent_merge_structural_rejection_evidence: none; adjacent expected totals are 12h, above the 8h ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: three added Phase review/closure/release handoffs; final-only avoids repeating repository-full tests; no quantified cost saving claimed
- agent_reasoning_mode_policy: default standard; pro only at an eligible justified agent launch
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 6 on 2026-10-13

These are structural planning results, not verification of a future task's
runtime profile. Dependency delay is separate from the implementation estimate;
the 8h ceiling is a packing rule, not an execution timeout.

### Source history comment policy selection — 2026-10-17

Source history comments are optional, repository-specific conventions, not a universal sm-workflow requirement. Select `sm-workflow.source-history.style` from `none` (default) or `goldenport`. The previous `sourceHistory.style` spelling was explanatory shorthand, not an external property or compatibility alias. Consume the current call's `ExecutionContext.resolvedParameters`; CNCF owns common configuration loading and command-parameter precedence, as recorded in the [authoritative configuration contract](../journal/2026/10/2026-10-17-source-history-resolved-configuration-contract.md). `none` performs no source-history rule access, source read/update, check, or warning. `goldenport` explicitly selects the Asami-style source history convention (浅海式); obtain its external rule data from `sm-workflow.source-history.rule` in the same resolved configuration rather than hard-coding the convention into sm-workflow or loading a separate rule file. There is no separate `asami` selection. An unavailable selected rule reports a configuration/tool error; it must not silently fall back to another style. Phase 6.1 P6-HR01/04 owns deterministic execution only when a style is enabled. Phase 6.2 P6-HR02/03 retains semantic comment review as appropriate to the selected profile; ordinary semantic source comments are not disabled by `none`.

Illustrative project setting:

```yaml
sm-workflow.source-history.style: none # none | goldenport
```

This setting/rule-data path is connected and focused qualified as recorded above;
provider deployment and Step/Phase acceptance remain separate and unfinished.
