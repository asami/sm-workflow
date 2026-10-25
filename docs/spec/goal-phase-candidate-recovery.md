# GoalPhase complete-staging candidate recovery

For a failure of the separately authorized new observation, see
[candidate observation continuation](goal-phase-candidate-observation-continuation.md).
That is a fresh independent Human boundary; this original recovery remains once-only.

Date: 2026-10-23 (Asia/Tokyo)

## R1 — Exact original frontier

`GoalPhaseService.describeGoalPhaseCandidateRecovery(sessionId)` reads native
storage and current configuration only. Eligibility requires the original,
unsuspended selected-Step `InspectCandidate` source, its exact saved inspection,
all adjacent successful source-history pairs if present, and successful adjacent
staging intent/result pairs for every nonempty distinct selected path. The actual
current frontier must be the last staging result. Native references and actual
next history causes determine the chain; a client path list cannot select it.
Missing results, gaps, crossed payloads, unknown staging, candidate/Validation,
whole-Phase, suspension, previous recovery, observation intent and later frontiers
refuse. Completed history outcomes are conservatively limited to `updated` and
`unchanged`. No stored receipt is rewritten.

The target binds Session, full current Plan and controller snapshot (including
completed Steps, base/tip, generation, repair counters and pending repair state),
definition, participant, original source, frontier, inspection, every history and
staging reference/record revision/payload, host root/branch/path policy and resolved
source-history selection. The old observation is explicitly `UNKNOWN`, with
`termination-unconfirmed`. Neither outer CLI exit nor successful stage receipts
establish termination, cancellation, success or `NotExecuted`.

## R2 — Independent Human binding

The exact choices are `AuthorizeNewObservation` and `Defer`.
`NativeHostPreparationService.prepareEvidence` with `family=GoalPhase` and
`kind=candidate-recovery` passively prepares the actual description and supplied
`GoalPhaseCandidateRecoveryAnswer`, reference, matching worker/participant, issuer
and time. Its dedicated `CandidateRecovery` evidence purpose binds component,
entire target, choice, rationale, reference and participant. The trusted host
independently provisions and attests genuine Human evidence. Preparation alone,
prior implementation Submit and support authorization grant no recovery consent.
Missing, foreign, crossed or stale evidence refuses.

## R3 — Durable direction only

`GoalPhaseService.recoverGoalPhaseCandidate(input)` rederives the target and admits
independent evidence. `Defer` authenticates and changes nothing. Authorization
appends exactly one pure managed mechanical/CAS direction at the final staging
result, in namespace `ordered-candidate-recovery:<original-source>`, with
`InspectCandidate` and the controller unchanged. Describe and recover perform no
Git, File or SBT effects. Stale and duplicate directions refuse. Source-history,
validation and closure recovery retain their existing contracts.

Reading an already accepted canonical Direction may reconstruct its captured
candidate paths when the current declaration preserves that exact ordered
vector and appends only distinct safe relative paths. The reader loads and
decodes the actual retained receipt first; caller-supplied targets cannot supply
this historical policy. Reconstruction changes only the declaration's
candidate paths to the captured vector and requires complete target equality.
All other current fields remain exact, including root, branch, controller,
Plan, base, participant, source-history rule, management paths, provenance and
every retained prefix. Removing, replacing or reordering original paths
refuses. The actual original frontier and adjacent native successor remain
required. No stored Direction, UNKNOWN outcome, unconfirmed termination or
completed fact is rewritten.

This reconstruction is solely for reading accepted history. New targets,
independent Human admission and new observation effects continue to use the
complete current declaration. An accepted Direction whose observation has not
started cannot authorize effects under an expanded policy: the existing strict
observation guard refuses before recording an observation intent. No new gate,
effect path, API, schema or relaxed field comparison is introduced.

## R4 — One new remaining observation

A separate ordinary Advance reconstructs and consumes the exact retained direction.
It uses the direction record as the new candidate source, retaining the original
selected/staged payload and accepted parent. Before any new File/Git effect it
saves `ordered-candidate:<direction-source>:observation-intent`. It never enters
initial inspection, source-history preparation/normalization or staging loops.
It verifies the unchanged resolved history rule and each completed source's saved
history text through the File `readText` DSL, without normalization writes.

Fresh pinned repository/index checks bracket these reads and the shared remaining
candidate observation. Root, branch, HEAD, original continuation-parent, selected
ownership and index tree must remain stable. All Git uses the existing typed DSL
and owning UoW. `IndexTree`/`write-tree` may create Git objects; this new observation
is not described as read-only. No stage, restore/reset, commit, arbitrary argv or
generic Process execution is added.

The actual candidate is saved in the existing
`ordered-candidate:<direction-source>:candidate` namespace, then ordinary native
Validation/checks and independent review proceed. Missing candidate after the new
intent pauses without replay or another same-source authorization. A failed new
attempt preserves its own intent and the old uncertainty. Original history and
stage identities, receipts, accepted work, Plan/base and repair counters remain.

## Executable evidence

`GoalPhaseCandidateRecoverySpec` covers target codec/provenance and passive Human
preparation. `NativeGoalCandidateRecoveryCliSpec` uses an isolated normal SQLite
host, actual typed Git and an existing normal-operation acknowledgement hook at
the final staging result. Native rows are observed read-only, never seeded or
mutated. It covers public pause/describe/prepare/recover, Defer, stale/duplicate
refusal, unchanged completed receipts, one new observation followed by ordinary
checks/review, and nonreplay after a new observation intent. Further examples
refuse changed history policy, changed saved history text and an
unrelated staged file without rewriting completed facts. The optional
`smworkflow.spec.installedCommand` scenario pins the external installed executable
for Goal and preparation operations. All repositories and state belong to the
fixture. Test-owned Human responses are not production consent or real-Session
recovery authority. Parent validation and independent review remain required.

`P116-CONT-01-CANDIDATE` extends the existing completed-effects example. At the
Direction's pending frontier, appending an unused safe candidate path must
refuse through the existing strict observation guard without any observation
intent, native payload, source byte/timestamp, index or check change. The test
restores its original configuration before continuing the existing successful
observation. After candidate observation and one ordinary check reach pending
independent ReviewStep, the same append must reopen the identical review with
the complete native record and payloads unchanged. Moving the original first
candidate path behind the appended path must fail closed with the same retained
facts unchanged. These assertions retain the original Direction, UNKNOWN
outcome and completed prefixes; parent-owned focused validation and independent
review remain required.

## Dependency candidate interruption (CC51-S1)

Recovery binds the exact saved dependency admission, repository/workspace/root/branch,
base, selected paths, Plan/Step/generation and contiguous native effect prefix.
Legacy primary-only targets retain their historical encoding and claim no dependency.
Explicit nulls, foreign bindings, substituted payloads and gaps refuse admission.

### Public target omission and raw JSON admission

The public target and the durable direction have distinct decoding entrances.
Public Command output may omit declared optional members. Decode that omission
to the declared typed `None`; preserve the closed durable codecs and all existing
subject, provenance, prefix and ownership checks. Never supply a missing required
value or infer successful dependency work from an omission.

| Public member | Absent | Explicit null | Present | Wrong type |
| --- | --- | --- | --- | --- |
| Controller `candidate`, `candidateParent`, `phaseReviewedCandidate` | None | Refuse | Decode Snapshot | Refuse |
| Controller `checksReceipt`, `reviewEvidence`, `closureIntent`, `decision`, `repositoryReceipt`, `decisionSource` | None | Refuse | Decode String | Refuse |
| Controller `repairDirective`, `pendingRepair` | None | Refuse | Decode RepairDirective | Refuse |
| Other 11 Controller members | Refuse | Refuse | Decode required type | Refuse |
| Candidate payload `dependencyAdmission`, `dependency`, `dependencyOrigin`, `continuation`, `stepEvidence`, `candidate`, `diff` | None, subject to existing consistency checks | Refuse | Decode declared type and validate | Refuse |
| Candidate `dependencyStaged`; target `dependencyPrefix` | Existing legacy empty vector, never completed-work evidence | Refuse | Decode and validate exact ordered prefix | Refuse |
| Source-history payload `outcome` | None, subject to existing history checks | Refuse | Decode String and validate | Refuse |

The required Controller members are `plan`, `stage`, `cursor`, `generation`,
`repairs`, `base`, `tip`, `completed`, `documentDecision`, `ordinaryRepairs` and
`phaseRecordingPaths`. Unknown Controller members and property-name collisions
refuse. Apply candidate payload rules to inspection, stage intent/result and
dependency-fact payloads, retaining all nested dependency validators.

The existing `PrepareEvidenceOperation` reads the selected request once through
its existing file resource owner. For candidate recovery, validate the raw JSON
target with this same typed public owner before Record conversion and generated
description decoding. Record decoding discards object members with null values;
the later `targetC(Record)` therefore cannot determine whether a missing public
member was originally explicit null. It handles typed omissions but does not
claim to recover lost wire presence. The raw entrance must reject explicit null
and unknown members in closed shapes before that loss occurs. No global Record
policy, new reader, Operation or permission gate is introduced.

The internal Controller codec still requires all 22 keys and accepts its
canonical nulls for optional members. Internal candidate/history legacy Option
nulls retain their previous behavior. Existing dependency extension null
prohibitions stay unchanged. Public compatibility does not relax durable codecs.
Executable specifications must cover normal Command formatting, JSON decoding,
the generated description reader and the real preparation entrance, separately
from historical internal codec roundtrips. Fixture Human answers are not actual
Session consent.
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
