# Typed partition enrichment publication and recovery

Date: 2026-10-02 (Asia/Tokyo)
Status: bounded application implementation compiled and focused-validated; not independently reviewed or accepted.

## Authority and observed predecessor

Continue the same original sm-workflow Phase 2 and admitted CNCF Phase 88
dependency, not a successor or replacement Goal. The user-directed correction
`テスト設定を補正して続けて` closed the public producer fixture failure.
Producer-786 passed 92/92 examples, SNAPSHOT refresh 787 succeeded, and the
unchanged consumer runtime/package accumulator 788 passed 415/415 in 64 suites.
Their exact terminal receipts are retained in the
[producer prerequisite journal](2026-10-02-partition-enrichment-atomic-publication-dependency.md).
The consumer runtime classpath selects the updated normal Ivy jar, and its
public `ComponentRecordStorage.stageCreateC(Record)` was observed with javap.
No live SBT handle remains. Actual `get_goal` now reports the same Goal active;
the prior blocked observation remains history, not a pending technical choice.
No Goal was recreated or reactivated by an assistant tool call.

Retain the user's limited parent-direct recovery, no agents, serial SBT, native
scoped execution permissions, existing caches/fixed JVM and exact terminal
lock-release evidence. Keep original bases, acceptance scope, independent
reviews, Step/release commits and all old repair ledgers. This bounded plan
neither manufactures missing historical typed authority/base records nor
claims a complete ordinary Phase PLAN admission or Step acceptance. Parent
runtime model/effort is not observable; do not invent profile evidence.

The existing managed completion binds a real generated public Operation and
atomically retains raw Judgment, typed closing result and adjacent history.
It computes resolved estimates/edges and deterministic next need in memory,
but does not yet persist those new input rows. The required host AllocationPort
chooses references explicitly; no deterministic naming convention or installed
production allocator is established. Those references cannot be guessed or
reallocated during recovery. The public component's default production assembly
still does not install this completion/allocation/human composition; that
installation remains required later, not evidence supplied by a private spec.

## Frozen behavior

Publish one immutable set through the existing canonical completion transaction:

- submitted semantic Judgment and its common evidence;
- typed closing result and the exact adjacent instance/history entry;
- a new complete estimates row iff novel work was resolved;
- a new complete handoff-plan row iff unknown boundaries were resolved;
- one application publication record linking the canonical result, context,
  instance, continuation, completed revision and exact new row references.

The publication record is necessary durable application data, not a claim,
hash, grant, acceptance flag or completed-result assertion. Its physical entry
identity is the canonical retained result identity; a caller never supplies a
separate recovery locator. New row identities/revisions come only from the
existing required host allocation. Create collision never becomes Save/upsert,
automatic renumbering, compensation or another allocation attempt.

Projection uses only privately constructed `SplitPhaseTypedPartitionCompletion.Plan`
and its admitted semantic outcome. Preserve all known estimates, original source
and proposal links, closure/ownership data, handoff authorities and ordering.
Reuse the existing strict planning-input codecs for full estimates/handoff rows.
The original nine planning inputs, context and Start remain unchanged. A
DecisionNeeded publishes only existing common completion/history; it stages no
estimates, handoffs or enrichment publication record and implies no genuine
human answer. No events are added and EventStore selection policy is unchanged.

Stage inert immutable Creates with `ComponentRecordStorage.bindForCallC` and
`stageCreateC` inside the actual owning native call. Compose those programs before
the existing typed Completed action. Do not execute separate writes before or
after completion, expose internal CNCF intent/guard APIs, preserve a capability
beyond its lease, capture a bootstrap ExecutionContext or create another pool.
The Workflow's native host re-admits captured same-owner/same-store Create grants
and verifies the entire committed post-image. The response remains the existing
post-commit WAIT identifying the actual deterministic next need, not a fabricated
WorkOrder or full SplitPhase completion.

## Publication data and host metadata

Add a closed application codec at schema
`sm-workflow.split-phase-partition-enrichment.v1`, field
`splitPhasePartitionEnrichmentPayload`. Raw data contains explicit result and
context references, component and instance identities, continuation identity,
completed instance revision and optional estimate/handoff references. Every field
is required, including explicit optional absence. Bound UTF-8 payloads at 1 MiB;
reject unknown/missing root or nested fields, malformed references/revisions,
null structures, wrong primitives, foreign schemas and mismatching exact selected
result reference. There is no legacy decoder, newest-row search or hash field.
Decoding constructs raw data only; it cannot construct usable recovery state.

Add explicit host configuration:
`sm-workflow.split-phase.partition-enrichment.store-name` and
`sm-workflow.split-phase.partition-enrichment.record-collection`.
Decode canonical exact values at assembly without acquiring a physical resource.
The store must equal the current Workflow store. The publication collection must
be distinct from all nine planning-input purposes, selection, Start, context,
instance and continuation collections. Reuse their existing declarations rather
than inventing a second collection mapping. Native captured-grant validation
still excludes EventStore infrastructure. Configuration restricts purpose; it
does not grant Create/read access or install the new collection automatically.
Only the trusted host captures the three required Create collections (estimates,
handoffs, publication), and each call must bind the exact target capability.

## Recovery: observation and deterministic reconstruction only

Add `Binding.recoverForCallC(continuation, core)` separately from the existing
raw retained-completion observation API. Reuse current-call issued-work, context,
original typed Start, all nine independent current-input readers and native
instance reader. Authenticate the current owning call and exact Start participant;
value-equal forged security or an expired call does not suffice. Read canonical
completion before constructing recovery. No completion yields no recovered state,
not a new issue, allocation or guessed result.

Recover the actual suspended prefix immediately before the recorded completion
from validated native history. Call the existing pending/result admission with
the exact durable issued WorkOrder and raw retained Judgment. For Enriched,
allocation references are read only from the publication row selected by the
canonical result; never invoke AllocationPort. For DecisionNeeded, use explicit
empty resolution references and the canonical result reference, with no
publication-row requirement. Verify exact retained closing value and recorded
completion revision, and match the reconstructed completed history prefix to
the real native history. Later legitimate history may follow that prefix;
recovery never rolls it back or treats an unrelated later revision as the
completion being reconstructed.

Reproject the expected publication metadata and full enriched rows from that
admitted Plan. Read only the selected new references through newly bound native
capabilities and compare complete decoded datasets with the expected projections,
including their exact versions and preserved known values/authorities. Missing,
crossed, malformed or divergent data denies usable recovery even when the common
completion remains durably observable. Do not relabel that as rollback, retry
an uncertain Create, or accept a stored success flag.

Only this complete comparison constructs private `Recovered`, containing the
reconstructed Plan/next mechanical need and actual observed current history.
Recompute deterministic planning from persisted semantic input with the existing
kernel; do not ask AI to estimate/boundary-review again, call the allocator,
reissue work or mutate history. This directly advances the explicit cost-reduction
goal while keeping all semantic admission in Workflow rather than skill routing.
Publishing subsequent host planning/next issued work and installing the genuine
production ports remain later required boundaries.

## Exact implementation frontier

All source/spec paths below are relative to the existing sm-workflow root. No
CNCF, Cozy, CML/ABI, shared skill/config or production-default-installation edit
is admitted in this application slice.

1. Add `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePartitionEnrichmentPublication.scala`:
   raw linking record, private constructed publication projection and verified
   recovered state; complete dataset comparisons, no I/O or freely constructed
   usable result.
2. Add `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePartitionEnrichmentJsonV1.scala`:
   closed bounded raw linking-record codec only.
3. Add `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePartitionEnrichmentStorage.scala`:
   pure host declaration binding, call-local stage/load through existing public
   native capabilities and existing complete planning-input codecs. No provider,
   lease, mutable cache, default authority or ordinary writer is owned here.
4. Extend `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedPartitionCompletion.scala`:
   pure `recoverC` using validated actual history prefix, existing Pending/Plan
   admission and exact canonical retained result/closing comparison. Preserve
   existing constructor visibility, semantics and DecisionNeeded behavior.
5. Extend `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedTypedPartitionCompletion.scala`:
   bind only new storage metadata during assembly, stage publication on the
   current closing call, and expose strict call-scoped recovery without allocator
   invocation. Keep raw retained observation and existing generated Operation.
6. Add `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePartitionEnrichmentPublicationSpec.scala`:
   pure complete projection, unknown-only preservation, empty DecisionNeeded,
   exact version/data mismatch, malformed/closed-schema codec, host declaration
   separation and private construction behaviors.
7. Extend `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedPartitionCompletionSpec.scala`:
   exact native-history prefix recovery, complete input drift and crossed retained
   result/closing refusal, recovery with later valid history, no freely accepted
   completed snapshot. Add adjacent Given/When/Then for modified behaviors.
8. Extend `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedTypedStartOperationSpec.scala`:
   preserve existing Start/issue/service/CLI cases, configure the publication
   purpose and captured exact grants, prove actual public completion Creates
   and physical reopen recovery; add native missing-grant and collision cases,
   no partial publication, bad recovery data and current participant/call refusal.
   Keep fixture-only human/allocation evidence explicitly separate from production.

This journal and `docs/phase/phase-2.md` are the two evidence/projection paths.
Existing primary programs/specs and their direct storage/codec collaborators
were read completely. Existing untracked files are original owned in-progress
work, not disposable scaffolding or accepted clean baselines. Preserve all other
dirty work. Existing private camelCase helpers in the touched legacy files are
pre-existing naming Hygiene, not authorization for unrelated whole-file cleanup;
new declarations use the retained repository naming contract at authoring time.
No new Development Candidate substitutes for any required acceptance above.

## Executable validation and remaining gates

Use the original single consumer runtime/package/focused accumulator, whose
`SplitPhase*` wildcard covers the new specification. This provides main/Test
compile and native public Service/ordinary CLI representative coverage without
a duplicate standalone compile or full suite. Test the new estimates/edges with
known entries preserved, complete metadata/reference equality, physical reopen
with unavailable allocation port and no new verifier/allocator/AI calls, later
native history preserved, missing grant/collision with raw result/history/new
rows all absent, wrong schema/reference/current facts/actor/expired call and
post-completion replay refusal. Restore deliberately altered fixture bytes;
do not weaken assertions or use memory-only publication as proof.

Use fresh single-use command IDs after 788, exact shared serial wrapper/native
scoped review, standard JVM/caches and authoritative terminal evidence. No edits
while SBT is live. No upstream refresh is needed unless actual producer bytes or
selected dependency identity change. Permit at most three converging frozen
pre-review repairs for this new implementation unit; retain every earlier unit's
ledgers separately, including exhausted producer repairs and user-directed batch.
No repair attempt, test or permission artifact was created by this PLAN.

Select the already required `step-protected-focused` independent conformance
route at Step acceptance for captured authority/native atomicity/recovery. No
reviewer is spawned or review waived by retained no-agent limited recovery.
Original accepted Step commits, mandatory full Phase review, release-tree full
validation and final release remain outstanding. Genuine human/allocator
installation, public advance, server/MCP/separate-process/bundled generic sm-*
integration are not proved by these fixtures and remain original acceptance.

The PLAN turn recorded design and exact ownership only. Its next state was IMPLEMENT of
this bounded frontier; no runtime/spec source edit, SBT, Git write, publication,
shared installation, cleanup, new pool or direct application SQLite occurred.

## IMPLEMENT candidate, next continuation

The next active-Goal turn applied the eight frozen application source/spec
paths under the retained user-directed parent-only recovery. Existing source
and fixture behavior were preserved; no upstream source, public CML/ABI,
shared skills, production default installation or Git mutation occurred.

The candidate now provides privately constructed complete publication projection
and verified recovery, a closed 1 MiB raw mapping codec, fifteen-purpose pure
host binding, and call-local inert staging via public `stageCreateC`. Staged
programs precede the canonical Completed action. The managed binding exposes
separate strict recovery using the actual issued/context/Start/current-input
and history observations, canonical retained completion and independently
loaded enriched rows; it contains no AllocationPort invocation. DecisionNeeded
still stages no enrichment rows. This describes candidate source, not proven
runtime behavior.

Executable spec additions cover complete known-value/authority preservation,
raw nested schema/reference refusal, immutable private construction, actual
completed prefix reconstruction with later native history, and full row
comparison. The existing public Service/ordinary CLI SQLite fixture now declares
the three enrichment Create purposes, checks complete durable rows and mapping
on initial/reopened hosts, makes its allocator deliberately unavailable after
reopen, and exercises missing-estimate-grant and publication-identity collision
without partial completion or new rows. Recovery also checks changed complete
estimates, crossed/malformed publication, changed original participant and
expired native call. Fixture mutations restore original payload bytes. Human
and allocation evidence remains explicitly private fixture evidence, not
genuine production installation.

`git diff --check` returned exit 0 after these edits. This is only a whitespace
check (and Git does not include untracked source content in that check); it
does not prove compilation, schema acceptance, native transaction behavior,
reopen recovery or any Step/Phase gate. No SBT, runtime/package refresh, command
permission, review or acceptance was executed in this implementation turn.
Pre-review repairs for this application unit remain **0/3**. Earlier 786/788
results prove only the predecessor tree and are not current-tree evidence.

Continue IMPLEMENT with the frozen unchanged consumer runtime/package/focused
accumulator and fresh serial command ID after 788. Preserve all earlier ledgers,
scope, independent reviews and final acceptance obligations. Do not regard
candidate coverage as a green test or mark the Phase complete.

## Focused validation 789

The next IMPLEMENT continuation observed the same original Goal active and
read the current tree and candidate journal before execution. The command,
serial wrapper and typed permission contracts were unchanged. The retained
parent-only recovery changes routing only; native scoped permission, shared
serialization, all original authority and assurance obligations remain intact.

Single-use command identity:
`SMWF-PARTITION-ENRICHMENT-PUBLICATION-RECOVERY-DIRECT-20261002-789`.
The existing 457 retired command IDs were carried into the fresh permission
builder; 789 was then retired before its one invocation. No reused permission,
legacy management hash or isolated dependency cache was used.

The exact unchanged consumer accumulator ran through
`/Users/asami/.agents/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`:

```text
--batch
cozyPrepareRuntime
packageBin
testOnly org.simplemodeling.textus.smworkflow.SplitPhaseGeneratedAbiSpec org.simplemodeling.textus.smworkflow.admission.SplitPhase* org.simplemodeling.textus.smworkflow.admission.GoalPhaseHostRecordReadPolicySpec org.simplemodeling.textus.smworkflow.admission.GoalPhaseManagedSuspensionOperationSpec org.simplemodeling.textus.smworkflow.admission.GoalPhaseRuntimeCatalogSpec org.simplemodeling.textus.smworkflow.admission.WorkflowJsonCommandRequestSpec org.simplemodeling.textus.smworkflow.admission.ProfileStartAdmissionSpec org.simplemodeling.textus.smworkflow.admission.GoalPhaseConfiguredComponentFactorySpec
```

The wrapper accepted fresh permission, selected heap 4096 MiB / stack 4 MiB /
code cache 512 MiB / G1 and existing normal caches. All five changed main Scala
sources and three changed Test sources compiled. Runtime metadata and package
generation succeeded. The runtime classpath still selects the admitted normal
Ivy `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT` jar refreshed by 787.
No upstream source/artifact refresh was needed in this continuation.

Terminal receipt, completed 2026-10-02 16:34:18 JST:

- **421/421** tests, **65** completed suites, 0 aborted, failed, canceled,
  ignored or pending; all tests passed.
- `sbt_exit=0`, `wrapper_exit=0`, `compile_failed=false`, `lock=released`.
- Summary:
  `/tmp/skill.cncf.d/a-62e7554b-576a-43bf-bdb0-8493e23a82f4/52396-20261002T073346Z.summary.json`.
- Full log:
  `/tmp/skill.cncf.d/a-62e7554b-576a-43bf-bdb0-8493e23a82f4/52396-20261002T073346Z.log`.

The authoritative summary was read after the process became terminal. The
full-log selected suite block confirms both public native fixture examples
passed: fifteen-purpose metadata restrictions and the expanded initial/reopen
Service/ordinary CLI behavior. Its missing-grant and Create-collision attempts
preserve suspended revision 2 with no common completion or new estimates/handoff
rows; the preexisting colliding mapping remains unchanged. The initial and
reopened hosts observe all complete committed enrichment rows. The reopened
allocation port explicitly fails if called, while recovery and replay-refusal
counter checks pass. Service recovery preserves a real later native revision
4 and reconstructs completion revision 3. Malformed/crossed publication,
same-reference complete row drift, foreign original participant and expired
call refuse usable recovery, with original fixture payloads restored. Decision
recovery retains HumanDirectionRequired without an enrichment row or next AI
WorkOrder. The four new projection/codec examples and two new pure prefix
recovery examples also passed; these explain the six-test increase over 788.

No source bytes changed during or after this SBT invocation. Application
pre-review repair count remains **0/3**. No live process remains. This result
proves the focused application publication/recovery slice, not genuine human
or allocator installation, installed public advance, separate-process or
server/MCP/packaged-skill acceptance, Step acceptance or full Phase closure.
Those original requirements, the selected independent protected conformance
review, accepted Step commits, mandatory full Phase review, release-tree full
validation and final release remain open. Continue the original Phase without
redefining it around these passing fixtures.
