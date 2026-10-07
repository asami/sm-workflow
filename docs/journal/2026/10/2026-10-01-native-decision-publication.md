# Native Decision publication prerequisite

Previous turn made progress: invocation 583 verified the actual Decision wire
cannot enter the WorkOrder wire path (7 tests/1 suite). Continue original
sm-workflow Phase 2. The Phase document explicitly records CNCF Phase 88 in
this task's mutation scope. Parent `/root`, standard mode, model/effort not
directly observable, owns the disclosed bounded implementation and validation.
User-directed limited recovery bypasses skill-only delegation/execution gates,
not platform review, managed authority or machine-wide SBT serialization.
No agent, acceptance, commit, push, publication or Phase closure is claimed.

## Implementation scope

CNCF workflow sources only: new private `WorkflowIssuedContinuationKind`, new
opaque `WorkflowManagedIssuedDecision`, existing issued-work adapter, instance
suspension adapter, managed issued-work binding and Component storage facade.
The existing `WorkflowManagedIssuedWorkSpec` owns the two new executable cases.
All upstream source/spec patches received scoped platform permission because
CNCF is outside the workspace writable root. Preserve unrelated dirty work.

`WorkflowComponentStorage.bindDecisionC` produces a Decision-only capability
with suspend, read and immutable equal retry. It exposes no WorkOrder SPI,
claim token, verifier, result submission or completion method. The private
host-selected kind cannot be supplied through public command/result JSON.

Existing public WorkOrder bindings default exclusively to WorkOrder. Their
wire codec and `issuedWorkPayload` storage property are unchanged. Internally
the same atomic issued-payload slot may hold exact Decision bytes selected by
the separately bound Decision codec. Readers never infer another kind or try
a fallback: the wrong kind refuses. This is storage reuse, not a Decision
disguised as WorkOrder. Stop-only Decision requests with no result type refuse
before publication and are not silently upgraded.

The shared Subsystem declaration/provider/lease and managed completion UoW
path remains the only physical route. Exact instance/history/Continuation
matching, codec round-trip, root CAS/successor Create and authoritative readback
remain joint. No private pool, direct SQL, side-state append, new grant or
human provenance is introduced.

## Executable coverage

Actual managed SQLite examples prove Decision native suspension/issued bytes
together, exact retry, changed visible question conflict, stale duplicate
suspension, WorkOrder/Decision cross-kind refusal, stopped-host refusal and
fresh physical host reopening the exact question. Both hosts retain one
managed pool and return their leases. A separate negative example proves
WorkOrder, stop-only question and foreign owner/collection refusal, leaving
the initial instance and absent Continuation unchanged.

The five-suite accumulator also covers existing WorkOrder issue, native
suspension, prepared transitions and completion regressions. It is not a
Decision completion/race/fault-injection or application Service/CLI proof.

## Validation and prerequisite recovery

- 584: exact five-suite CNCF validation stopped before compile/tests because
  the project-selected Cozy development runtime-classpath file was absent.
  Both exits 1; authoritative `lock=released`.
  Log: `/tmp/skill.cncf.d/a-8919e133-9799-49ff-8678-b3ec33dfc5aa/69549-20261001T110101Z.log`.
- 585: validation-only project-owned `cozyExportRuntimeClasspath` in
  `/Users/asami/src/dev2025/cozy` restored target/cozy.d runtime classpath and
  version metadata. No Cozy source edits or publication. Both exits 0;
  `lock=released`. No Cozy full-test acceptance is claimed.
  Log: `/tmp/skill.cncf.d/a-70b8b11b-b6f5-468c-81d9-4844333f7da5/70208-20261001T110149Z.log`.
- 586 (`SMWF-CNCF-NATIVE-DECISION-ISSUE-DIRECT-20261001-586`): the same exact
  five-suite command passed 83/83 tests, zero failed/aborted/pending/canceled.
  Both exits 0 and `lock=released`; persisted summary and the two new scenarios
  inspected. Source diff whitespace check clean.
  Log: `/tmp/skill.cncf.d/a-d83ef8aa-6640-468a-8d33-74560ef5f612/70908-20261001T110236Z.log`.
  Summary: `/tmp/skill.cncf.d/a-d83ef8aa-6640-468a-8d33-74560ef5f612/70908-20261001T110236Z.summary.json`.

All invocations use fresh typed permission storage, first-attempt scoped
escalation, normal caches and the canonical serial wrapper. None is live.

## Next boundary and remaining Phase scope

Implement separately kind-bound native Decision completion on the same managed
claim/closing-program/atomic history path. Human verification stays an
application/host prerequisite, never a result flag or authentication inference.
Only after producer completion validation and the derived SNAPSHOT artifact
refresh should sm-workflow bind its native question and answer recovery. No
dependency artifact refresh or consumer native API acceptance has happened in
this slice.

Keep existing accepted-review observation IDs immutable: allocate native
question identity/revision separately; recover the exact retained question,
receipt and pending boundary on interrupted follow-up. Do not replay human
selection or grant Git authority from the answer. Actual human issuer/verifier,
bounded command authorization/effect recovery, application public progression,
server/MCP, full Phase validation/review and exact release remain open.
