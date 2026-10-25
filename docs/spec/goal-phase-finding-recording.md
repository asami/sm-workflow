# GoalPhase finding recording

Spec ID: `goal-phase-finding-recording`. Since: Oct. 18, 2026. Version: Oct. 19, 2026.
Author: ASAMI, Tomoharu.

Planned extension adopted 2026-10-24:
[recovery observations](recovery-observations-and-cause-resolution.md) require
an explicit operation-sourced Finding producer through common Journal services.
The review-origin wire/provenance rules below do not already implement that
producer. Do not fabricate review observations or issue clerical AI work to record
runtime recovery. Successful recovery leaves cause disposition unresolved until
supported closure. See the [ROBS plan](../notes/recovery-observations-delivery-plan.md).

R1. Actual ReviewStep and ReviewPhase results may carry optional
`reviewObservations`, including an explicitly empty `items` vector. Each
observation has an exact identity matching `[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}`,
category `HYGIENE` or `DEVELOPMENT_CANDIDATE`, and nonblank NUL-free summary and
rationale bounded to 16384 UTF-8 bytes each. At most 128 unique identities are
allowed. Implementation and repair results require absence. Required
`findings`, GoalPhaseFixIssues, quality conditions, counters and adoption keep
their existing independent contracts; observations cannot launder blockers.

R2. The optional Component parameter `sm-workflow.findings.recording` is an
object or JSON string with exactly `schemaVersion`, `hygiene` and
`developmentCandidate`. The schema is
`sm-workflow.finding-recording-policy.v1`. Both destinations use the existing
repository-relative-path contract and must be explicit literal entries in every
selected Plan Step's `ownedPaths` and the host's fixed candidate paths. Native
recording also requires the literal case-sensitive `.md` suffix. Both
categories may use the same file. No prefix expansion or command/environment
configuration is provided by this contract.

R3. A nonempty finite recording Request assigns at most 256 Items. The ordinary
business key is `(sourceReference.identity, sourceReference.revision,
observation.identity)`, without hashes or transport identities. Each Item names
its exact destination. ReviewStep Items name the last completed Step;
ReviewPhase Items have no Step identity and require all selected Steps completed.
Completed Steps must be the ordered unique Plan prefix, with 40-character
lowercase hexadecimal commit/head/tree identities and parent chain beginning at
the original base. References, metadata and section identities are exact nonblank
control-free strings bounded to 1024 UTF-8 bytes. These input checks never prove
the actual native frontier, source or history. No-items input rejects; the native
owner will skip recording issuance when nothing is accumulated.

R4. Each ItemResult repeats its assigned source key, reports `recorded`,
`partial` or `failed`, and explains its actual disposition. Recorded and partial
require one or more unique `(file, section)` records, bounded to 128; failed
requires none. Each file must equal that Item's assigned destination exactly;
sections are nonempty and control-free. Results must return exactly the assigned
keys in order with no missing, duplicate, extra or crossed source. The overall
outcome is `completed` iff all items were recorded, `failed` iff all failed,
and otherwise `partial`, including mixed recorded/failed and any partial item.
Summary and item rationales are nonblank NUL-free text bounded to 16384 UTF-8
bytes. A partial/failed result adds no retry, adopted work, task or counter.

R5. Pure PayloadCodecs use distinct type identities
`GoalPhaseFindingRecordingRequest` and `GoalPhaseFindingRecordingResult`, with
schemas `sm-workflow.goal-phase-finding-recording-request.v1` and
`sm-workflow.goal-phase-finding-recording-result.v1`. Decode validates business
contracts. Optional fields may be absent and must never be explicit null.
Public observation projections preserve malformed nulls for typed rejection.
Absent `reviewObservations` preserves the existing result wire and evidence.
Present observations survive public preparation, independent review verdict
binding, and Human question display/binding: changed content requires different
actual prepared evidence. Source references confer no authority.

Examples E1–E6 in `GoalPhaseFindingRecordingSpec` cover actual review admission
separate from blockers, invalid/absent wire and null projections, explicit policy
destinations, valid finite payloads, truthful partial/failure, and exact ordered
source/destination rejection. E11 in `NativeHostReviewPreparationSpec` covers
public preparation and independent evidence binding of observations.

R6. `goal-phase-business-operation-v7` adds the `findingRecords` constituent,
subject `findingBatch:GoalPhaseFindingRecordingRequest`, in `Retained` at all
13 existing Phase combinations and INITIAL. Existing progression names and
counts stay unchanged. The sixth Required SPI is
`goal-phase-finding-recording-capability`, action `RecordGoalFindings`, operation
`GoalPhaseFindingRecordingService.recordGoalFindings`. The fifth Goal Provided
operation is `GoalPhaseService.submitGoalPhaseFindingRecording`, making 17
normal three-workflow operations. Public CML request/result values are ordinary
typed DTOs; the internal payload codecs remain distinct mechanical encodings.
No old-instance migration, V6 alias or fallback is supplied. Existing V6 actual
evidence keeps its original scope. New normal cases use fresh isolated V7
Sessions; the orchestration Goal is not a product Session.

R7. Native contiguous reconstruction accumulates observations only from actual
accepted ReviewStep/ReviewPhase completions. Source reference is the actual
completion's `resultReference`, never worker-supplied attribution. Order is
preserved; different references retain recurrences separately. Duplicate or
conflicting `(source identity, revision, observation ID)` keys refuse admission.
Mixed required/nonblocking reviews retain their observations through genuine
repair and re-review without issuing recording before the real reviewed Step
commit. The safe Step frontier is the next Implementation or PhaseInspection
with every pending ReviewStep item belonging to the last completed Step. The
Phase frontier is PhaseClosing after accepted PhaseReview and before closure
intent. Recording precedes document refresh, progress and successor work.
Empty accumulation creates no suspension or counter event. Missing policy or
more than 256 accumulated items returns truthful Wait with all typed pending
items intact; no subset, split, silent fallthrough or completion is invented.

R8. A fresh finite Request captures the current full Plan, original base,
completed prefix, Step scope, actual issues and repair snapshot. Its identity
is `<instance>:ordered-recording:<actual-source-revision>`. Current policy is
resolved once through ordinary Component initialization declarations; its two
literal destinations must satisfy Plan and fixed-host ownership. Category
selects the assigned destination deterministically. Invalid configuration or
scope refuses before issue. Retained requests validate captured literal
ownership and actual source continuity, consuming their captured destinations
without re-resolving or comparing newer policy. No prefix expansion, section
scanner, command-line/environment parser or document-source classification is
introduced.

R9. Native issuance binds the existing execution profile once with distinct
recording capability, SOURCE_IMPLEMENTATION risk, Standard reasoning and no
review requirement for clerical recording. Public mode is Implementation.
The native canonical suspension/completion owns CAS, participant and execution
evidence. Contracts are `goal-phase-finding-recording-context-v1`,
`goal-phase-finding-recording-completion-v1` and
`goal-phase-finding-recording-evidence-v1`. Named Submit parameters are
`sessionId`, `result:GoalPhaseFindingRecordingResult`, `resultReference`,
`completionFacts*` and optional `executionEvidence`. The distinct
`GoalPhaseFindingRecordingSubmissionResult` returns actual committed
session/accepted/outcome/message through the normal after-commit binding.
Submit validates exact assigned Request/result keys and destinations against
the canonical retained Start, participant admission, declaration scope,
contiguous actual history and issued identity/run/revision/SPI/operation.
Submit reconstructs this retained source without observing the live Git root
or branch. It runs no File, Git or SBT effect and calls no eager Advance;
actual Git observation and managed effects belong to separate ordinary Advance.
Its history
retains the same controller stage with actual result causation. Only later
ordinary Advance removes the completed batch's pending keys and appends its
actual Request/reference/Result once. Public Request, Human, Wait, child and
Terminal projections retain pending and completed recording state. Valid
partial/failed outcomes are terminal batch dispositions, not a claim that all
items were recorded; no retry, adopted work, repair count or refund follows.

R10. Recording work edits only the exact selected current Markdown documents:
read each file, preserve prior content, and append exact assigned source
identity/revision, observation ID, summary and rationale under meaningful
sections. Return honest saved file/section/outcome per item. Missing files,
partial edits and failures remain truthful. No optional implementation adoption,
counter/reset, command execution or recursive review belongs to this task.
The component skill describes this same ordinary Request and distinct Submit.

R11. Ordered unique destinations from actual completed recording Requests grant
bounded candidate inclusion even for honest partial/failed replies. They stay
separate from `documentPolicy`. Before Phase review, the exact management
allowance is actual document-progress paths union retained recording paths;
whole-candidate dirty, selected/staged paths and delta must fit that union with
normal root/branch/head/tree/index/unmerged/diff checks. Every recording path
is literal `.md` owned in every Step and the fixed host. Journals never become
`GoalPhaseDocumentSource`.

After accepted PhaseReview, only actual phase-scope completed recording items
grant final staging paths. The native recording-candidate owner saves inspection,
each stage intent and successful result, then actual final candidate under
`GoalPhaseNativeRecordingCandidateReceipt` /
`sm-workflow.goal-phase-recording-candidate.v1`. References use
`<instance>:ordered-recording-candidate:<source-revision>:<key>`. All actual
dirty paths and rename origins must fit this Phase batch's assigned paths.
The initial index equals the reviewed tree; the actual head/tree remains the
completed-Step tip. Fixed existing typed Git staging uses the host's path
ordinal. PendingPaths/PendingDiff compare actual index against the reviewed
tree object. Final changed paths fit assigned and actually staged paths; no
unstaged/unsupported paths, binary or oversized diff is accepted. Unexpected
edits fail visibly without overwrite. Unknown/partial intent remains visible
Wait with no replay; failed Git results preserve their actual intent.

An unchanged final index retains a successful no-change receipt and no new
controller event. A changed index applies `PhaseFindingRecordsObserved` only
at PhaseClosing before closure intent with existing candidate equal to the
reviewed snapshot, accepted checks/review present, the same completed-Step head,
a different tree and finite unique shared literal Markdown paths. It sets
`phaseReviewedCandidate` and `phaseRecordingPaths` and replaces only the final
candidate, preserving Plan/base/prefix/generation/counts/directives and accepted
checks/review. Duplicate observation refuses. Nonclearing transitions and
refresh retain both candidates; new candidate/work/Step resets clear them.
This supplies no changed-source review or new check acceptance.

R12. Repository closure retains the original accepted review reference and
optional `reviewedCandidate` separately from its final actual management
`candidate`. Authority bindings include the optional source only when present;
ordinary `reviewed_candidate` aliases normalize through passive preparation.
Canonical scope selects its actual head/tree and rejects explicit null, scalar
or incomplete present snapshots. Absent historical output stays absent. A grant
for absent/different source or final candidate cannot authorize another target.
The child consumes the final actual index through the existing bounded closure
preconditions, while Terminal's reviewed source remains the original accepted
snapshot. No new review, fabricated receipt or remote-effect grant is implied.

`GoalPhaseNativeFindingRecordingSpec` connects normal Start/Advance, actual
managed checks and reviewed Step commit to one finite batch, immutable pending
issuance across host policy changes, public destination/key refusal, typed
after-commit acknowledgement, actual retained outcomes, stable missing-policy
and oversized Wait, honest partial/failed outcomes, final Phase Markdown staging,
two-candidate authority Wait, bounded local child closure and no-replay/error
behavior for unknown staging/unassigned edits. Its reopened canonical Session
regression denies every declared Git capability for completed Step and
partial/failed Phase replies: malformed destination/source replies leave history
unchanged, valid Submit appends only its completion, and separate Advance fails
under the same denial. Reopening with ordinary grants permits the existing next
frontier while retaining exact batches, Plan/base/completed prefix and counts
without duplicate recording or SBT replay. All reviewers, authority and file
edits in that specification are explicitly SPECIFICATION-OWNED. Pure controller
and authority preparation/scope specifications cover preservation and target
negatives; the actual Scala2.12 CML probe checks all 13 combinations and typed
Required/Provided contracts. These are executable expectations, not claims that
validation ran. Actual installed-skill AI file editing is the subsequent R3
execution on this route; genuine AI effects and the existing independent Step
acceptance are still required before a connected-delivery claim.

R13. The explicitly selected `NativeGoalFindingRecordingLiveSpec` supplies the
bounded R3 ordinary CLI procedure. It is `@DoNotDiscover`; its explicit
`runtimeTestOnly` allowlist entry selects the suite without changing ordinary
discovery counts. Each invocation selects exactly one of `prepare`,
`after-implementation`, `after-review`, `after-authority`, or `after-recording`
using `smworkflow.spec.p62.recording.stage`. The parent prepares the current
runtime/package through the existing normal `runtimeTestOnly` task and supplies
the real normal executable in `smworkflow.spec.sbt`. For example, the parent
submits this ordinary SBT selection through the registered serialized runner,
with the two properties set for that invocation:

```text
-Dsmworkflow.spec.sbt=/absolute/real/normal/sbt
-Dsmworkflow.spec.p62.recording.stage=prepare
-Dsmworkflow.spec.p62.recording.output=/absolute/primary/target/sm-workflow/work/fresh-stage-output
runtimeTestOnly org.simplemodeling.textus.smworkflow.admission.NativeGoalFindingRecordingLiveSpec
```

`smworkflow.spec.p62.recording.output` is required for every stage: an absolute,
normalized, fresh ignored directory below the primary repository's
`target/sm-workflow/work`, whose real parent already exists. Every saved business
output uses `CREATE_NEW`. Subsequent stages additionally require
`smworkflow.spec.p62.recording.root`, the actual normalized direct child of
`target/sm-workflow/work/native-cli` returned by `prepare`. Their invocation-log
ranges begin at 1000, 2000, 3000 and 4000 respectively. Reopening reads ordinary
host/configuration, raw Plan/input and canonical task routing files, validates
the dedicated selection and retains all configuration, including the selected
prepared import and SBT environment. These are semantic input checks, not
full-content stability certificates. The driver never reads Workflow SQLite,
status, history or private storage.

The dedicated fixture owns exactly `value.txt`, `docs/usage.md`,
`docs/journal.md` and `docs/candidates.md` as both Step ownership and fixed
candidate paths. Raw Plan `native-finding-recording-plan` revision `1` has only
`step-1`, Phase `verification-p62-recording-live`, repository
`smwf-p62-cli-recording`, repair limit 2, and ordinary `test` for Step and Phase.
There is no document/progress binding. Before Start, fixed fixture setup creates
and locally commits nonempty usage, hygiene and candidate baseline narratives;
normal Start retains that actual source base. Implementation must set
`value.txt` to exactly `expected` plus one LF and append ordinary usage to the
existing usage narrative, leaving both journals at baseline. The existing
ProductCheck owns the actual Test. The one closure program uses the fixed
message `Complete selected continuation Step 1`; later Step commit belongs to
the ordinary typed UoW, never to the driver.

The finite lifecycle is:

1. `prepare` creates the dedicated fixture and `finding-recording-live-task`,
   runs ordinary Start, retains its actual `workflow-session.json`, and explicitly
   Advances to the actual `ImplementStep`. It saves the original Plan/base at
   repair progress 0/0/2 and checks that there is no Test PID. It creates no
   semantic result and makes no actual implementation edits.
2. `after-implementation` requires
   `smworkflow.spec.p62.recording.result` and
   `smworkflow.spec.p62.recording.reference`: real regular JSON files within
   the selected root containing the external `GoalPhaseWorkResult` and
   `WorkflowArtifactReference`. The supplied result must be `completed` and the
   actual value/usage/journal files must satisfy the assignment. Advance first
   redisplays the saved initial request. Its issued named Submit accepts the
   supplied result; only a separate explicit Advance runs normal Test and issues
   actual `ReviewStep` with candidate and successful typed receipt. A repeated
   Advance retains the identical request with exactly one actual check PID.
3. `after-review` requires
   `smworkflow.spec.p62.recording.preparation`, a real
   `WorkflowEvidencePreparationInput` JSON file within the root. Its request URI
   selects the canonical saved ReviewStep; result, reference and executionEvidence
   URIs select the actual external review files within that root. Ordinary
   `prepareEvidence`/import and named Submit consume the unchanged supplied
   result and execution evidence, preserving all actual findings, observations
   (including an empty set), limits and provider facts. A separate Advance saves
   actual next work. Actual `accept` must yield `StepClosure` Wait with the
   reviewed candidate. Repair, decision-needed or other nonacceptance stops this
   finite stage at its actual frontier; the parent routes further work without
   an authority or recording expectation.
4. `after-authority` requires the same `preparation` property, now selecting an
   external `WorkflowAuthorityPreparationInput` JSON file. Its request URI names
   the actual saved Wait and its reference URI selects a nonempty external
   reference within the root. Ordinary `prepareAuthority`/import and explicit
   Advance perform the real local Step commit. The returned closed prefix must
   retain the original base as parent and the reviewed candidate tree. Genuine
   nonempty actual review observations must produce the finite recording
   Request on the distinct `submitGoalPhaseFindingRecording` route, with exact
   source reference/order, category destinations, closed Step, Plan/base and
   progress. Repeated Advance issues no extra Test. With zero observations,
   retain actual normal ReviewPhase and a truthful `recording-issued:false`
   report; that branch does not prove genuine R3 editing and does not run the
   after-recording stage.
5. `after-recording` requires the `result` and `reference` properties again,
   selecting the actual external `GoalPhaseFindingRecordingResult` and
   `WorkflowArtifactReference`. It reads the saved public
   `findingRecording.request`, applies existing pure request/result admission
   for exact ordered keys/destinations and honest outcomes, and inspects the
   returned recorded/partial sections in assigned real files. The baseline
   narrative and actual source identity/revision/observation ID must be present;
   recorded items must include the actual observation summary and rationale.
   Failed items require no invented section or edit. Pending Advance must equal
   the saved request. Only its distinct named Submit consumes the supplied
   result; the typed acknowledgement must accept in the same Session and
   report the actual disposition, without eager Advance or another Test PID.
   A separate explicit Advance retains the exact Request/result/reference once,
   clears pending/request, preserves Plan/base/closed commit/progress, and
   performs normal Phase inspection/Test to actual ReviewPhase with candidate,
   receipt and bounded inclusion of assigned journals. Exactly two check PIDs
   exist; repeated Advance adds none. Stop before any Phase review result,
   final authority or closure.
6. `verify-recorded`, fixed log range 7000, verifies an already consumed
   recording Submit using only the actual saved initial, recording and next
   public handoffs and the external `result` and `reference` files. Exact
   request/result admission and saved journal sections remain required. Exactly
   the Step checks plus one Phase check must already have distinct real child
   PIDs. Two ordinary Advances must each equal the saved next handoff and
   preserve those PIDs. The shared post-recording assertions require the exact
   retained Request/result/reference once, empty pending and absent request,
   unchanged Plan/base/closed Step/progress, bounded journal inclusion and
   successful ReviewPhase receipt. This stage performs no Submit, preparation,
   authority import, new Test or closure. It neither reconstructs storage nor
   replaces missing handoffs; `after-recording` is not a recovery route for an
   already consumed Submit. Every output remains create-new.

Public handoffs are retained at the scenario root as
`finding-recording-{initial,review,wait,recording,next}.stdout.json` when their
branches are reached. The actual external review preparation is also retained
as `finding-recording-review-preparation.json`; it continues to reference the
actual external result/reference/execution files. Each fresh stage output saves
root/task/session metadata, actual invocation argv/PIDs/stdout/stderr, and actual
SBT check PIDs from the existing ProductCheck marker. Review and recording stage
reports state actual branching facts. Retain these outputs together with the
external installed-skill provenance and actual returned files.

The parent supplies task-private installation of the current built skill bundle
and real external implementation, independent review and recording workers.
This procedure neither installs a global skill nor invents any result, verdict,
observation, quota, reviewer/provider metadata, authority or receipt. Passive
preparation alone is not a genuine review claim. R2's specification-owned
no-effects/history/Git proof keeps its separate scope; this R3 transport adds no
raw Git verification engine. A required repair or product failure remains
visible for the parent's separate frozen repair route, without fallback, reset
or implicit retry. This procedure specifies expected evidence only: no run,
genuine AI work, validation success, independent Step acceptance or Phase
closure is claimed by adding the fixture, suite or documentation.

R14. The same finite driver additionally selects the explicit
`smworkflow.spec.p62.recording.example=semantic-maintenance` variant. Absence
or a blank value retains R13; every other nonblank value rejects. Selection is
passed explicitly to fresh/reopen fixture helpers, never inferred from source
content or the latest root. Its fresh root prefix is
`p62-semantic-recording-live`; canonical task routing and
`finding-recording-live-task` remain the same. Host ownership, every Step and
typed Git selected paths contain R13's four paths plus the literal
`src/test/scala/ProductCheck.scala`. Raw Plan
`native-semantic-recording-plan`, revision `1`, Phase
`verification-p62-semantic-recording-live` and repository
`smwf-p62-cli-semantic-recording` retain one Step, Step/Phase `test`, repair
limit 2 and the same fixed closure message. Its purpose requires exact
`expected` plus LF, accurate usage preserving the original introduction and
meaning-correct ProductCheck narration/comments with unchanged check behavior.
Journals remain untouched until distinct assigned recording work.

Before Start, fixed fixture setup replaces only the existing test-owned
ProductCheck source and includes it in the baseline narrative setup commit.
The original usage and journal introductions remain unchanged. ProductCheck
retains its marker path, actual PID write, entered/passed markers, exact
`readString(value.txt) == "expected\n"` assertion, failure behavior and existing
main/test wiring. Its printed Given claims CRLF input; When describes the
ordinary read; Then claims either LF or CRLF is accepted. An adjacent comment
claims normalization before matching although the expression performs none.
These descriptions are deliberate specification-owned counterexample inputs,
not product bugs or AI-authored findings. The actual independent reviewer owns
any verdict and findings; a separately assigned actual repair editor owns
description/comment corrections. The driver neither compares source text shape
nor fabricates semantic output, review observations or workflow history.

The R13 stages and transport remain, with these finite semantic extensions:

1. `prepare` and `after-implementation` use ordinary Start, named Submit and
   separate Advance. The separately frozen initial implementation authors only
   value and usage; the fixed narration remains review input. The actual first
   Test succeeds with one child and saves `finding-recording-review.stdout.json`
   at progress 0/0/2. `after-review` retains the actual independent preparation
   once as `finding-recording-review-preparation.json` and consumes the complete
   unchanged result, including optional observations and empty arrays. Actual
   accept follows ordinary StepClosure Wait. An actual repair saves
   `finding-recording-repair.stdout.json` with progress 1/1/2, one prior child,
   the same Plan/base/Step, a nonempty review and absent check failure. This
   REVIEW_FIX route has the actual issue source literal `REVIEW`, with assigned
   identities and history. Other actual nonacceptance stops at its returned
   frontier without downstream authority or recording assumptions.
2. `after-repair`, log range 5000, is semantic-only. Normal Advance must equal
   the saved RepairStep. External result/reference must report completed work,
   cover every assigned issue exactly once as `FIXED`, and carry the actual
   explanation-only fix assessment: `HYGIENE`, with requirementChanged,
   contractChanged, crossFileLogic and minorBugFix all false. The driver checks
   exact value, preserved usage introduction and both unchanged journals; it
   does not prove semantic accuracy from source shape. Issued Submit performs
   no eager check. Separate Advance performs the second typed normal Test and
   saves `finding-recording-rereview.stdout.json` at progress 1/0/2. Actual
   REVIEW issues remain OPEN with latestDisposition FIXED, their original
   evidenceReference and unchanged history. A successful Test does not resolve
   an independent semantic finding. Repeated Advance retains that ReviewStep
   and exactly two child PIDs.
3. `after-rereview`, log range 6000, is semantic-only. External independent
   preparation must name exactly `finding-recording-rereview.stdout.json` and
   is retained separately as `finding-recording-rereview-preparation.json`.
   Pending equality, Plan/base/corrected candidate, progress 1/0/2 and two
   checks remain required. The actual report explicitly covers every issued
   REVIEW issue; actual accept requires complete RESOLVED dispositions without
   finding ordinals and no required findings. The driver passes the result
   unmodified through ordinary prepareEvidence/import and issued Submit.
   Separate Advance saves the accepted StepClosure Wait at the existing wait
   handoff; any other outcome saves
   `finding-recording-rereview-next.stdout.json` and stops. Repeated Advance
   retains the actual frontier without another Test. No intended verdict or
   observation quota is supplied.
4. Semantic `after-authority` consumes that actual accepted rereview, its saved
   Wait and fresh external authority through the existing route. Pending Wait
   retains progress 1/0/2 and two prior checks. Only native typed Git closure
   commits the actual rereview candidate, with original base as parent, then
   resets progress to 0/0/2. Recording items accumulate complete actual original
   review and rereview observations in order, each with its own actual
   resultReference, sourceKind ReviewStep and Step1 scope. Nonempty assignment
   retains the completed Step and actual post-acceptance RESOLVED required
   issues: same identities/order/source/repository/Step/summary, RESOLVED latest
   disposition, new nonblank evidence and the prior history prefix followed by
   actual RESOLVED transitions. The earlier OPEN handoff cannot stand in for
   this state. Duplicate or conflicting keys fail owning admission visibly.
   Empty accumulation truthfully issues no recording task and reaches ordinary
   ReviewPhase after the third Test.
5. Semantic `after-recording` uses the same distinct recording Submit, exact
   key/destination/section checks and truthful completed/partial/failed support
   as R13. There are two children before Submit and three after separate Phase
   Test. The completed Step, accepted candidate, resolved issues, selected
   Plan/base, reset 0/0/2 and actual retained batch remain current; repeated
   Advance adds no child or replay. The ordinary example still expects one
   child before recording and two after Phase Test.

Required issue records and their complete transition history must equal the
recording Request's captured `requiredIssues` exactly after Phase Test. The
latest movement describes the current evidence boundary rather than cumulative
resolved history: a successful Phase validation with no issue transitions has
the current `work.checksReceipt` as evidence, zero open/resolved/new/reopened
counts and known correspondence. It does not retain the earlier rereview's
movement. Both `after-recording` and `verify-recorded` enforce this separation
for reviewed-repair examples while preserving the full RESOLVED records and
history. `verify-recorded` requires three existing check PIDs for these examples
and adds none.

Both actual independent review results retain their own provenance and complete
optional observations, including observations accompanying the repair outcome
and empty arrays. All saved outputs are create-new; actual invocations and child
PIDs use the existing driver. No storage seeding, history reconstruction,
alternate process route, additional engine, recursive review, forced finding,
primary acceptance or new run is implied. R13's ownership, source attribution,
security, no-items and honest failure limits continue to apply. Adding this
variant claims no actual execution or independent acceptance.

R15. The same explicit finite driver additionally accepts
`smworkflow.spec.p62.recording.example=security-contract`. Absence/blank keeps
R13, `semantic-maintenance` keeps R14, and every other nonblank value rejects.
The explicit fresh/reopen security helpers share the existing setup and stages;
they never infer selection from repository contents. The fresh root prefix is
`p62-security-recording-live`, raw Plan `native-security-recording-plan` revision
`1`, Phase `verification-p62-security-recording-live`, and repository
`smwf-p62-cli-security-recording`. Ownership and typed Git selection use exactly
R14's five literal paths, one Step, repair limit 2, Step/Phase `test`, canonical
task and the fixed closure message.

The adopted Step requires exact `expected` plus one LF, accurate ordinary usage
preserving its introduction, and unchanged ProductCheck check behavior. Its
narration/comments and usage must accurately explain that CNCF SBT registration
authorizes only the declared operation: registration alone grants neither
arbitrary filesystem/network authority nor bypass of independent File/HTTP
grants. Ordinary `git_`/`sbt_` effects run in CNCF UoW; generic Process/shell
fallback must not be recommended. The threat illustrated is misleading
cross-effect authorization guidance. This is documentation and contract
composition, with no actual bypass, network access, scanner, application
authorization engine or new executable feature. Journals stay at baseline
until distinct assigned recording work.

Before Start, the same baseline setup commit includes a fixed test-owned
ProductCheck counterexample. Its Given says `A registered SBT program grants
arbitrary filesystem and network authority`; its When describes the ordinary
read; its Then says `The registered command may bypass independent File and
HTTP grants`. The adjacent comment says `Registration alone authorizes those
other managed effects.` These false guarantees are specification-owned input,
never execution authority or authored review findings. A baseline local
`workJson` holds the normalized absolute Paths.get root, used by `val root =
workJson`; that harmless old naming is outside adopted cleanup and imposes no
observation or verdict requirement. Marker location, actual PID write,
PRODUCT_CHECK_ENTERED/PASSED, exact real value assertion, failure behavior and
normal test/main wiring retain R14's meaning.

R14's finite reviewed-repair stages and counts apply unchanged: actual REVIEW
RepairStep at 1/1/2, exact external FIXED issue coverage with explanation-only
HYGIENE and all four change flags false, normal second Test and independent
rereview at 1/0/2, explicit RESOLVED on actual accept, StepClosure authority,
typed Git commit of the accepted candidate and reset to 0/0/2. A substantive
non-HYGIENE assessment stops truthfully for separate parent planning. The real
reviewer owns accept/repair/other outcomes, findings and optional observations;
the separately assigned editor owns any repair. No finding, category, identity,
quota or intended outcome is supplied by setup or source-shape assertions.
Complete actual review and rereview sources, including empty observations and
observations accompanying repair, accumulate in order with their own provenance.
Actual nonempty observations use the distinct recording route with exact keys,
destinations and honest outcomes; empty accumulation retains truthful no-items.
There are two checks before recording and three after Phase Test, without replay.
All actual result, authority and destination checks remain required.

This variant reuses existing producer guarantees and selects only changed
misleading contract explanation; it adds no producer retest or security review
campaign. Adding it claims no live run, nonempty recording, security enforcement,
primary acceptance or universal security PASS. Original integrated P62-RECORD
acceptance and the sole full Phase review remain required.
