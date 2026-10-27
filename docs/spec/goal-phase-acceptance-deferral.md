# Explicit Phase acceptance deferral

This contract addresses `P68-PLAN-DEFERRAL-01`: an actual independent Phase
review can identify an unmet, explicitly adopted Phase condition after every
Step has closed. An independently admitted Human may exclude that condition
from the required acceptance set while preserving its unverified observation.
The same Session then owes a fresh independent Phase review and normal closure.

## Rules

R1. `phaseAcceptanceDeferralConditionsC(previous, proposed)` validates both
complete Plans and returns the exact ordered changed condition identities.
The revision must be fresh. Both Plans must have raw adopted acceptance with
identical Usage, condition membership, identity, scope, description and order.
Only a nonempty subset of `phase` conditions may change `required` from true
to false. Every other Plan field remains exact, including Steps, permissions,
checks, policies, repair limits and inventory. All/Step changes, additions,
removals, renames, reorderings, false-to-true changes and no-ops refuse.

Both saved effective selections must exist. The proposed effective selection
must equal the previous saved selection with exactly those changed adopted
Phase conditions made optional. Each changed effective definition must have
been required, sourced from `adopted`, and match the prior raw condition.
Project defaults, Usage, descriptions, sources and ordering stay exact. Later
project defaults are never recaptured for this branch.

R2. Adoption requires Decision sourced from PhaseReview, with cursor equal to
both the complete Step count and the exact valid accepted-prefix size. The
actual candidate and successful checks receipt must be present and valid.
Document decisions, pending repair, closure intent, accepted Phase review,
recording paths, release commit, pending policy repair, convergence stop and
generation overflow refuse. Actual-shaped nonempty evidence is required.
The controller increments generation once and returns to PhaseReview, retaining
the original base, all accepted Steps, candidate, checks and repository receipt,
repair counts, control, windows, observations and directive. It clears only the
old decision, decision source and review evidence. It invents no accepted
review, satisfied observation or closure.

R3. Fresh semantic capture structurally selects the closed saved
`Captured.revisionScope = phase-acceptance-deferral` for an all-closed
PhaseReview Decision with the same Step count and differing raw acceptance,
before ordinary capture. Failure never selects a fallback. Selected documents
are unsupported. The capture derives effective acceptance from the previous
saved selection and proves R1 and R2 eligibility. Historical absent scope
remains readable with its unchanged strict ordinary meaning. Removing,
forging or replacing the marker cannot route this delta through ordinary or
remaining-purpose adoption.

The actual source must be ReviewPhase without a Step identity, with every Step
completed, a candidate and checks receipt, and an actual `decision-needed`
result containing nonempty findings and the exact complete saved proposal.
Its acceptance report must satisfy the existing report contract. Every changed
condition must have an explicit `unsatisfied` or `not-evaluated` observation;
missing or satisfied observations refuse. These checks establish business
eligibility and do not constitute Human consent. Fresh preparation and saved
question readback apply the same source guard.

R4. The existing Human question uses the Phase checks and Phase validation
policy rather than indexing an absent current Step. It identifies every
deferred condition and states that excluded conditions remain unverified,
prior unmet observations and successful checks are retained, and fresh
independent Phase review and ordinary closure are still owed. Choices remain
exactly `AdoptPlanRevision`, `RejectPlanRevision`, `Defer`, `Cancel`.
The existing independent evidence, actual target, participant and atomic
completion owners remain authoritative. No new endpoint or AI consent receipt
is introduced. Adopt uses `human-phase-acceptance-deferral-adopted` from
Decision to PhaseReview. Reject retains the original Plan and resumes its
existing review; Defer and Cancel keep their existing paused behavior.

R5. The retained-answer native walker preserves candidate and validation only
for an actual AdoptPlanRevision whose validated saved capture carries this
scope. Other Plan adoptions retain their existing clearing behavior. Existing
review-context pairing and receipt validation remain authoritative. Human
Submit itself performs no checks, staging or commit. The following explicit
Advance reissues ReviewPhase in the same Session with the same candidate,
checks, accepted prefix and consumed counters. Repeated Advance is stable;
duplicate, stale, crossed-target and missing independent evidence cannot grant
adoption. Prior review observations remain available to the fresh reviewer.
An optional condition may remain unsatisfied, but Phase acceptance still
requires new independent evidence and the existing normal closure gates.

R6. Installed qualification requires an explicit absolute
`smworkflow.spec.installedCommand`. Old-to-new compatibility additionally
requires explicit `smworkflow.spec.inventoryPreviousInstallations` and
`smworkflow.spec.inventoryInstallations`. The old image must create and drive
the original normal episode through actual ReviewPhase and its accepted
decision-needed result. Only the fixture installation selector changes before
the new image continues the identical database and Session through ordinary
question/adoption. No private Workflow state is seeded or reconstructed, and
no global development runtime is selected. The compatibility example may
cancel when neither old nor new property is selected; qualification itself
requires a later actual run with both roots and the installed command.

R7. A historical native candidate receipt may carry optional `hygiene` JSON
only as completed, inert evidence. Its exact closed six-field record must have
mode `auto-fix` or `error`, empty findings and outcome `clean` or `corrected`.
Clean records have no corrections. Corrected records require `auto-fix`, a
valid nonempty original index object ID and one to 256 distinct production
`.cml` correction paths. Each closed correction retains its supported file
mode, 40- or 64-character Git object ID and staging ordinal from 1 to 256;
test, tests, fixtures, expected, golden and snapshots path segments refuse
regardless of case. Required strings and arrays must be well typed, including
the diagnostic string. The original index may be empty only for clean records.

Decoding and encoding preserve the exact raw historical JSON and optional
absence; current candidate creation omits this field. The metadata grants no
permission, ownership or effect, and existing candidate snapshots, path scope,
lineage, check receipts and effect-intent guards remain authoritative. Null,
malformed, extra-field, pending, diagnostic, remaining-findings and unknown
outcomes refuse without automatic replay, hygiene preparation or conversion
into a completed result. No persisted historical record is rewritten to admit
it. `GoalPhaseNativeCandidateBoundarySpec` supplies focused codec obligations
for this finite compatibility boundary; execution and installed old-to-new
qualification remain parent-owned evidence.

## Executable examples

E1. `GoalPhasePlanRevisionSpec` closes the accepted prefix, consumes one normal
repair, retains successful Phase checks and an actual-shaped unmet independent
review, and captures/adopts the exact deferral. Assertions cover generation,
base, prefix, candidate, receipts, counters, control and prior observations,
plus legacy same-count adoption and issued refresh refusal. Negative cases
exercise crossed source, scope, evidence, observations and unrelated deltas.
Existing corrective-Step and remaining-purpose examples remain separate.

E2. `NativeGoalPhaseAcceptanceDeferralCliSpec` uses
`NativeWorkflowCliFixture.freshReleaseFix` with real Git and normal typed SBT.
All semantic results, independent reviewers and Human answers are explicitly
SPECIFICATION-OWNED. Public preparation/import and native operations create
the Session, accepted Step prefix, successful checks, one consumed repair,
unmet Phase result and exact saved Human question. Ordinary independent adoption
and Advance must retain the candidate/checks/counters/prefix and reissue
ReviewPhase without another check or Git effect. It covers stable readback,
missing/crossed/duplicate answer refusal, fresh review evidence, and separate
Reject/Defer/Cancel controls.

E3. The installed compatibility example in the same suite drives E2's original
episode with the explicitly selected old image, switches only its installation
selector, and completes ordinary deferral adoption and fresh independent
review with the new image. The same database, Session, candidate, checks,
accepted prefix and counters must survive the switch.

These are authored specification obligations, not execution results or genuine
Human direction for a consumer Session. The parent owns generation, focused
validation, independent review, normal local publication and installed
qualification. This repair performs no real consumer Session operation and
does not implement P68-D2, reopen accepted Steps, reset counters, or establish
Phase acceptance or completion.
