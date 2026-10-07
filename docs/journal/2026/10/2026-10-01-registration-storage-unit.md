# Registration storage unit and host grant trace

Active Phase 2, parent-direct limited recovery. Previous turn implemented and
verified managed retained Start read (138/138). This turn owns only the
installation design note and this journal; no public API, runtime or upstream
source is changed.

Read EventStorePersistenceDeclaration.policyC, Subsystem's managed verified
view assembly, DataStoreSpace.conditionalHostEventsFinalizedC and
MultiGuardHostAdmission.validateLocalC, DataStoreLocalCompletionPlan and
WorkflowClosingProgramPreparation. The local-intent preparer admits declarative
shapes, not storage authority. The production host policy admits side Creates
only in EventStore event/index collections; local completion also rejects those
reserved collections. It currently supplies no association/receipt write grant.
Thus the closing-program seam cannot be claimed as an existing registration
writer, and a dummy Workflow/Continuation/cursor must not be introduced.

Refine standalone pre-review registration to one immutable record containing
both association and typed receipt. This removes an unnecessary requirement for
a new multi-record primitive solely for two projections of one registration.
The existing Create-only operation can be used only after explicit actor and
owner/collection admission, atomic provider/readback proof and a closed complete
record schema. Existing v1 association bytes contain too few facts to prove the
new receipt; no decoder fallback or fabricated upgrade is admitted.

Correct a residual design bullet which still placed association installation at
AI completion: registration precedes review, completion retains its own existing
typed result. Neither entry grants human approval or command authority.

Documentation-only validation: git diff --check. No SBT is appropriate for this
non-executable contract refinement. No prior test result proves the unimplemented
writer. Next: fix the complete private registration record and actor/write
admission before binding a production Operation. Phase/goal remains unfinished.
