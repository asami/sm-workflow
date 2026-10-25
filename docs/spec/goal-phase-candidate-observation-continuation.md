# GoalPhase candidate observation continuation

Updated: 2026-10-23.

This contract repairs CAND-CONT-01 after a separately authorized candidate
observation itself failed. The original [candidate recovery](goal-phase-candidate-recovery.md)
remains once-only and unchanged.

## R1 — Exact retained frontier and chain

`describeGoalPhaseCandidateObservationContinuation(sessionId)` admits only the
selected-Step `InspectCandidate` frontier with an independently authorized
observation followed by its unresolved native `observation-intent`, with no
candidate or other successor. Original inspection, source-history intent/result
pairs and all staging intent/result pairs must be complete and unchanged.
The target retains the original RecoveryTarget, full controller, participant,
definition, root, branch, path and history policy, original recovery direction,
and the exact contiguous prior direction/observation-intent chain (1..64).
Every prior observation explicitly remains `UNKNOWN` and
`termination-unconfirmed`. No old fact, counter, Plan, base or staging receipt is
rewritten. Missing recovery, gaps, incomplete staging, foreign/crossed policy,
validation, candidate, committed and whole-Phase frontiers refuse.

## R2 — Current repository and independent Human evidence

Description obtains a bracketed stable repository/index snapshot with existing
typed Git in the current UoW: root, branch, HEAD/tree, exact dirty entries and
index tree, retaining selected ownership and original parent checks.
Repository safety admits scoped staged, unstaged and untracked entries through
the existing `scopedDirty(..., staged=false)` rule. Entries must be unique and
safe relative paths, with no unmerged entry, foreign staged path or crossed
rename. The bracket also observes owned PendingPaths against the exact retained
parent. Description performs no diff or history read, staging or SBT.
`IndexTree` uses Git `write-tree`, which can write Git objects and index cache.
Description performs no PendingDiff, File history read, staging or SBT.

The target binds the selected CNCF `cncf.git.diff.maximum-bytes` value as a
`Long`. CNCF alone parses and enforces that property while reading diffs;
sm-workflow does not apply a second diff byte limit. The default and finite
maximum are 1048576 bytes (1 MiB). Explicit host process ceilings remain
independent and can be smaller. Ordinary whole-Phase aggregation and recording
composition remain complete, with binary/ownership checks and no truncation.

`choicesFor(target)` returns exactly `AuthorizeNewObservation` and `Defer` for
clean or observation-eligible staged snapshots. Observation eligibility retains
the existing stricter `scopedDirty(..., staged=true)` rule. Otherwise safe owned
development snapshots, including `MM` and selected untracked additions, offer
exactly `ReviseWork` and `Defer`; they never authorize an observation. An
exhausted generation cannot authorize revision work. Binding, public continuation,
durable decoding and replay enforce the chosen target's eligibility. Passive
preparation checks the exact choices for the actual decoded target.
Evidence preparation uses kind `candidate-observation-continuation`, purpose
`goal-phase-candidate-observation-continuation`, and a distinct installed
independent port. It binds the complete target including snapshot and budget,
choice, rationale, participant, answer reference and component instance.
The independent attestation owns provenance of the described snapshot and
fresh Human answer. Engineering approval, earlier Submit and original recovery
consent grant no new observation. Old kind/purpose aliases are unsupported.

## R3 — Pure direction and a distinct ordinary attempt

`continueGoalPhaseCandidateObservation(input)` recomputes pure retained
storage/controller/chain/configuration facts using the supplied independently
attested snapshot, authenticates the exact binding, and appends an adjacent
mechanical direction under the existing reference namespace
`ordered-candidate-observation-continuation:<direction-source-revision>`.
Neither that operation nor direction append performs Git/File/SBT.
`Defer` authenticates without a direction or effects.

Historical `sm-workflow.goal-phase-candidate-observation-continuation.v1`
directions have no payload choice. They mean only `AuthorizeNewObservation`,
require an observation-eligible repository, and retain their exact encoded byte
shape. Any injected choice in v1 refuses. New `ReviseWork` directions use the
explicit v2 envelope with mandatory `choice=ReviseWork`. Unknown schemas,
missing/null/wrong v2 choices, `Defer` directions and ineligible targets refuse.
The type identity and mechanical reference namespace remain unchanged: one
admitted choice can occupy a frontier. Old receipts are never rewritten.

A separate ordinary `advanceGoalPhase` records a DISTINCT observation intent
under the NEW direction revision before any File/Git observation. It checks
the current selected CNCF budget and actual repository/index snapshot against
the target. Drift refuses under that new intent; approval never silently
adjusts. Only shared remaining candidate observation and retained history text
verification run. Original normalization writes and staging never repeat.

## R4 — Replay and further failure

Replay checks the retained continuation for each exact direction source before
pausing, verifies its unresolved predecessor intent and actual contiguous
history, and reconstructs each direction using its originally bound budget.
For an already accepted direction, the historical reader also uses the captured
original candidate paths and repository snapshot. Today's paths must be non-null,
safe, distinct and an ordered append-only extension of that captured vector.
Only the candidate paths are substituted for historical reconstruction; the
actual original recovery target, previous chain, controller and all other
declaration fields remain exact. The complete reconstructed target must equal
the captured target, with the same adjacent native successor. Reordered, removed,
unsafe or duplicate paths refuse. This historical read grants no current work
or effect authority and never rewrites a saved payload or UNKNOWN fact.
A new unresolved intent waits; no automatic retry or old-direction reuse is
permitted. Another attempt requires another fresh description and independently
prepared Human answer. All failed intents and UNKNOWN facts remain intact.
Observation success follows ordinary Test and independent Review; no synthetic acceptance
or Phase completion follows from continuation.

## R5 — Explicit new semantic work

`ReviseWork` independently authenticates the actual fresh Human target and
choice and appends one adjacent direction with outcome
`revision-work-authorized`. It grants no candidate observation. Its private pure
controller event carries the actual durable direction reference and rationale.
Only the current selected-Step `InspectCandidate` with no candidate, checks,
review, closure or suspension-derived acceptance is eligible. It becomes
`Implementation` for the same Step with generation + 1, retaining the exact
Plan, original base, completed prefix, repair counts, ordinary count and window.
It does not complete or answer the previous work.

Replay recomputes the same complete original chain and eligibility, applies the
same controller event and verifies adjacency and `phase.Implementation`.
A separate ordinary Advance issues one genuinely new `ImplementStep` request
under that direction's source revision and new work identity. It clears only
transient candidate, validation and inspection-observation pointers; completed
staging/history and all old UNKNOWN/termination-unconfirmed intents remain.
It executes no old intent, command, check, staging or history normalization.
Further Advance retains the same pending new work, and old direction replay
cannot issue another generation. Normal retained history traversal continues
after the new semantic result.

If current candidate paths differ from the captured original policy, a pending
`ReviseWork` direction at the actual Direction-immediate tail refuses before
issuing any new semantic work. After validating the exact saved successor, the
reader requires the actual current native revision to be strictly greater than
that Direction revision. Normal traversal must then validate the actual following
issued work or completion. Already issued original-scope work can reopen
unchanged; an append grants no additional scope. Unchanged-policy pending work
keeps its ordinary issuance behavior. New observation dispatch likewise retains
its strict current candidate-policy/controller guard before saving a new intent.
Historical reconstruction never substitutes for fresh current admission.

The optional repairDirective explicitly labels **Human-requested
reconciliation**, identifies its durable direction reference and carries the
Human rationale. It is not an independent reviewer finding. The new work must
preserve the current index and old effects and first assess whether the scoped
development changes require different adopted requirements. If so, it returns
`decision-needed` with the existing complete bounded `planRevision` Proposal
before implementation; the actual existing Human flow decides adoption. No
Plan, checks, ownership or limits change automatically. Future ordinary
candidate staging belongs to the new explicitly issued generation after its
semantic result and never replays the old generation.

## Executable evidence

`GoalPhaseCandidateObservationContinuationSpec` covers schema, complete binding,
strict preparation and crossed/stale/foreign facts. E5 covers MM/untracked
revision choices and observation refusal; E6 covers exact v1 bytes and strict
v2 refusal. `GoalPhaseOrderedProgressionSpec` E7 preserves a completed prefix and
consumed repair window while issuing the new semantic generation and allowing
the existing bounded proposal capture. The existing isolated
SQLite/Git `NativeGoalCandidateRecoveryCliSpec` retains E1..E10 and adds normal
public continuation, failed-attempt retention, repository/budget drift and
complete >128 KiB diff scenarios through `_call`, including its opt-in actual
installed executable route and exact argv assertions. CNCF specs own property
numeric boundaries, host-ceiling tightening and incomplete-capture rejection.
E16 uses that same installed-selectable `_call` route for actual isolated native
effects: safe MM description, independently prepared fixture-Human revision,
pure adjacent direction, separate new same-Session `ImplementStep` with all Git
capabilities denied, unchanged original stage/history bytes and index, no checks,
no second work on replay, and actual decision-needed entry into the existing
Plan revision question without fabricated Human adoption. These fixture answers
never supply consent for a real Session.

P116-CONT-02-TWO-READERS extends the same E16 and E11 examples. E16 refuses an
appended policy at the pending revision Direction before any WorkOrder, restores
the exact configuration for the single normal issue, then reopens identical
issued work under the append and refuses a reordered prefix. Its original
unadopted Plan-revision Human flow remains required. E11 refuses appended paths
at the existing observation guard before any new intent, restores configuration
for the actual Test, and reopens the identical Review with every complete diff
byte after an append. Both preserve full native records and stored payload JSON,
old UNKNOWN/termination-unconfirmed intents, source bytes and modification time,
index and check counts on passive or refused calls; reordered prefixes refuse
without changing those facts. No schema or public API version changes.

## Dependency candidate interruption (CC51-S1)

Recovery binds the exact saved dependency admission, repository/workspace/root/branch,
base, selected paths, Plan/Step/generation and contiguous native effect prefix.
Legacy primary-only targets retain their historical encoding and claim no dependency.
Explicit nulls, foreign bindings, substituted payloads and gaps refuse admission.

### Public target omission and raw JSON admission

Use the [candidate-recovery public Controller and payload contract](goal-phase-candidate-recovery.md#public-target-omission-and-raw-json-admission)
for both the original target and this continuation's Controller, prior observation
intents and dependency facts. Preserve the closed durable codecs, exact original
Controller equality, source revisions, subject, budget and repository checks.

| Public member | Absent | Explicit null | Present | Wrong type |
| --- | --- | --- | --- | --- |
| Dirty `renamedFrom`, in either repository snapshot | None | Refuse | Decode String and validate | Refuse |
| Dirty `index`, `worktree`, `path`; required snapshot fields | Refuse | Refuse | Decode declared type and validate | Refuse |
| Target `dependencyRepository` | None only if the original has no dependency admission | Refuse | Decode snapshot and match the admitted dependency | Refuse |
| PriorObservation `dependencyPrefix` | Existing legacy empty vector, not proof of completed effects | Refuse | Decode exact contiguous facts | Refuse |

Unknown Dirty members and property-name collisions refuse. Canonical internal
Dirty encoding retains all four keys, including null for absent `renamedFrom`;
its strict internal decoder is unchanged. Internal `dependencyRepository` null
remains invalid, as do existing dependency-extension nulls. No absent required
field becomes a default, empty repository or successful observation.

The existing preparation request reader validates the raw JSON target using
this same typed public owner before Record conversion and generated description
decoding. Object null fields otherwise disappear during Record conversion and
cannot be distinguished from omission by the later `targetC(Record)`. That
Record-only entrance handles declared omissions without promising lost-presence
checks. Use the existing resource read exactly once; do not introduce another
Operation, reader, permission gate or general recursive null policy.

Specifications exercise actual Command formatting, JSON and generated-description
decoding for primary-only and dependency-bearing targets. They exercise explicit
null, wrong type, unknown closed-shape fields and alias collisions at the raw
preparation entrance, preserving all historical internal roundtrips and binding
refusal cases. These fixture answers never authorize a real Session recovery.
The passive preparation owner derives choices from this decoded subject, never from
an editable caller choice list. The existing independent Human evidence port remains
required. Description and direction append in candidate recovery perform no Git,
File or SBT effects; the observation-continuation description alone retains its
existing explicit typed Git snapshot effects for each selected repository.

An unresolved dependency stage intent (or unfinished staging prefix) offers only
same-Step `ReviseWork` or `Defer`. Revision uses the existing controller event,
retains all receipts/index effects/unknowns/bases/counters and increments the semantic
generation; it never dispatches an old intent. Fully recorded staging permits one
separately authorized new remaining observation, with a distinct native intent.
Completed dependency capture retains its actual source and receipt provenance across
directions; only the remaining primary observation is authorized in that case.
An unknown dependency observation permits a distinct independently authorized
observation, never staging or history replay. A later unknown requires another
fresh observation-continuation target/answer. Every old unknown remains UNKNOWN with
termination-unconfirmed. No supplied snapshot, consent or source edit is validation.

At effect time the existing typed Git owner brackets primary and dependency freshness
and checks exact scope/grants/bases/index/diff. Completed captures are freshness
checked and referenced, not recaptured under the direction's source revision.
Ordinary separate Advance, selected validation, independent review and closure retain
their owners; no additional workflow or automatic replay is introduced.
