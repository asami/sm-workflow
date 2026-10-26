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

D1 implementation uses a read-only projection of the retained typed native SBT
receipt. The validation view adds `diagnostics`; it does not rewrite the receipt
codec or outcome. It recognizes attributable console boundaries, preserves raw
line locators and distinguishes compilation-blocked selections from tests that
actually ran in other aggregate projects. Missing counts remain unknown.
Preceding suite headers are context, not proof of attribution under parallel output.
The bounded initial display reports omitted groups, occurrences and context;
the complete retained capture remains the source. Identical blocks are grouped
only within the same stream and do not establish common causation. Unsupported
formats stay explicitly unmatched. Common Evidence keeps existing Test stdout
identities and additionally archives available failed Compile/check stderr and
typed incomplete prefixes with distinct prefix output identities. An association
or source projection alone does not prove that archival succeeded.
This source change is not installed-route or connected D1–D4 acceptance.

D2 adds one declared information operation on the same GoalPhase service. The
native Port walks contiguous retained history and requires the exact currently
open failure-bearing RepairStep and canonical WorkOrder context before reading.
The operation does not call Advance, Submit, Git, SBT or a semantic provider.
Reasoned selections expand original retained occurrence context, cause/stack
lines, execution-wide output or an owned repository source file through File/UoW.
Unattributable suite context remains partial; unknown formats retain the full
available output route. Current source reads do not assert candidate equality.

Delivered batches use immutable Common Evidence associations keyed by the work
continuation/revision and request identity. Their metadata's source reference is
indexed by the existing Evidence Journal producer; a finite paged query supplies
replacement-worker references without copying raw logs into Journal. Historical
Journal payloads remain readable without the optional new producer index. A
repeated batch reuses the retained observation; a fresh identity is required to
observe edited source. Context-loss recovery reads retained facts through the
normal Evidence service. Storage/Journal availability is distinct from source
availability and original check failure. D2 focused evidence and later D4 native
connection are separate acceptance scopes.

D3 keeps the existing RepairStep and semantic result, adding a strict optional
Fix Manifest rather than another diagnosis executor or progression loop. The
provider forms cause/evidence/edit/dependency/verification directions within the
issued scope before the repair participant edits. Submission retains that
manifest in the existing native result. Current-work information references
must be confirmed Common Evidence observations for the same issued continuation;
log references must address the captured failure. Scope and selected compile plus
behavioral checks are admitted; causal claims and implementation quality still
need their ordinary evidence. A source fix cannot claim behavioral success.

The contiguous native-history walk accumulates accepted manifests and projects
same-Step prior diagnoses into later work. No independent counter, history store,
retry controller or mandatory diagnosis review is added. Old accepted results
remain unchanged. A RepairStep deliberately has no current candidate: additional
information therefore binds to `checkFailure.validation.candidate`, the actual
failed source, rather than inventing a candidate on the repair assignment.
Common RRC qualification and connected managed execution remain separate delivery
evidence and cannot be inferred from these source contracts or fixture counts.

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
