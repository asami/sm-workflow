# Incremental SBT failure diagnosis: development handoff

Date: 2026-10-23
Status: allocated to planned Phase 6.6; diagnostic implementation pending
Owner: sm-workflow development chat

Allocation and baseline correction, 2026-10-23: the user subsequently selected
[Phase 6.6](../../../phase/phase-6.6.md). The
[Phase 6.6 handoff](2026-10-23-phase-6.6-incremental-diagnosis-handoff.md) supersedes
the unnumbered allocation. Fixed caller-size-limit removal is already complete;
it is not part of remaining diagnostic development. Phase 6.4/6.5 continue without
waiting for 6.6, preserving current work and existing results.

## User decision and main objective

The user requests a low-overhead diagnosis and repair route that preserves ongoing
work and existing Sessions, histories, repair counts and validation results.
The main objective is incremental information delivery for meaningful AI diagnosis,
not merely removal of a fixed transport-size limit.

Read the [specification](../../../spec/incremental-sbt-failure-diagnosis.md),
[design](../../../design/incremental-sbt-failure-diagnosis.md) and
[D1 through D4 development plan](../../../notes/incremental-sbt-failure-diagnosis-plan.md).

Deliver this route first:

```text
SBT failure → retained evidence + structured diagnostic overview
  → AI diagnosis → batched request for additional information
  → managed evidence/source reads → same diagnosis context
  → concrete repair proposal → existing repair assignment
  → focused behavioral validation → existing admission or revised diagnosis
```

## Findings and design rationale

The earlier helper rejected response.json above 8 MiB and stderr.log above 1 MiB
after storing output and waiting for termination; its response read was also
capped. These duplicate caller ceilings have now been removed. The component and
distributed helpers were confirmed identical and their regression examples were
inspected, without rerunning tests for this planning update. The initial pending
removal instruction was stale and is corrected here. Preserve actual exit/JSON
handling and retained files. Presentation size remains separate from acceptance.

The existing standalone SBT summarizer extracts counts, failed suites, compiler
failure indicators, diagnostic lines and Scala source locations. Its first-200
entry and 2000-character line truncation has no explicit omission metadata. Use
its useful fields and examples as a starting point, not as a complete contract.
The normal caller helper's structured progress-event parsing is not failure
diagnosis. The reported roughly 17.8 MB response motivates the problem; this
planning change does not independently measure that incident or AI token savings.

The established skill pattern is worth retaining: the parent diagnoses and groups
related work, and repair workers implement concrete Fix Manifests. Improve its
information supply rather than creating one agent for each failed test. Mechanical
extraction supplies facts; AI infers common causes, scope and dependencies.

## Implementation instructions

1. D1: Connect one retained real SBT failure to Common Evidence and a useful
   diagnostic view. Include assertion values, cause chain, relevant stack/source
   locations, surrounding context and explicit coverage/omission information.
   Attribute existing structured reports to the actual run when using them.
2. D2: Implement a minimal typed additional-information round through existing
   issued-work/CML/caller contracts. Batch requests, retrieve through managed
   File/Resource/Evidence APIs and return to the same diagnosis context where
   supported. Make unavailable information explicit. No ProcessExec file reader.
3. D3: Admit one concrete RepairProposal as an ordinary repair assignment and
   prove actual focused behavioral validation. Preserve old hypotheses and
   failures when findings require revision. Do not count diagnostic reads as fixes.
4. D4: Verify the integrated connection, coordinate component helper and workstation
   distribution, and hand off the actual installed interface and adoption steps.
   Source changes and isolated tests alone do not prove live adoption.

Do not first implement a general parser framework, automatic causal grouping,
parallel scheduler or model comparison. D1/D2 must join D3 early. Broader log
context remains available when local details cannot explain a failure. Delivered
references/ranges help avoid repeat reads, but a replacement agent still needs
the context it has not received. No forced tiny chunks or repeated AI selection
calls just to decide what to invoke next.

## Preservation and phase allocation

This plan neither starts a new Goal nor replaces an executing plan. Adopt the
delivered capability at the existing continuation boundary after pending calls
finish. Preserve Goal/Session/Step/work identities, accepted commits, repair
counts, existing changes and original success/failure/UNKNOWN results. Never
re-Start, re-Submit or replay accepted/uncertain effects merely for adoption or
evidence generation. Information return is not new repair authority.

The improvement is now allocated to Phase 6.6, extending existing repair and Evidence services.
It does not add completion gates to active Phase 6.4/6.5 or reopen Phase 12.
Phase 6.6 reuses the Step/Release FIX route delivered by 6.5. Phase 9 keeps convergence, model
switching and its existing automatic-Fix limit; reuse this delivery there.
Phase 13 accounting is optional for observations, not an implementation gate.
No LLM evaluation or claimed cost reduction percentage is required.

Keep existing full/heavy-test ownership and applicable review obligations.
Use focused tests for affected repairs and reuse unaffected results. No full
test, independent review or Human approval is added per information request.
Genuine scope or authority changes remain governed by existing contracts.

## Current handoff status

This update changes documents only. No helper/source implementation, test run,
deployment or live Session operation has been performed for this plan. The
sm-workflow-dev worktree and its Phase 6.5 plan were not edited. Existing concurrent
implementation and validation remain intact. Sending this handoff to another
chat is a separate action; this record does not claim delivery or adoption.
