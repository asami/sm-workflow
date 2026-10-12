# Workflow definition reference comparison

## Current Phase 5.2 C29 — minimal input using existing public contracts

This section takes precedence over all historical comparison contracts below.
The execution definitions declare `goal-phase-minimal-operation-v5`,
`split-phase-minimal-operation-v13` and `repository-sync-minimal-operation-v11`.
They expose four Goal, six Split and five Repository normal Operations. Their
local `PARAMETER` is a JSON string `arg1`: required for Start and result
submission, optional for advance. Nominal `INPUT` declares each Start business
type and each actual result/answer type. Advance declares only its actual
optional routing selectors as GoalPhaseAdvanceInput, SplitPhaseAdvanceInput or
RepositorySyncAdvanceInput. It still needs no argument in the normal host session.
The current generator requires a nominal command input; these value declarations
describe existing selectors rather than a required body or control envelope. Explicit
parameters determine the generated request arguments; these
type declarations do not introduce another body or mandatory argument. Legacy
Command/control envelopes remain retired. Genuine Required SPI request/result
types remain separate from the transport.

The normal native completion owners use the existing public
`WorkflowCompletionServiceOperation.bindC` and
`WorkflowDecisionCompletionServiceOperation.bindC`. They provide actual
business codecs, ClosingPrograms, issued-work/question readers and managed
current-call bindings. CNCF invokes its internal completion implementation;
sm-workflow does not call `completeC` or substitute an execution engine.
Use the currently available CNCF public contracts. Parallel CNCF development
and its completion are not prerequisites for this consumer's development or
validation. Deleting a nominal type declaration was not required to remove
unnecessary payloads or checks. CML declares the actual types consumed by the
existing codecs; generated metadata is not patched or forged to bypass a
binder. If an apparent new framework capability becomes necessary, first
reassess whether this consumer's design/deletion has departed from that intent.

Actual executable reference resolution belongs to the parser/binder. Business
inputs and results belong to their consuming Operation. Selected target and
effect scope prevent another participant's work or unrelated files/repository
from being mutated; real Human/reviewer evidence belongs at its effect-authorizing
consumer. Common managed completion owns state, suspension, CAS, grants and
atomic writes. None of these responsibilities creates a second copy comparison,
source-line/origin check, unchanged-content proof or validation stamp here.

Obsolete Goal step/closure-Human, Split novel-semantic and Repository old-review
declarations and their dedicated transport/ABI fixtures are retired without a
compatibility reader. Current consumer specs use actual generated outputs,
not checked-in old producer copies. Unsupported old in-flight executions are
not silently migrated: keep their historical records and start a new execution.
No duplicate-answer, winner, replay or intermediate-recovery guarantee is added.

C18/C19/C20/C29 connected validation has now run through the registered serial
runner. The first invocation stopped at a deleted existing state heading; that
heading was restored. The second generated successfully but stopped compiling
because the unnecessary advance `CommandAction` declaration shadows its native
superclass, and one issued-work load lacked its current execution context.
The third invocation showed that removing advance nominal input entirely is not
supported by the current generator. Its actual optional selectors are now declared
as distinct value types, without a framework change or extra caller input.
Those first three invocations executed no tests and remain historical failures.
After migrating four existing test callers and one fixture Start-allocation row,
connected current-features validation06 passed all 26 suites / 194 tests with
SBT/wrapper exit 0, released shared lock and parent-admitted current-request result
and consumed permission. See the [actual validation record](../../.codex-workflow/phases/PHASE-5.2/current-features-validation-05-progress.md).
No CNCF/generator change was required. This is current connected validation,
not Step/Phase acceptance: independent review, owned commits and final Phase
review/release/committed closure remain outstanding. Earlier results below
retain their original scopes.

## Current Phase 5.2 C16 — consume work and completion, not record copies

Normal Goal and Repository consumers select work or its semantic result by the
actual assigned completion, without comparing reconstructed instance records,
history prefixes or frontiers. `sameDefinition`, `sameRecord` and `includesState`
are removed, together with their exclusive comparator spec. Actual executable
reference resolution remains with the parser/binder; common storage owns actual
state/CAS/grants. No equality replacement, verifier or acceptance stamp is added.

C15's fresh registered validation completed14 suites/74 tests, SBT/wrapper0 and
lockreleased; [terminal evidence](../../.codex-workflow/phases/PHASE-5.2/legacy-entry-validation-02-terminal.md).
C16 passed fresh six-suite45-test focused validation, SBT/wrapper0/lockreleased;
[terminal evidence](../../.codex-workflow/phases/PHASE-5.2/normal-consumer-proof-validation-01-terminal.md). All-fifteen
public CML/ABI/DTO migration and connected review/release remain unfinished.
The historical reference-comparison scenarios below are not current requirements.

## Current Phase 5.2 C15 — resolve declarations, not compiled copies

Split runtime catalog now resolves actual official producer/Workflow resources and
their executable reference through the existing parser/native binder. Its complete
compiled-copy comparison is removed. Split and Repository definition binding read
actual producer declarations through that same parser, not hardcoded ABI tuples.
The current Split catalog spec resolves its actual resource classpath, not an older
packaged JAR. It accepts unused entry-state/policy metadata and retains genuine
missing, malformed, ambiguous and selected-reference incompatibility cases.

No new verifier, validation stamp or identity/whole-declaration equality replaces
the deleted proofs. Necessary reference/type resolution is distinct from proving
source lines, generated origin or saved bodies unchanged. Remaining normal consumer
comparison removal and all-fifteen public CML/provided-ABI/DTO alignment are still
unfinished; C15 does not claim that connected migration complete.

These C15 inputs passed the scoped validation recorded above. Earlier C14 and Phase 5
observations below retain their original scope, not current Step/Phase acceptance.

## Historical focused validation and Phase 5 observations

Current Phase5.2 C14 passes fresh focused validation:67 tests in9 suites,
main/Test compiled, SBT/wrapper0 and lockreleased.
[Terminal evidence](../../.codex-workflow/phases/PHASE-5.2/current-start-validation-01-terminal.md).
Its Goal runtime catalog resolves
the actual official resources and executable reference through the existing
producer/Workflow parsers and native binder. It no longer reconstructs and
compares complete compiled producer/Workflow declarations or calls admitBindingC.
Goal/Repository native assemblies also remove their hardcoded provided-ABI copy
tables. Actual generated Operations and native bindings remain in use.
Earlier Phase5 observations/results below retain their original inputs. C14 is
focused runtime evidence, not current Step/Phase acceptance.

Status: earlier corrected Phase 5 definition path had focused producer/consumer
verification on 2026-10-08. The subsequent R4 source/spec correction is UNVERIFIED;
the historical results do not accept the changed inputs. Integrated Phase 5
acceptance and read-only review remain open.

## Historical Phase 5 reference-comparison contract — nonnormative for Phase 5.2

### R1 — Separately admitted references, not content certificates

The native Workflow resolves the execution definition's identity/revision.
Neither this reference nor the runtime declaration is a proof of unchanged
content. Source lines/resources, generator identity/revision, ABI provenance,
JSON formatting, hashes and object identity are not reopening gates. New V1
records write only the reference; old provenance/digest metadata is ignored.
There is no replacement full-declaration equality or integrity ledger.

E1 uses actual generated SplitPhase ABI and producer resources admitted through
the existing CNCF parser. The second resource changes every Workflow source
line to 999, relocates the resource and changes JSON formatting, while retaining
the executable contract. Native record comparison accepts those bindings,
while checking the actual current state. This is not a full-declaration
comparison or a proof that only presentation changed.

### R2 — Actual state and revision contradictions remain rejected

This change does not migrate a saved Workflow revision, erase history, grant
execution permission, or let semantic answers select definitions. E2 admits
mutually consistent old-revision resources through the parser and confirms they
are not the current compiled definition. E1 separately changes a record's
causation and confirms that actual saved state cannot be treated as equal.
Existing persistence CAS and owner/collection authorization are unchanged.

### R3 — Executable compatibility, not declaration equality

The consuming parser/binder resolves actual executable data and its format/type
contract. No complete policy/state table, compiled declaration, identity/revision
or provenance equality replaces a removed hash. Current E3 in
GoalPhaseRuntimeCatalogSpec accepts moved diagnostic sources/display labels,
one receiver-valid state change and one structurally valid producer Judgment
text change rather than refusing differences from a compiled copy. Missing,
malformed or ambiguous actual resources still fail their owning resolution.
These C14 examples passed that fresh invocation; the older comparative policy-
substitution matrix is retired, not relabeled as current passing evidence. Do not add a
precheck where an actual downstream consumer already prevents the harm.
Current state/CAS/scope/permission obligations remain separate from unchanged-
content proof; their name alone does not justify repeated checks.

### R4 — Resolve actual state frontiers, not a complete-history certificate

Current-record comparison checks the actual instance/definition reference,
revision, lifecycle, progression, correlation/causation, composite/predecessor
and suspension state. It does not compare a complete historical body. A later
record resolves the selected prior revision and its actual frontier; it does
not prove that every preceding entry is identical to a retained copy. History
is not erased, rewritten or repaired by these read-only comparisons.

E4 constructs valid native records with three contiguous transitions. A change
to an earlier, unselected history entry does not refuse current-state or
selected-revision resolution. A contradictory selected frontier, foreign
instance, later-than-current selected revision, changed current revision or
missing record still rejects. GoalPhase native and RepositorySync native
conflict/merge-review/Human prefix consumers use this frontier resolution.
The E4 case and changed callers have not been executed. Other reconstructed
history/body assertions remain removal work, not implicit coverage by R4.

These cases verify the consumer reference comparison, not actual AI execution,
skill integration, all unnecessary-check removal or Phase 5 acceptance.

The corrected CNCF producer suites passed 51 tests; after the normal local
SNAPSHOT refresh, the eight affected sm-workflow definition/native/direct
Operation suites passed 176 tests on their earlier inputs, before R4. All actual failures/interruptions and prior
superseded evidence remain historical. See the
[implementation record](../journal/2026/10/2026-10-08-phase-5-entry-contract-audit.md).
