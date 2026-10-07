# SplitPhase canonical CLI/MCP command carriers

Goal State: IMPLEMENT — sm-workflow Phase 2. Previous turn added official
SplitPhase catalog and passed 174 tests / 22 suites: progress. User-authorized
parent limited recovery, no agents. Parent model/effort not directly observable.

## Frozen implementation

Own the four SplitPhase Start/ProposalAssessment/Preview/Apply ServiceOperation
files, new SplitPhaseCommandTransportSpec.scala and this journal. Whole target
files inspected before edits. Four existing createOperationRequest methods
inspect only request.arguments, whereas canonical MCP supplies named properties.
Use the already validated WorkflowJsonCommandRequest projection for all four,
without changing JSON decoders, public ABI, selection or execution policy.

New executable scenarios bind actual Operations against the official catalog.
Counting refusal-only execution ports provide no consent, data or effects.
Observe eight accepted typed-command preparations, eight malformed-command
refusals and 32 ambiguous/duplicate/extra/non-string/foreign carrier refusals,
with zero host port calls. This is actual Operation request intake, not a regex
or helper-only test, and does not execute a Workflow or modify documents.

Use official preparation/packageBin and the existing SplitPhase* plus GoalPhase
catalog/shared-carrier accumulator. No source edits during serial invocation.
No hashes, installer, authority record, runtime service exposure or private
engine are introduced. Existing fixture Services are not promoted to production.

## Production connection findings

Start's existing BoundCall still passes SelectionPort without adapting a
CallScopedSelectionPort to the current ActionCall.Core. Proposal/Preview/Apply
also depend on fixture-owned entry/proposal/preview/semantic ports. These must
be supplied through canonical managed host contracts before production binding;
do not capture Factory-time context, invent human consent or expose inert tools.
Existing Start and document snapshot hash/fingerprint contracts require their
own deliberate conversion/admission, not implicit permission from the pending
distribution digest question. P2-R04 and full Phase acceptance remain open.

## Bounded validation repair

689 completed main compile, official preparation and packageBin, but Test/compile
failed before any suite: Request.apply requires explicit switches/properties;
Property.apply requires its optional spec argument. Full wrapper receipt was
read (exits 1/1, lock=released). Stable blocker SCP-TRANSPORT-CTOR-01 is an M1
test-construction mismatch, not a product decoder or platform denial.

Orchestrated repair mode under cncf-test-fix: freeze only the already owned
new spec, use the existing Request.of pattern with all carrier lists explicit
and Property(..., None). No expectation, count, JSON, authority or source-path
change. One bounded pre-review repair attempt, preserving 689's failed evidence;
independent review/full Phase validation remain later gates. Parent limited
recovery owns this repair. Re-run the exact frozen accumulator with fresh serial
permission metadata; do not infer consent or new acceptance from the correction.

Failed receipt: /tmp/skill.cncf.d/a-58b03e4a-9b5c-4349-bb68-ee0450c3fc97/69402-20261001T173529Z.summary.json

## Settled observed validation

690 reran the same frozen commands and passed: 179 tests / 24 completed suites,
no failures, aborted, canceled, ignored or pending cases. Full summary was read;
sbt_exit=0, wrapper_exit=0, lock=released. All three new transport scenarios
passed with zero execution-port calls, alongside existing SplitPhase and shared
carrier/GoalPhase catalog regressions. SCP-TRANSPORT-CTOR-01 closes in this
pre-review batch after one bounded correction; no review-cycle ledger is reset.

Receipt: /tmp/skill.cncf.d/a-e8d8852a-e2c5-46b5-845a-9c7af85b78d1/69979-20261001T173645Z.summary.json

git diff --check passed. Only sm-workflow was edited/tested; inspected CNCF
dependencies were not changed. The worktree remains dirty, no commit performed.
cncf-test-fix kept the correction scoped to test constructor compatibility and
retained all negative expectations. Full validation remains pending at the
Phase release gate; network MCP, managed SplitPhase assembly and restart/effect
acceptance are not established by request-intake preparation alone.
