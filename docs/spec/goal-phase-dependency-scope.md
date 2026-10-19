# GoalPhase dependency scope

Spec ID: `goal-phase-dependency-scope`.
@since Oct. 19, 2026. @version Oct. 19, 2026. @author ASAMI, Tomoharu.

`GoalPhaseDependencyScope` is the package-private, pure input owner for
PHASE-6.2/P62-DECISION. Its existing native consumers connect a proposed dependency
change for the existing ordered Human decision. A proposal grants no approval,
workspace access, execution, acceptance or permission to edit another Component.
Normal public Usage Contract consumption and inspection need no dependency-change
question. A change to the dependency is the subject of this input.

R1. `Component(identity, repository, workspace)` names a logical Component,
repository identity and CNCF workspace ID. Proposal identity/revision and
Component identity/repository are exact nonempty strings, each at most 1024 UTF-8
bytes, without controls, whitespace, slash or backslash. Colon, dot, hyphen and
underscore are allowed. These identifiers prove neither existence nor authority.
`WorkspaceId.parseC` validates each workspace; resolution stays with CNCF.
Repository identifiers are neither URLs nor physical checkout paths.

Requester and dependency must have distinct Component identities. Equal
repository identities describe a same-repository Component boundary; different
repository identities describe an external repository boundary. External
dependencies may use the same logical workspace ID. Same-repository dependencies
remain valid and still require their own change scope; repository equality grants
no permission to edit a different Component. `sameRepository` compares only the
two repository identities on already validated input. It performs no I/O or
resolution.

R2. The complete proposal shape is:

```text
Proposal(identity, revision, discovery, requester: Component,
  dependency: Component, usageContract: ContextReference, reason,
  paths: Vector[String], acceptance: Vector[String],
  dependentWork: Vector[ContextReference], discoveredEdits: Vector[String])
```

`ContextReference` is the existing CNCF workflow value. Its identity and revision
are exact nonblank, control-free strings, each bounded to 1024 UTF-8 bytes;
their content is preserved without trimming. There is no duplicate reference
model. `usageContract` identifies the selected Usage Contract, and `dependentWork`
contains 1–32 unique exact references to dependent work. `reason` and each of
1–32 unique acceptance descriptions are nonblank, NUL-free text, each at most
16384 UTF-8 bytes; multiline content is preserved.

`paths` contains 1–128 unique literal relative `WorkspaceFilePath` values.
They are relative to the dependency's declared logical workspace. URI/colon,
backslash, controls, absolute paths, empty path segments and traversal fail.
There is no prefix expansion, target-path resolver, root inference, inventory or
file inspection. All collections, members, nested objects and Options must be
nonnull. Validation preserves the exact selected content and collection order.

R3. Discovery is exactly `PLANNING`, `IMPLEMENTATION`, `REPAIR` or `REVIEW`.
`admitResultC` always validates the optional proposal first. A present proposal
requires outcome exactly `decision-needed` and these source restrictions:

| Existing semantic kind | Allowed discovery |
| --- | --- |
| `ImplementStep` | `PLANNING`, `IMPLEMENTATION` |
| `RepairStep` | `REPAIR` |
| `ReviewStep`, `ReviewPhase` | `REVIEW` |

Every other kind fails with a present proposal. Completed, accepted,
repair-required and any other non-`decision-needed` outcomes fail with a present
proposal. `None` is normal absence: it adds no condition to the existing result
owner's kind or outcome behavior. Native implementation, repair and review
admission invoke this owner against their actual assigned kind and result outcome.
Review continues to reject every Plan revision proposal. A result cannot contain
both a Plan revision and a dependency proposal.

R4. `discoveredEdits` contains 0–128 unique paths under the identical literal path
rules and must be an exact subset of `paths`. A nonempty report requires
`discovery = REVIEW`. It represents an escaped, unapproved dependency edit
reported by independent review. The input retains the disclosed paths and reason;
it neither checks that edits happened nor approves, reverts or accepts them.
Directory-prefix membership cannot hide a file outside the requested selections.

R5. Component and Proposal companions provide strict Circe encoders/decoders.
Every field is required in its owning shape; unknown, missing, null and malformed
fields fail, including nested Components and ContextReferences. Decoding invokes
the business validators. Empty `discoveredEdits` remains an explicit array.
Errors use the prefix `GoalPhase dependency scope`.

`fromPublicProposalC` takes an optional structured `Record`, uses the existing
Record JSON conversion, then `GoalPhasePlanRevision.normalizeJsonC`, then the
strict owning Proposal codec. Recursive property normalization stays with CNCF;
any normalized-name collision fails, even if the alias values are equal.
`publicProposal` projects already validated input with
`GoalPhaseNativeMetadata.record`. Absent input remains absent. There is no
application argument, environment or configuration parser, alternate schema,
alias policy, envelope, version fallback, hash, seal or second permission source.

R6. Ordinary semantic Submit consumes the optional public
`GoalPhaseWorkResult.dependencyProposal` Record through the strict input owner.
The existing NativeWork result retains it, omitting absent values and rejecting
present null or malformed values. Ordinary Advance saves an ordered Human
question containing the exact assigned work, requesting result reference, full
result, reason and repair progress. Fresh and retained questions must match
their actual semantic source. No second proposal store or capture DTO is added.

The saved proposal offers exactly `ApproveDependencyScope`,
`RejectDependencyScope`, `Defer`, `Cancel`. Generic Resume, repair renewal and
Plan adoption cannot substitute for either dependency direction. Both explicit
directions require the existing independent Human evidence port. Human source
selection retains the entire proposal together with any review observations in
one exact, nonempty `result` object; null and unknown fields fail. Review evidence
likewise retains the full proposal. Changing the target, Usage Contract, paths,
acceptance, dependent work or discovered edits changes the selected evidence
target. Existing public-name normalization rejects even equal alias collisions.

Ordinary `prepareEvidence`, selection of its managed import and Human answer
Submit precede a separate Advance. Approval and rejection both use the existing
`DecisionResumptionAdmitted` event with the actual retained answer result
reference. The native-history walker supplies that reference to
`decisionContext.decisionReference`; a proposal and choice without a valid
reference cannot decode as dependency direction. The public context carries the
same reference and exact requesting result. No claimed AI reference supplies it.
Defer and Cancel retain the existing paused behavior without automatic work,
questions, commands or replay.

R7. Approval selects only one bounded linked work unit: the requesting semantic
result, its exact proposal, acceptance and dependent work, plus the actual Human
answer reference. A separate Advance resumes the same semantic kind and ordered
frontier with the same Plan, original base, completed Steps and repair counts.
Repeated Advance retains the pending assignment and saved answer; it does not
ask again for each selected file. A new out-of-scope dependency proposal requires
another question. There is no arbitrary future scope, hidden dependency workflow,
automatic dispatch, new stage, reopened Step or budget refund.

An independent ReviewStep or ReviewPhase remains read-only after either direction.
It may report REVIEW discovery and escaped dependency edits, but neither edits
dependencies, adopts a Plan nor approves itself. Approval does not execute linked
work or grant target, File, command, Git, SBT or acceptance authority. Those
authorities retain their existing CNCF owners. No pure Decision, ApprovedScope
or consent factory is supplied by the dependency input owner.

`GoalPhaseDependencyScopeSpec` traces R1–R5 through same-repository and external
roundtrips, source-specific input admission, exact escaped-edit disclosure,
finite/null/duplicate/path rejection, strict nested JSON and real public Record
naming/collision cases. All business reports are specification-owned fixtures.
They prove input admission only, not actual AI edits, native Human evidence,
effect authority or runtime success. `GoalPhaseDependencyDecisionSpec` traces
R3/R6/R7 through owning semantic admission, input/result/public projections,
issued-choice and evidence selectors, exact references, malformed options and
normalized-name collisions. Its constructed questions and references prove
contracts only; they do not fabricate native consent.

The explicitly selected `@DoNotDiscover NativeGoalDependencyScopeCliSpec` covers
the normal native command path for same-repository approval, external-repository
rejection, defer and refusal without prepared Human evidence. Its semantics and
answers are explicitly SPECIFICATION-OWNED. It asserts same-frontier public
context and retained reference, unchanged dependency source and no SBT marker
executions. This is command wiring proof, not genuine Human or provider execution.
Normal SBT generation owns the two appended public CML fields; no generated
Scala, Workflow schema version or controller identity is edited manually.

Parent-owned focused validation includes the dependency input/decision and Plan
revision specifications plus the changed evidence regression selection. The
separately frozen native CLI batch selects this suite and the existing Plan
revision CLI suite through `runtimeTestOnly`. This document records no validation
run. Actual Human/AI/operator execution, linked dependency editing and acceptance,
primary Step acceptance and Phase closure remain pending; specification-owned
tests cannot establish them or close DECISION.

The explicitly selected `@DoNotDiscover NativeGoalDependencyScopeLiveSpec` uses
`smworkflow.spec.p62.dependencylive.stage` for seven finite frontiers: `prepare`,
`after-proposal`, `verify-question`, `after-human`, `after-implementation`,
`after-review`, and `after-authority`. No stage cancels;
unknown stages refuse. Every invocation selects a fresh canonical `output`
directory below primary `target/sm-workflow/work`; retained stages also select
the exact existing `root`. Actual invocation argv, PIDs, stdout, stderr, Session
metadata and SBT child PIDs remain in ignored output with create-new files.
The root retains actual `dependency-live-*.stdout.json` handoffs. No latest-root
selection, Workflow-store reconstruction or substitute command evidence occurs.

This representative declared same-repository model names requester
`org.simplemodeling.p62.GuideWorkflow` (owner of `docs/phase.md`) and dependency
`org.simplemodeling.p62.InputValue` (owner of `value.txt`), both in repository
`smwf-p62-cli-continuation`, logical workspace
`sm-workflow-repository-validation-1`. Public consumption reads current UTF-8
text without changing it. Exact `expected` followed by one LF, with no CR or
extra content, is dependency-internal work requiring actual Human scope. The
unchanged continuation fixture supplies the two-Step Plan and initial product;
it supplies no live semantic proposal, answer, execution evidence or review.
`prepare` writes declared requirements before Start, binds
`usage-contract:p62-input-value@1` and `p62-live-dependent-guide@1` to meaningful
headings, and saves the ordinary guide baseline for later prefix assertions.
These logical references grant no path or effect authority.

`after-proposal` consumes separately authored result/reference files through
public DTO readers and the owning dependency decoder, submits only that real
decision-needed result, then saves the actual four-choice Human question.
Question comparison retains all non-proposal fields of the typed requesting
result and compares the full validated Proposal through `fromPublicProposalC`,
including the normal recursive public property-name normalization owned by R5.
`verify-question` selects the exact retained root and externally supplied result
and reference with a separate invocation range. It checks the saved initial and
unanswered Human handoffs, then checks ordinary Advance and repeated Advance
against that saved Human and the same complete result semantics. It leaves the
dependency and guide unchanged with zero SBT children, submits no result or
answer, prepares no evidence and overwrites no saved handoff.
`after-human` requires separately supplied GoalPhase Human evidence preparation
bound to that saved question, normal preparation/import and issued answer
Submit. Approve and Reject retain the same Session, ImplementStep, Plan, base,
completed prefix and 0/0/2 counts with the actual decision reference; Defer and
Cancel retain their actual pause. Through these frontiers, dependency and guide
remain unchanged and no SBT child runs. The driver never creates Human input or
chooses a direction; real provenance and installed Skill use remain parent-owned.

Only the saved genuine approval permits `after-implementation` to consume
externally completed linked work: exact dependency text and a truthful guide
append explaining newline and ownership while preserving its original baseline.
Approval alone performs no edit. Separate ordinary semantic Submit and Advance
then reach ReviewStep through one configured typed SBT `test` in the current
CNCF UoW. The unchanged fixture's actual test target runs ProductCheck; the
contract checks its successful receipt and `PRODUCT_CHECK_PASSED`, exact changed
paths and one child PID. It claims no ScalaTest case counts or absent task-line
marker. Repeated pending frontiers must remain equal without replay. Actual
external semantic work, Human evidence, runtime observations, independent review
and acceptance remain pending until supplied and admitted; this document records
no successful run or Phase closure.

`after-review` selects the exact saved initial and ReviewStep handoffs and an
externally supplied GoalPhase review preparation input. Its request URI must
name `dependency-live-review.stdout.json` and decode to that retained review;
the result, reference and execution evidence must be real normalized local files
under the exact root. Ordinary Advance must retain that exact Step1 review before
normal `prepareEvidence`, selection of its managed import and the issued named
semantic review Submit consume the unchanged supplied fields. The selected
preparation is saved create-new as `dependency-live-review-preparation.json`.
Separate Advance saves accepted review as `dependency-live-wait.stdout.json`,
requiring StepClosure purpose, the independently reviewed candidate and original
parent, Plan, revision and Step1 scope. Other actual outcomes are saved as
`dependency-live-review-next.stdout.json` without assuming acceptance; output
records the actual outcome. Repeated Advance must retain the same frontier,
Session, 0/0/2 counts and sole existing child Test PID.

`after-authority` requires the saved actual accepted review result and rejects
nonempty review observations before authority preparation or any closure effect.
Those observations retain their separate actual recording route. The supplied
authority preparation must reference and decode to the exact saved StepClosure
Wait and a real local authority reference. Ordinary Advance first retains that
Wait and candidate. Only normal `prepareAuthority`, managed import selection and
a separate Advance may perform the existing typed Git UoW local isolated Step1
commit, saving `dependency-live-after-closure.stdout.json`. The next request must
be Step2 `ImplementStep`, with exactly Step1 completed and the original Session,
Plan, base and 0/0/2 counts. Actual 40-hex commit identity, parent and tree are
checked without a fixed commit ID: identity differs from parent, parent is the
original base head, and tree is both the reviewed candidate and Wait candidate;
the actual review reference is nonblank. Repeated Advance retains that identical
Step2 frontier and one existing child Test PID without replay. No Step2 work,
primary commit, remote operation or Phase closure is performed by this frontier.

Both added stages consume actual externally supplied independent review and
parent-host authority provenance through the normal native ports; the driver
creates neither evidence nor authority. These are finite executable contracts,
not a record of a successful new run or primary acceptance. The independent
integrated primary Step review and sole Phase review remain required.
