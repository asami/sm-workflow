# Incremental SBT diagnosis design

Date: 2026-10-23
Status: target design; no implementation acceptance claimed
Contract: [incremental diagnosis](../spec/incremental-sbt-failure-diagnosis.md)
Delivery owner: [Phase 6.6](../phase/phase-6.6.md)

The caller's fixed response/stderr rejection and response read cap are already
removed. Phase 6.6 reuses that baseline and delivers the diagnostic interaction;
ongoing Phase 6.4/6.5 remain on their current repair procedures until adoption.

## Main connection

```text
CNCF typed SBT result + available captures/reports
  → Common Evidence + deterministic diagnostic view
  → existing issued diagnosis work to the caller
       ← NeedInformation (several selections and reasons)
  → managed evidence/source reads → same diagnosis context
       ← RepairProposal (one concrete Fix Manifest)
  → existing repair assignment → focused SBT validation
  → existing admission, or revised diagnosis from actual remaining failures
```

The view identifies facts; the AI identifies causes. Use existing business
references for run/check/evidence/issued work. Do not add hash identities,
global registries, signed diagnostic certificates or a separate persistence
model merely to associate observations. Persist the minimum additional request,
delivery and hypothesis facts through existing work history/Journal facilities.

A diagnosis case needs a failure overview, occurrence-level evidence references,
current hypotheses, delivered information references and proposed repair units.
These are conceptual data requirements, not new mandatory nested wrappers.
Inspect current CML/generated records before selecting minimal extensions.

Deterministic extraction must preserve failure boundaries and surrounding text.
For parallel or unscoped output, retain ambiguity instead of assigning a line to
an arbitrary test. Exact-text grouping retains all occurrences and cannot collapse
distinct cause hypotheses. Existing XML/report data may improve attribution, but
report presence alone does not prove it belongs to the selected invocation.

The delivery layer returns requested detail in batches, using existing Evidence
and File/Resource APIs. Reuse fetched references within the same source context;
invalidate applicability after relevant edits without discarding old observations.
Caller context continuity reduces repeat tokens, but cross-agent handoff explicitly
supplies necessary facts. No new Codex API/model run is required just to retrieve
retained material from the current caller.

## Initial boundary and later extensions

Deliver one failed SBT check, one additional-information round, one repair unit and
one actual focused validation first. Include truthful empty/partial extraction
and unavailable-source behavior within that connection. Do not first build a
general parser framework, automatic causal classifier or parallel scheduler.

Later extensions may add report formats, multi-cause units and conflict-aware
parallel assignment. Phase 9 owns convergence and provider switching. Optional
Phase 13 observations can measure model/token use; neither token instrumentation,
LLM comparison nor a claimed percentage saving is an acceptance prerequisite.

Existing standalone summarizer behavior is useful input, but executing its Python
script from the runtime for filesystem access would violate the managed-I/O
boundary. Extract through the existing runtime service boundaries. If a reusable
CNCF capability is genuinely missing, issue a narrow producer request rather than
adding a private ProcessExec workaround.
