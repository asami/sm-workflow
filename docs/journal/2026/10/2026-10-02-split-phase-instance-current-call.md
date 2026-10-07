# SplitPhase instance persistence at the current managed call

Goal state: Phase 2 IMPLEMENT, continued under the retained explicit limited
parent recovery. Previous turn is progress: 706 passed 249 tests / 35 suites
including exact immutable issue retry and real pool shutdown/reopen. Preserve
all original Phase bases, scope, independent Step/full review and commit gates.

## Frozen implementation boundary

Observed violation: SplitPhaseManagedStartAssembly.bindPersistenceC captures its
Factory-time implicit ExecutionContext in the anonymous instance SPI. Current
selection/entry/proposal readers correctly use the incoming Operation capsule,
but instance create/load/append do not. Consume existing canonical bindings at
the current call, not a new provider, pool, schema or grant. Own new internal
CallScopedWorkflowInstances.scala; SplitPhaseManagedStartAssembly.scala;
SplitPhaseStartServiceOperation.scala; SplitPhaseProposalAssessmentServiceOperation.scala;
SplitPhasePreviewServiceOperation.scala; SplitPhaseApplyServiceOperation.scala;
SplitPhaseSemanticWorkflow.scala; SplitPhaseManagedStartAssemblySpec.scala and
SplitPhaseSemanticWorkflowSpec.scala. Preserve all coexisting dirty changes.

One internal SPI trait denies context-free CRUD and exposes call-scoped CRUD.
An ephemeral view forwards the exact capsule, leaving ordinary fixture SPI
unchanged. Managed implementation checks the actual sm-workflow component,
the exact owning Subsystem and bound configuration, requires the dispatch lease
through the existing application datastore binder, and restores the prior
DataStoreSpace binding in finally. Retain only the canonical instance binding;
no provider, lease or Factory ExecutionContext escapes. Update all four Split
Operations and semantic completion/automatic Preview to choose this per-call
view. Host equality checks still compare original shared ports at composition.

Existing seeded storage scenario must explicitly use storage.bindInstancesC
for its private non-Operation fixture setup/readback, not bypass the new public
call-scoped SPI. New real SQLite GWT scenario observes private fixture CRUD in
actual Start dispatch before returning absent human selection. Prove distinct
incoming contexts, exact history, stale append/configuration refusal, plain API
refusal and expired/foreign/incomplete capsule denial. No claim of positive
public Start: no human ingress is manufactured. Extend the existing positive
memory Service/CLI semantic scenario to observe instance load/append callbacks
at the exact completion capsule. This proves forwarding only, not managed
semantic completion or durable intent/catalog.

Whole-file inspection completed for modified Operations, assembly, semantic
workflow and affected specs. Existing fixture hashes/loose semantic transaction
remain separate known production conversion work; this delta introduces none
and promotes none to production. No public ABI, JSON, definition, grant,
configuration policy or protected producer contract changes. No other repository
edits, skill changes, Git mutation, publication or install. Parent-owned limited
recovery; actual parent model/effort not directly observable, standard mode.
No agents are started. Skill-only delegation stops are overridden by the direct
human recovery instruction; sandbox/platform review and shared SBT lock remain.

## Evidence plan

Every created/modified behavior scenario uses AnyWordSpec, adjacent
Given/When/Then and should matchers. Fresh invocation 707 uses the unchanged
runtime/JAR and focused accumulator from 706. At most three bounded mechanical
pre-review repairs, stop on new semantic/scope/root issue or stalled behavior.
This is not a reset of 702–705's exhausted unit or 706's closed correction.
Independent acceptance/release reviews remain unperformed. Full Phase positive
ingress, all-profile runtime, separate-process restart, managed semantic intent,
canonical server/MCP parity, management, external-command receipts, costs,
resident references and installable public skill proof remain incomplete.

## Validation and bounded repair

707 terminated during main compilation: direct access to ComponentCreate's
private[cncf] subsystem carrier is intentionally unavailable. No tests ran;
both exits 1 and lock released. Full summary read at
`/tmp/skill.cncf.d/a-88277fae-9eb6-481e-9849-d2b0ea6451f7/441-20261001T190539Z.summary.json`.
Repair 1 follows existing GoalPhaseManagedStartAssembly.RetainedReadCall:
retain only typed configuration/definition/collections, derive storage from
the incoming Component's actual Subsystem, re-admit the exact canonical grant,
require its dispatch binder and obtain the canonical instance binding per call.
Factory's probe still validates composition, but its instance binding is not
retained. No reflection, Product/extractor access to opaque assembly, public
getter or producer change. This supersedes the earlier proposed captured-owner
reference comparison: the current actual owner and grant select the provider,
so no old-host binding is used at all. Same frozen contracts and validation;
fresh retry 708. Remaining repair allowance: 2.

708 compiled and passed 249 / 250 tests in 35 suites, including semantic
Service/CLI instance callback forwarding and retained WorkOrder reopen. The
new actual host scenario passed current-call CRUD/readback, plain/incomplete/
foreign component/configuration refusal and binding restoration, but its
post-dispatch read unexpectedly succeeded. Both exits 1, lock released; full
summary and precise diagnostic read at
`/tmp/skill.cncf.d/a-42301a85-32a4-4f98-9648-ba9f1c6d0818/1305-20261001T190744Z.summary.json`.
Cause: WorkflowInstancePersistenceBinding independently borrows a new host
lease on each method; calling it after the incoming binder resurrects access
instead of using the dispatch-bound lease. Repair 2 consumes the existing
WorkflowInstanceDataStorePersistence directly from current DataStoreSpace's
managed application store, after the same pure WorkflowComponentStorage grant
admission. The canonical borrowed adapter remains inside the synchronous call
and restores binding finally. It opens no pool/lease, retains no store or
provider, and creates no substitute engine. The original ended dispatch lease
now controls the actual CRUD. Retain the failing expired-call assertions;
do not weaken lifetime semantics. Fresh unchanged accumulator retry 709;
remaining repair allowance: 1.

709 succeeds: runtime preparation and packageBin, 250 / 250 tests in 35 suites,
no failures/aborts/cancellations/pending examples; SBT and wrapper exits 0,
matching final marker `lock=released`. Full summary and GWT diagnostic read at
`/tmp/skill.cncf.d/a-554ee996-8c4f-4bae-a644-b633e51a4e0d/2094-20261001T190948Z.summary.json`
and its sibling log. The actual SQLite probe confirms two distinct incoming
contexts (neither Factory context), initial Create, one append, exact canonical
readback, stale revision/configuration/incomplete/foreign-component/plain API
denial, DataStoreSpace restoration, and rejection of all CRUD on the escaped
completed capsule. Start still refuses missing selection. The positive memory
Service/CLI semantic scenario observes both instance-load and instance-append
callbacks at the identical incoming completion capsule alongside all original
evidence/intent/catalog callbacks. Existing storage shutdown/reopen passes.
The borrowed adapter is used only synchronously and is never returned or stored.
`git diff --check` passes. No agents, acceptance review, stage/commit/publication.

This IMPLEMENT unit has made progress, not reached Phase completion. Current
production Split host entry assembly consumes the current-call instance port;
semantic runtime still needs managed intent/publication/completion/catalog and
identity conversion before standard Factory/launcher/server activation. Do not
promote the seeded provider or memory scenario to positive public human ingress,
durable semantic completion, separate-process recovery, MCP parity, full-suite
or accepted Step/Phase evidence. Original full Phase gates remain incomplete;
next state remains IMPLEMENT on those runtime integrations.
