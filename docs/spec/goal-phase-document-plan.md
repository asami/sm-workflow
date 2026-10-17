# GoalPhase explicit document plan

`GoalPhaseDocumentPlan.decodeC(phaseDocument, checklistDocument)` imports explicit
in-memory Markdown into an existing `GoalPhaseOrderedProgression.Plan`, ordered
Step bindings and selected checklist items. This is a data decoder. The host
selects source paths and authority; this decoder neither reads those paths nor
authenticates sources. Normal GoalPhase Start binds an explicitly selected source
and acquires its current documents as described below. Issued semantic requests
carry the selected acceptance into their input and context. Ordinary Advance also
selects current documents at each unissued business frontier through the existing
native workflow instance.

## Explicit source selection and normal Start

The existing `prepareGoalStart` operation accepts its same eight fields. Its
explicitly selected `plan` input may contain this complete source declaration:

```json
{
  "schemaVersion": "sm-workflow.goal-phase-document-source.v1",
  "phasePath": "docs/phase.md",
  "checklistPath": "docs/checklist.md"
}
```

These are distinct literal workspace-relative filenames, validated through
CNCF `WorkspaceFilePath.parseC`. Absolute paths, traversal, URI sources, missing
or null values and extra declaration fields are rejected. No workspace selector,
host root, command or permission can be supplied. Any JSON object containing
`schemaVersion` must be a valid source declaration; an invalid declaration never
falls back to a legacy Plan.

Preparation reads only the selected business input and declaration files through
its ordinary reader. It does not read the Phase or checklist, authenticate the
selection, admit a Plan, start execution or grant File access. The normal prepared
import contains exactly the existing profile-selection row and a plan row with
`id` and `selectedPlanPayload`. The latter uses the explicit prefix
`sm-workflow.goal-phase-document-selection.v1:` followed by:

```json
{
  "schemaVersion": "sm-workflow.goal-phase-document-selection.v1",
  "payload": {
    "selectionIdentity": "selected-phase",
    "participantIdentity": "participant",
    "source": {
      "phasePath": "docs/phase.md",
      "checklistPath": "docs/checklist.md"
    }
  }
}
```

Each object has exactly the fields shown. Identities must be exact nonempty
strings. This is passive datastore input: no copied documents, hashes, integrity
certificates or execution authority accompany it. The existing typed importer
owns installation into the explicitly selected collections. The public operation
fields, CML prepared-row type and storage field are unchanged.

On ordinary GoalPhase Start, `GoalPhaseSelectedPlanDataStorePort` reads that field
within the current owning call and dispatches only on the exact document-selection
prefix. It checks the stored selection identity against the requested identity
before document acquisition. `GoalPhaseDocumentSource.loadForCallC` then reads both
declared filenames through `WorkflowFileExecution.readTextC`, using a CNCF
`FileTarget` for the fixed logical workspace `sm-workflow-repository`. CNCF owns
workspace/root binding, access, path resolution, cwd and read limits in the current
UoW. The document selection supplies none of them. There is no direct filesystem,
resource-reader or process bypass in this product path. Missing files, denied
reads and malformed document Plans return their actual failures without fallback.

The fresh decoded Plan goes through the existing ordered Plan validation,
acceptance capture, selected-participant and Phase/repository scope checks, and
current clean native Git root/branch admission. Durable native Start captures the
original Git base, admitted Plan and optional `documents` data: original source
filenames, ordered stable bindings and selected checklist items with semantic
acceptance text. This data grants no receipt or execution authority. Checked
items remain presentation and do not become completed work or evidence. Initial
Advance starts from that actual Start and original base, reconstructs actual
contiguous native history, and selects current documents before fresh dispatch.

Schema-less explicit Plan JSON retains its previous preparation, storage prefix,
semantic admission and Start route. Legacy selected Plan and native Start payloads
omit `documents` on encoding and decode absent `documents` as `None`; their schema
versions and original meaning are unchanged. Unknown storage prefixes fail.

The live connection uses that same explicit source and pure compatibility policy
at actual unissued business frontiers. A changed selection is one canonical
mechanical business-input transition in existing workflow storage. The Git-only
operation examples below specify acquisition and Human decisions. They do not
establish full B2 or Step acceptance: the complete between-Step Git/SBT/commit
live-plan proof remains part of the runtime accumulator, latest-document progress
writeback and tracked-document Git ownership remain C, and failed-check correction
remains COMMAND.

## Captured semantic input and context

`GoalPhaseNativeWork.Request.documents` is an optional final business-input field
in the unchanged request v2 format. Fresh work uses the current selected Plan and
Bound documents at its actual unissued frontier. The actual persisted issued-row
selection remains authoritative:
reconstructing a request does not replace an old issue's input or context with
later document data. The existing input codec carries the captured value through
the canonical issued WorkOrder context without adding a public Operation field.
Absent or JSON-null legacy documents decode as `None`; encoding `None` omits the
field and leaves the existing review/decision-context omissions unchanged.

`GoalPhaseDocumentSource.instructionsC(documents, stepIdentity)` is a pure
projection. For a selected Step it requires exactly one matching stable binding
and includes only that binding's stable item IDs and semantic acceptance, in
membership order. With no selected Step it includes all declared bindings in
binding order. Checkbox values do not appear in the instructions or change them.
The appended text explicitly grants no test, review, command or commit authority.
It follows the existing effective-acceptance instructions in the ContextBundle;
captured business input is not execution evidence.

Null options, null captured values/vectors/members, invalid source paths or stable
IDs, empty membership, duplicate Step bindings, repeated item references, missing
or ambiguous selected item data, extra item identities and empty acceptance fail
with a `Consequence`. The captured item ID set must exactly match its declared
references. An invalid selected Step fails instead of silently dropping context.
No documents means an empty addition and preserves the previous context text.
Source paths remain data: this projection performs no IO, executable-command
parsing, progress inference, permission acquisition or writeback. The captured
value proves neither current source identity nor unchanged current content.

## Phase declaration

The Phase document must contain exactly one top-level block whose opening line
is exactly three backticks followed by `sm-workflow-plan`, with no indentation or
surrounding spaces. Its closing line is exactly three backticks. LF and CRLF
documents are supported without rewriting the supplied source. Other fenced
examples, including selected-looking blocks inside backtick or tilde fences,
have no declaration semantics. Missing, duplicate or unclosed selected blocks
are invalid input configurations.

````markdown
```sm-workflow-plan
{
  "schemaVersion": "sm-workflow.phase-plan.v1",
  "plan": {
    "identity": "phase-plan",
    "revision": "1",
    "phase": "PHASE-6.1",
    "repository": "sm-workflow",
    "steps": [
      {
        "identity": "S1",
        "purpose": "Decode the selected input",
        "ownedPaths": ["src/one.scala"],
        "dependencies": [],
        "checks": ["focused-one"]
      },
      {
        "identity": "S2",
        "purpose": "Connect the selected input",
        "ownedPaths": ["src/two.scala"],
        "dependencies": ["S1"],
        "checks": ["focused-two"]
      }
    ],
    "phaseChecks": ["whole-phase"],
    "repairLimit": 0
  },
  "bindings": [
    {"step": "S1", "items": ["P61-A", "P61-B"]},
    {"step": "S2", "items": ["P61-C"]}
  ]
}
```
````

The envelope has exactly `schemaVersion`, `plan` and `bindings`. Every binding
has exactly `step` and `items`; bindings are array rows so duplicate Step rows
cannot disappear through Map construction. `plan` uses the existing Plan JSON
decoder and `validatePlanC`, including acceptance, work classification, unique
Steps and earlier-only dependencies. The Plan engine and its validation rules
are unchanged. Optional Plan fields follow that existing decoder.

Each Step must have exactly one binding, with no extra or missing Step. Each
binding has at least one item ID, and every referenced item ID is unique within
and across bindings. Step binding and item member IDs match
`[A-Za-z0-9][A-Za-z0-9_.-]{0,127}`. Every referenced item must exist exactly once
in the checklist. Returned bindings follow Plan Step order and retain the item
order within each binding. Returned items follow that same membership order.

## Checklist acceptance

Actual supported items are unindented bullets with one of these forms:

```markdown
- [ ] P61-A: Decode the explicit Plan
  with stable membership.

  Preserve the declared acceptance paragraphs.
- [x] P61-B: Keep checkmarks presentation only.
- [X] P61-C: Connect selected source data.
```

The first-line text and trimmed indented continuation lines are joined with
single spaces and trimmed. Blank lines may separate acceptance paragraphs.
Unrelated non-indented narrative, headings or another bullet end the current
item. Fenced code examples are ignored. Indented checkbox-looking text is
continuation text rather than another supported item. Unsupported bullet forms
cannot satisfy a reference. Repeated stable IDs in actual supported items fail,
including unselected items. Referenced acceptance must be nonempty; an item can
supply it entirely through continuation lines. Unreferenced items may remain in
the shared checklist and are omitted from the returned selection.

`checked` is presentation-only data. Otherwise identical checked and unchecked
documents produce identical Plan, binding and semantic acceptance values. A
checkmark supplies no closed/completed state, receipt, evidence, authority,
counter or command. Narrative outside the selected declaration and supported
items supplies no Plan semantics; purposes and selected checks come only from
the explicit Plan.

Malformed JSON, unsupported schemas, null documents, invalid Plan contracts and
contradictory membership return clear failed `Consequence` values. The decoder
does not perform file/process/tool operations, source authentication, progress
acceptance, runtime migration or workflow execution.

`GoalPhaseDocumentPlanSpec` provides Given/When/Then behavioral examples for the
two-Step import, acceptance continuations, explicit input addition/reordering,
checkbox equivalence, membership and Plan failures, closed JSON objects,
selected fence selection, fenced examples, CRLF and null input. These pure
examples establish no caller integration or execution evidence.
`GoalPhaseDocumentContextSpec` specifies Step and whole-Phase selection order,
checkbox neutrality, malformed/ambiguous captured data, explicit source/acceptance
codec retention and absent/null legacy compatibility with should matchers.
`GoalPhaseDocumentSourceSpec` covers passive declaration/storage validation and
legacy optional-field compatibility. `NativeHostGoalStartInputSpec` covers the
same two-row preparation route without document acquisition or invalid-declaration
fallback. `GoalPhaseDocumentStartOperationSpec` specifies ordinary public
Start/Advance on an isolated verification Phase and temp repository, fresh text
after preparation, captured bindings/acceptance and the original clean base, plus
missing/malformed input and missing workspace authority. These scenarios end at
initial issue. Its normal scenario also inspects the same actual loaded WorkOrder
for the durable Start documents and stable `VERIFY-1` acceptance in ContextBundle
instructions. These scenarios require no child SBT execution or command/review
evidence. Their presence is an executable specification, not a claim that the
captured-context pass has passed parent validation or completed live refresh.

## Pure refresh compatibility

`GoalPhaseOrderedProgression.State.refreshedC(proposed, issued)` compares a valid
newer Plan with the selected controller frontier without effects. The declaration
owner supplies already captured effective acceptance; refresh never recaptures
from an overlaid effective boundary. `issued` describes actual current semantic
work already issued. Fresh dispatch and reconstruction of the pre-issue frontier
for admitting that selected request pass `false`. It is business context, not
authority or proof. `protectedStepCount` includes the completed prefix and the
current Step after implementation issue or departure from Implementation.

Plan identity, Phase, repository and repair limit stay fixed; revision may change.
Completed Step definitions remain identical and in their original prefix order.
A protected current Step also remains identical, including purpose, ownership,
dependencies, checks and classification. A valid unissued Implementation may
change its own definition. The remaining future tail may change, reorder, grow
or shrink under the existing coverage and earlier-dependency validation.

After any completed Step or protected current Step, saved effective Usage and
all/Step conditions remain fixed. Conditions compare by stable identity with
every field, including source, while presentation order has no meaning. An
absent boundary equals an empty boundary with the same Usage and condition maps.
Raw adoption remains in the proposed Plan and cannot override this comparison.
Phase-scoped effective conditions lock only when whole-Phase review is issued
or the controller reaches PhaseClosing. Whole-Phase review classification locks
at the same point. A compatible classification change before issue still needs
the existing runtime-profile admission when actual work is issued.

Phase checks may change before whole-Phase validation completes. PhaseReview
and PhaseClosing retain the selected checks. An unissued PhaseReview may instead
adopt new checks together with a newly appended tail because its prior aggregate
evidence becomes obsolete. Once Phase review is issued, or PhaseClosing begins,
any change to the selected Step vector is a `phase-scope` contradiction, including
appending work to the completed scope.

Compatible adoption preserves the original base, completed commit/review facts,
cursor, generation, consumed repairs, repository receipt and relevant candidate,
check, review, closure and repair fields. At unissued PhaseInspection,
PhaseValidation or PhaseReview, adding a tail returns Implementation and clears
only obsolete aggregate candidate, check receipt, review evidence, closure intent,
decision, repair directive and decision source. Removing every remaining unissued
Implementation Step from a still nonempty Plan returns PhaseInspection with that
same clearing. Neither case invents evidence, increments generation or refunds
repair allowance. Existing events, `next` selection and Human resumption retain
their meaning; compatible adoption exposes the proposed Plan through `next`.

`GoalPhaseDocumentRefresh.evaluateC` adds document acceptance comparison and returns
either diagnostic labels or `Adopted(controller, documents)` containing the actual
refreshed controller and proposed documents. Both old and proposed Bound values
must pass `GoalPhaseDocumentSource.instructionsC` validation. Their ordered Step
binding vectors must equal their respective Plan Step identities. Source filenames
must equal the original selection; document text cannot select another source.
Protected Steps compare item identity membership and identity-to-acceptance maps.
Checkboxes, item storage order and membership presentation order do not matter.
Removing, rebinding or changing a protected item conflicts even when its Step is
already completed and is not the current selection. Future unissued acceptance
may change. A removed protected Step also receives its document diagnostic.

Valid contradictions return labels in this order: `plan-identity`, `phase`,
`repository`, `repair-limit`, `completed-steps`, `issued-step`, `phase-scope`,
`step-acceptance`, `phase-checks`, `phase-acceptance`,
`phase-review-classification`, then distinct `document-acceptance:<Step-ID>` labels
in prior Step order. These labels are diagnostic business data. Malformed Plans,
uncaptured adoption, malformed documents, changed sources, mismatched binding
vectors and refresh at Decision or Closed fail with an ordinary `Consequence`;
they do not become Human contradictions. Rejection leaves the original state
untouched.

`GoalPhaseDocumentRefreshSpec` specifies compatible future edits, protected
definitions and acceptance, presentation neutrality, aggregate restart, Phase
locks, consumed repair preservation and malformed-input distinction. Its pure
fixtures reach controller states through existing events; their snapshots and
receipts are controller unit input, not native Git/SBT or provider evidence.
The pure policy reads no files, issues no work, persists no result and supplies no
checklist writeback. The owning continuation consumes its exact result after
actual native predecessor reconstruction and before each unissued frontier.
Valid contradictions after genuine work use the existing Human flow below.

## Live native selection and actual predecessor reconstruction

The existing NativeWorkContinuation Port is the sole driver. Its in-memory Cursor
starts at the actual saved Start successor and carries the reconstructed ordered
controller, selected documents, current actual Candidate and Validation results,
the genuine last semantic request/result, and any admitted semantic Human context.
It traverses one contiguous actual successor at a time; it does not predict the
last Step's successor from an older complete Plan. The same read-only traversal
serves Advance reconstruction, semantic Submit lookup and Human answer lookup.

GoalPhaseNativeHistory projects every frontier field from its selected actual
HistoryEntry. It admits the same instance and workflow identity/revision, returns
only the immediate next entry, and consumes a semantic or Human completion only
at the exact pending revision's next actual entry with the owning result cause,
unsuspended state and expected phase stage. Historical reconstruction never
appends another completion. Only managed Submit's writer constructs a new
expected completion for the canonical atomic transition.

At a semantic suspension the traversal loads the exact saved issued row. It
admits the selected Plan/documents against that actual predecessor through the
pure compatibility policy with issued=false, then checks instance, source
revision, kind, Step, generation, original base, completed facts, candidate,
selected checks and repair contract. It consumes the actual request/context and
existing runtime disposition. It does not compare copied complete envelopes as
unchanged-in-transit proofs. Both document options absent retain legacy Plan
behavior, including ordinary valid Step IDs. A source or option mismatch fails.
Submit returns the requested pre-completion state and retains its existing host
review evidence, repair ownership, native Git preconditions and managed atomic
completion gates. Submit never reads current document files, starts a native
stage or advances the workflow.

Normal pending Advance displays the exact saved work or Human question first,
before any fresh document acquisition. Once an action has begun, its saved input
owns that action or check batch until the actual result. Partial native intent
history with no complete result produces a truthful Wait at the current actual
record. It cannot trigger another staging, SBT or Step commit. An existing child
repository-closure intent/result remains owned by NativeRepositoryClosure and
its retained child input; it is neither refreshed nor cancelled. A completed
session reuses its actual repository terminal.

At an unissued live frontier, Advance reads only the original fixed source paths
through the current CNCF File authority. It captures effective acceptance from
the fresh raw imported Plan, never from an old overlaid effective Plan. A changed
Plan or semantic Bound creates one mechanical transition with payload
sourceRevision, plan, documents and conflicts. Its type identity is
GoalPhaseNativeDocumentSelection and schema is
sm-workflow.goal-phase-native-document-selection.v1. Its reference is the actual
instance plus :ordered-document-plan: plus source revision, revision 1. The
business cause is ordered-document-plan-selected or
ordered-document-plan-conflicted. This is ordinary selected business input, not
an integrity, amendment or recovery ledger. No source body, full previous Plan,
hash, TTL, generic store or second engine is introduced.

A retained selection is read only at that exact reference. Its original source,
source revision, deterministic pure compatibility labels and actual contiguous
native successor must correspond. Compatible selection adopts the actual
controller and Bound. Checked flags, item-vector order and membership
presentation order alone create no execution fact. Plan revision remains normal
selected data and may create a selection. CML documents-selected loops at each
live stage, documents-tail-added returns an eligible aggregate frontier to
Implementation, and documents-tail-removed enters PhaseInspection from
Implementation. Closed and Idle have no document route.

Candidate inspection and Validation are separate business frontiers in this
driver. Candidate.advanceC retains its bundled default for older callers; this
Port uses runValidation=false. Validation accepts an already admitted Validation
or PhaseValidation controller with its same genuine candidate. Its optional
from parameter identifies the actual selected Validation frontier, including a
document-selection successor. The unchanged payload sourceRevision, mechanical
references and initial check intent/result record all anchor there. Candidate
snapshot, diff and path ownership continue to come from the genuine earlier
Candidate result. ChecksObserved uses the actual Validation payload source
revision in its receipt identity. Current Candidate and Validation results
survive compatible selection and document Human resumption and supply actual
independent-review context. Repair selection and Implementation/Repair completion
clear obsolete active Candidate/Validation; old checks cannot become a repaired
candidate's evidence. Failed checks retain their real public validation projection
at a nonresumable native Decision/Wait; COMMAND owns further correction.

After a completed native stage, Advance reloads and reconstructs actual history
before selecting documents for the next business frontier. Missing installation,
authority or progress returns the existing Wait rather than spinning. New
ImplementStep, RepairStep and ReviewStep/ReviewPhase assignments share the same
issuer, current Bound and existing classification/profile/host-evidence admission.
Semantic completion alone never establishes Step or Phase acceptance.

## Document contradictions and existing Human direction

A well-formed contradiction after a genuine completed semantic result enters
Decision with DocumentConflictObserved. The reason is Latest document acceptance
conflicts followed by the deterministic labels. It preserves source stage,
generation, consumed repairs, original base, completed facts and relevant native
evidence. Fixed Start identity, Phase, repository or repair-limit contradictions
before any issued result instead fail as ordinary invalid selection input.
Malformed or missing documents, duplicate IDs, invalid dependencies, changed
sources and membership errors always fail before the next native effect; they
cannot fabricate decision-needed work. A compatible future-tail edit is adopted.

The existing Human question carries the actual previous request, result and
reference together with the current conflict reason. Only the internal Source
adds optional selected documents; Human Input/Answer wire formats and public
Operations remain unchanged. The document notice says Resume rechecks current
documents and grants no amendment, command, review acceptance or repair allowance.
An evidenced Resume through the normal managed answer Submit returns exactly to
the original live source stage. It clears only document-decision direction fields
and preserves generation, repairs and every relevant native fact. The CML event
is human-document-resumed- followed by that actual source stage. Answer Submit
performs no implicit native work. Subsequent owning Advance rechecks the selected
source at that frontier; an unresolved contradiction can produce a new genuine
question. It cannot amend the Plan automatically or fabricate a semantic result.
Ordinary semantic Resume retains its existing four stages and generation+1.
Unknown native failure remains nonresumable. Evidenced Defer or Cancel retains
actual history and yields stable Wait without a new question, work or effect.

## Executable coverage and remaining proof

GoalPhaseNativeHistorySpec uses actual appendC unit inputs to specify prefix
facts, exact next selection, paired completion and rejected revision/owner/stage/
cause substitutions. These unit records are not product effects or provider
proof. GoalPhaseDocumentRefreshSpec adds event-derived InspectCandidate,
Validation and Closing document conflicts/Resume, including consumed repairs and
native evidence preservation, plus the native-failure/semantic-resumption split.

GoalPhaseDocumentLiveOperationSpec uses the normal public Operations and the
managed repository fixture with only Git observations and bounded File authority.
Its isolated verification Phase and fixture-ignored documents specify changed
unissued purpose/reordered/added tail, actual selection receipt and reopened
pending issue; old pending acceptance and genuine completion followed by Human;
evidenced reconciled Resume at InspectCandidate without candidate execution;
evidenced Defer/Cancel stable Wait; malformed/duplicate input failure after
accepted work; and checkbox/narrative/presentation neutrality. It provisions no
handwritten native histories or issued rows and installs no SBT/provider program.

These Git-only examples do not prove tracked-document writeback or commit
ownership. The connected tracked-document executable specification below adds
that source-authored scenario; actual runtime validation and acceptance remain
parent-owned. COMMAND failed-check correction and ADMISSION provider connection
remain separate work. The parent selects focused suite execution and lifecycle
generation; the full repository suite remains deferred to Phase 6.3.

## Optional progress projection and exact owned body

The selected checklist may opt in with exactly one unindented top-level
`sm-workflow-progress` fence. Its opening line is exactly three backticks followed
by `sm-workflow-progress`; its closing line is exactly three backticks. Examples
inside other Markdown fences are skipped with the same backtick/tilde, marker
length and indentation rules as the Plan decoder. LF and CRLF are supported.
An initial opt-in has this exact business structure:

```sm-workflow-progress
{
  "schemaVersion": "sm-workflow.phase-progress.v1",
  "phase": "PHASE-6.1",
  "steps": []
}
```

After an actual ordered Step closes, each projected Step has exactly `step`,
`items`, `commit` and `review`. `items` contains its protected stable checklist
IDs in sorted order. `commit` has exactly `identity`, `parent` and `tree`, with
the actual closed Step's object IDs. `review` is that closed Step's admitted
independent review reference. The envelope has exactly `schemaVersion`, `phase`
and `steps`; unknown or null fields, malformed JSON, duplicate/empty IDs, invalid
object IDs, contradictory Phase or multiple/unclosed progress fences fail.

GoalPhaseDocumentProgress derives this projection exclusively from the ordered
controller's actual completed Steps and selected protected document bindings.
Step order is native completion order. Checkmarks, narrative, item presentation
order, source Plan revision, dates and pasted progress facts supply no completion
or review evidence. The block makes no whole-Phase closed claim and never points
to its own later management/checkpoint commit.

Without an opt-in and without a previous actual successful projection, selected
documents retain their read-only behavior. A first present block must identify
the selected Phase and have empty Steps; prefilled execution facts are rejected.
An empty desired projection creates no write or mechanical event merely to
remember the format. After an actual successful update, the current block is
required and its typed business value must equal that previous actual projection.
Removal, malformed data, duplicate blocks or any changed owned field are ordinary
explicit overlap failures, including a user edit that already equals the next
desired projection. Formatting differences alone are harmless. If previous and
desired projections agree, current body formatting is preserved without a write.

Every update merges against the latest acquired checklist. Only the bytes between
the opening line's end and the closing line's start are replaced; the original
prefix, fence boundaries and suffix survive byte-for-byte. Independent narrative,
planning, checkbox and ordering edits outside that body remain. No whole-document
parse/reserialization, copied source transport, hash or additional state store is
used. The document body is never read back as workflow execution facts.

## Current-call File intent/result and overlap handling

Enabled progress requires both literal selected source filenames in every
current Step's `ownedPaths` and in the host's fixed `candidatePaths`. Parent
directory coverage does not imply this permission. The internal Policy carries
only these fixed filenames, in Phase/checklist order. Missing scope fails before
native work; it does not enlarge staging or grant File/Git authority.

At every unissued live business frontier, the existing Port first selects fresh
documents, then reads the current checklist through WorkflowFileExecution and
the fixed CNCF FileTarget workspace `sm-workflow-repository`. Pending work/Human
display and managed semantic Submit remain current-file-read-free. Decision,
Closed and an already-started child repository closure do not enter writeback.
Only Implementation and PhaseInspection may write a changed completed prefix;
other live stages validate overlap and require the previous successful prefix
already to match desired facts, preserving checked/reviewed candidates.

GoalPhaseNativeDocumentProgress is a subordinate mechanical owner. Before File
update it saves an ordinary no-op prepared intent in the actual native instance,
with same-stage cause `ordered-document-progress-intent`, and acknowledges
`goal-document-progress-prepared`. It then uses UpdateExisting with the current
ephemeral full text as the CNCF expected-text concurrency input and the pure
merged replacement. Neither full text is retained. Updated or Changed/Missing
conflict is saved with same-stage cause `ordered-document-progress-result`, then
acknowledged as `goal-document-progress-recorded`. Created and AlreadyExists are
never update success. Failed/unknown File results and acknowledgement interruption
return transaction-indeterminate while retaining the prepared intent.

The receipt type is GoalPhaseNativeDocumentProgress, schema
`sm-workflow.goal-phase-native-document-progress.v1`. Payload contains only
`sourceRevision`, fixed `source`, optional `previous`, deterministic `desired`
and optional typed `outcome` (`updated`, `changed`, `missing`). References use the
actual instance plus `:ordered-document-progress:`, actual source revision, and
`:intent` or `:result`, revision 1. Names grant no authority or integrity proof.
The existing CML lifecycle has only the two same-stage intent/result loops at
Implementation and PhaseInspection.

Read-only reconstruction selects the exact retained reference and contiguous
actual intent/result HistoryEntries through NativeHistory. Source identity,
revision, stage, deterministic desired facts and previous actual cursor projection
must agree. It appends no historical records. An intent without an actual result
returns explicit unknown-effect Wait at the actual current record and never
executes File again. Recorded Changed/Missing conflicts also pause without retry
or rewrite. Only actual Updated advances the cursor's last successful projection
and Policy; the Port reloads its actual session after a fresh completed update
before further dispatch. There is no alternate driver or abandoned-run recovery
promise.

## Tracked documents, Phase candidate and existing child closure

Ordinary Step candidate behavior remains unchanged. At PhaseInspection, Policy
allows only the two exact fixed source filenames, including rename origins, as
dirty paths. Existing fixed stageGoalCandidate programs stage only those dirty
filenames under their existing persisted intent/result semantics. Branch and
actual HEAD remain the final ordered Step tip; HEAD tree remains the ordered
Step tree. The supported index must be fully staged. Typed PendingPaths against
that final tip identifies the actual management delta, while PendingPaths and
PendingDiff against the original base provide the complete Phase review. Existing
diff-size and binary limits still apply.

A nonempty source-only staged delta uses the dedicated pure event
PhaseDocumentCandidateObserved. It requires PhaseInspection, a valid same-tip
snapshot with a different tree, and distinct valid paths explicitly owned by
every Plan Step. The native helper additionally admits only actual Policy source
paths carried by the saved selected/staged candidate. It enters PhaseValidation
via the existing `phase-candidate-observed` CML edge, granting no commit authority.
The old CandidateObserved same-tree guard remains unchanged. Empty management
delta uses the ordinary clean whole-candidate path. Candidate v2 wire fields
remain unchanged: selected/staged paths are actual selected document paths;
changed paths and diff describe the entire Phase.

Whole-Phase validation and review preconditions admit staged dirty sources only
with actual progress Policy, nonempty equal selected/staged payload paths, exact
bounded dirty/rename membership, matching branch/HEAD/HEAD tree/index tree and
no unstaged/unmerged/unsupported changes. Raw payload path strings grant no
policy. Legacy whole candidates with no selected paths require a clean tree.
Policy is an optional internal trailing parameter; Candidate, Validation, closure
and semantic/public wire formats remain unchanged and legacy defaults remain None.

Before a new child closure intent, the existing RepositoryClosure owner admits
the actually reviewed staged source-only candidate under the same fixed policy,
HEAD/tip/tree/index and fully staged constraints. Otherwise it keeps the original
clean candidate rule. The normal FULL_STATE child checkpoints that staged input
through its existing typed Git effects and authority, then integrates and
converges. Original base, ordered Step commits and their final tip remain separate
from the child's actual checkpoint/convergence tip and tree. An already-started
child remains owned by its saved input; later source differences do not refresh
or cancel it. No new Git operation, argv builder, Process main or public field is
introduced.

## Connected executable specification and proof limits

GoalPhaseDocumentProgressSpec specifies event-derived ordered facts, sorted
bindings, CRLF/outside-body preservation, legacy/empty/no-op semantics, malformed
and initial-fact rejection, typed overlap/removal, explicit ownership, the
dedicated candidate guard with the unchanged old guard, and distinct actual File
outcome classification. Controller fixtures are pure business inputs and claim
no native evidence.

GoalPhaseDocumentWritebackOperationSpec extends the existing managed repository
fixture using only its normal preparation/setup/acknowledgement hooks. Phase and
checklist are tracked by the initial fixture commit and never ignored. Ordinary
NativeHostGoalStartInput preparation supplies selected datastore input; no native
history, issued row, provider or driver is fabricated. Fixed typed Git programs
and one normal selected SBT executable supply workflow effects; generic programs
are empty. The upstream is an isolated local bare fixture repository.

The integrated scenario authors an initial one-Step Plan and performs assigned
Step1 edits, real checks, specification-owned independent review preparation,
separate authority and actual commit. Exactly at
`goal-step-commit-recorded:step-1`, the fixture adds Step2 and narrative. Ordinary
Advance consumes that actual commit, selects the new tail, merges genuine Step1
facts into the latest checklist and issues Step2 in history order. Reopening
retains the exact pending request/context/continuation without another update.

While Step2 is pending, a fixture user edits one owned review field plus unrelated
narrative/order/checkmarks. Pending Advance and actual semantic Submit retain the
issued contract. Next unissued Advance fails explicit overlap without advancing
history/index/check count. Reconciling only the edited field to actual Step1 facts
allows ordinary checks, independent review, authority and Step2 commit. Final
progress records both real Step commits, preserves outside bytes and enters a
complete Phase review with only fixed source management staging. Normal Phase
SBT, review/authority and FULL_STATE child checkpoint/convergence complete through
existing APIs. Assertions cover actual receipts/check output/exit, external child
PIDs, original base/ordered commits, separate convergence result, committed final
progress, clean repository and stable subsequent terminal/read behavior.

A focused second episode interrupts acknowledgement after an actual prepared
intent and before File. Reopening returns stable unknown-effect Wait with no
replay, new check, projection or dispatch. Typed outcome branches separately
retain Changed/Missing distinctions; these examples are not an exhaustive
interruption matrix or recovery guarantee.

Fixture reviews, authority and semantic responses are declared specification
facts. They are not real CBD production-provider evidence; ADMISSION owns that
later connection. Source authoring is not executed proof, Step acceptance,
independent protected review or a commit. The parent owns focused compilation,
CML generation, actual runtimeTestOnly accumulation, integrated protected Step
review and local acceptance commit. COMMAND failed-check correction and remaining
Phase 6.1 obligations stay separate; the full repository suite remains deferred
to Phase 6.3.
