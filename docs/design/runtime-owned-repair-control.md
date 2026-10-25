# Runtime-owned repair decisions

The existing sm-workflow repair controller owns the policy and its transitions.
Caller skills convey intent, work and results; AI supplies semantic judgments.
No separate approval manager, retry ledger or skill-owned controller is introduced.

The connected path is retained failure -> issued diagnosis/repair -> actual
validation -> runtime convergence/budget decision -> ordinary continuation or
typed decision. Candidate adoption requires applicable successful qualification;
qualification failure remains input to repair, not a new permission boundary.

Restoring the executing product itself needs an executable producer scope linked
to the suspended consumer. Its transport may use existing standalone execution
facilities, but repair control belongs to the common runtime owner. Isolation of
execution must not silently merge or reset producer/consumer accounting.

During migration, absence of that new controller is not a prerequisite failure
for existing authorized restoration. One delivery owner finishes the bounded
restoration using the already-applicable standalone facilities and existing
limits while RRC is implemented in parallel. Consolidate review and handoff at
the outcome boundary; do not build a substitute skill controller or require the
consumer to approve every internal technical choice. Standalone restoration
qualification and RRC qualification remain separate claims.

Existing Session policy adoption is a typed public transition with historical
continuity, not storage surgery. Existing Journal/Evidence/Finding owners project
actual stop origins and cause/recovery distinctions. They do not infer hidden AI
text or introduce a new observation-only AI invocation.

Behavior: [runtime repair contract](../spec/runtime-owned-repair-control.md).
Work sequence: [correction plan](../phase/runtime-repair-control-correction.md).
