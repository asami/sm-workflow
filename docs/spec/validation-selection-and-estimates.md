# Focused validation, estimates and shared preparation

Date: 2026-10-30
Status: partial implementation and private publication; behavioral qualification pending.

## Authority and ownership

The actual Human request is recorded in the SimpleModeling architecture chat,
messages `01a18a0d-20a6-7740-be47-42aaf04803fc`,
`01a18a0e-9176-7300-a2ba-14142d1a2605` and
`01a18a0f-844c-7092-9c91-e8884c7931b4`. The native destination request is
`focused-validation-estimates-and-safe-fixtures-20261030`. It is distinct from the
retained prerequisite-publication repair. Neither its admission nor a fixture
test is evidence that the old engineering validation or real consumer completed.

## Selection and classification

ADMISSION, ordinary and HEAVY describe executable specifications. An ordinary
declaration has no classification label and belongs to the normal FULL selection.
FULL excludes HEAVY and external-service requirements. FOCUSED is a selection for
a particular change, with explicit conditions and a reason. It may include
ordinary, ADMISSION, HEAVY and existing scripted cases. Preserve their original
classifications. No authored FOCUSED membership or second suite registry is needed.

Use the existing CNCF discovery inventory, normalized metadata, provenance and
operation binding. Missing, ambiguous or unsupported selected leaves must not
disappear. Zero selected/executed tests cannot establish a pass. Independently
selected and reported parameterized instances count separately, including exact
typed input variants of the same operation.

For ScalaTest, each such instance is an actual separately registered selector.
Its test body owns the executable typed input. Optional `registeredInputs` in
the execution estimate records that input alongside its canonical selector;
it never supplies arguments or creates an extra execution. Different input
descriptions for one registration are ambiguous and rejected. Preserve these
typed descriptions in the producer result and receipt round trip.

`scripted` is optional for SBT/plugin build scenarios. Migrating a test to that
harness does not by itself reduce preparation cost or establish qualification.

## Estimate and actual process deadline

Reuse the CNCF-resolved `expectedDuration`: operation declarations override class
defaults, and a class default applies to each leaf. The configurable default for
an unspecified leaf is one second. The configurable initial multiplier is 1.5.

```
deadline = ceil(multiplier *
  (sum(selected leaf estimates) + startup + compilation + shared preparation + cleanup))
```

Preparation estimates are explicit, nonnegative estimates for the selected run,
counted once. A leaf's nested SBT or publication work belongs in that leaf's
scenario estimate and is not added again as shared preparation. The calculation
sets one process deadline; it does not create a 1.5-second timer per leaf.
ADMISSION's one-minute net-body target is a separate guideline.

Read ordinary configuration through the executing context's resolved parameters:

- `sm-workflow.validation.default-leaf-duration-millis`: positive integral number,
  initially 1000.
- `sm-workflow.validation.deadline-multiplier`: positive number, initially 1.5.
- `sm-workflow.validation.estimated-check-workspaces`: exact logical TestOnly
  workspace names; the host independently declares TestCompile and TestOnly.
- `sm-workflow.validation.startup-duration-millis`: nonnegative integral number,
  initially 30000; shared-preparation and cleanup duration keys follow the same
  prefix with defaults 0 and 5000 respectively.

Normal `Test / compile` exports CNCF's actual compiled discovery, including its
global completeness limitations. The required selected projection must contain
every admitted suite and valid unambiguous leaves. Runtime File/UoW reads that
generated inventory; there is no authored suite membership registration.

For opted-in bounded Task checks, native state retains the observed successful
TestCompile before inventory reading or body admission. The candidate tree and
check ordinal own applicability. A confirmed-unstarted body reuses that record;
unknown preparation/body effects wait for observation. Inventory/configuration
failure preserves the successful preparation and does not claim a body intent.
Observed compilation duration is counted once in the aggregate and subtracted
from the remaining TestOnly allowance. CNCF enforces the latter under the host's
explicit finite ceiling. Native SBT facts retain the selected typed estimate.

Reject malformed or overflowing estimates before execution. The CNCF adapter must
apply the resolved deadline to the actual typed SBT execution, subject to its
normal host limits. A caller-side timer, AI monitoring, argv injection or process
fallback is not an implementation of this contract. Preserve native cancellation,
cleanup and uncertain-effect handling.

## Reuse and scenario isolation

One validation run prepares its normal immutable installed baseline, common inputs
and build prerequisites once. Reuse valid compiled preparation only when the
ordinary build's inputs/configuration establish applicability. Normal dependency
caches remain shared. No generic cache manager, global lock or repeated repository
scan is introduced.

Every scenario owns writable repository candidates, configuration, Session stores,
selected-image pointers and publication/update destinations. Shared inputs and
installed baseline images must remain immutable. Use deterministic specifications
for policy, duplicate, trend and counter branches. Keep actual process,
publication, activation and continuation checks at the integration boundary.
Repeated whole-application copies and full publication solely for policy branches
are not required acceptance behavior.

The initial `SharedValidationPreparation` helper is arrangement infrastructure
only. Its isolation specification does not prove native publication, installed CLI
acceptance, or that the retained long integration case has been accelerated.

## Evidence and durable completion

Retain the Step selection conditions/reasons and actual resolved leaf inventory,
classifications, precise selectors/typed variants, source and operation revisions,
estimates/defaults, once-only preparation, effective deadline and actual results
in native evidence. At Phase completion, persist a concise English Git-tracked
record or companion inventory with these facts, measured timings, remaining
limitations and retained log references. A Task prepares its handoff; its owner
records final Phase facts. Disposable runtime evidence is not the only record.

The retained historical incident remains a timeout: 1,800,000 ms during the third
of four scenarios, 147 CLI calls, nine successful publications totaling 669,770 ms,
and 1,071,420 ms of terminal SBT work including publication. No deadlock was proved.
Do not rerun unaffected successful checks merely to obtain new timing samples.

## Current implementation boundary

The existing Task's public scope correction and ordinary Advance succeeded in
the same Session. Original ownership, purpose, checks, conditions, base and repair
counts remain; necessary SBT bridge and public-connection paths were added.
The implementation uses exact independently selected input and CNCF managed
WorkOrder supersession, retains the immutable old issue, rejects stale result
contexts, and does not infer nonexecution from an unclaimed issue.

Normal generation/Compile and runtime-owned publishLocal succeeded for the
repaired source, with task-private installed activation. Supporting read-only
review closed its identified blockers. Behavioral specifications are authored
but not executed; neither native integrated review nor Task acceptance is claimed.

Focused/FULL discovery and the typed TestCompile/deadline bridge source are
connected for the opted-in bounded Task path. CNCF's normal local publication
and supporting independent source review succeeded; actual focused test/UoW
execution is still pending. Real expensive-fixture reuse and application to the
retained engineering timeout remain incomplete. Other validation owners have
not been silently declared migrated.
Do not certify these conditions or Submit completion until their runtime
connections and actual selected checks have succeeded. The current Task also
places a post-validation delivery/application obligation before implementation
Submit. A public same-Session planning correction has been proposed; no condition
has been removed or certified merely to unlock validation.

See the journal's same-Session correction section for actual results and retained
evidence locators. No Start, accepted Submit, old engineering attempt or real
consumer operation was replayed, and no Phase completion is declared.


### Same-Session evaluation timing correction

An explicitly admitted correction may assign a proper ordered subset of the
existing condition IDs to `submissionConditions`. Empty added ownership is
permitted only with this timing correction. The objective, all condition IDs,
checks, base, history and repair counters remain adopted. An existing timing
plan cannot be replaced by this operation. Historical input without this field
retains the original all-conditions-at-submission behavior.

Implementation, repair and integrated source review must report every adopted
condition. Submission conditions must be satisfied; later conditions must remain
false with their actual pending reason. Successful validation does not convert
pending delivery conditions to success. Runtime validation, independent review,
local commit and required publication follow the existing route.

After actual local closure and publication, ordinary Advance issues a
`DeliveryAcceptance` semantic WorkOrder using the existing implementation
capability. This work is read-only and evaluates actual evidence for all retained
conditions, including application to the retained engineering failure when that
is required. Its completion requires every condition satisfied, no findings,
no unresolved questions and unchanged committed source/unrelated work. It does
not repeat implementation, SBT, commit or publication, replace the independent
review, or certify actual consumer activation. `ReturnReady` is reached only
after this acceptance; requester adoption remains separately attributable.

The correction uses the existing exact-target describe/prepare/correct operations
and managed WorkOrder supersession. The old issue and any unknown effects remain
retained. Only ordinary Advance issues the corrected work; a stale issue cannot
submit its result. Neither this documentation nor source compilation establishes
that the correction or any later acceptance has been applied to a real Session.


### One remaining candidate observation after an interrupted preparation

`describeDelegateTaskCandidateContinuation` observes only an active destination
Task's retained CandidateIntent. Its exact target includes the native frontier,
original payload and semantic completion, Component/participant, root/branch/base,
actual Git index/dirty paths and resolved host execution policy. Passive
`prepareDelegateTaskCandidateContinuation` grants no authority. The recover
operation requires independently admitted ExplicitHuman selection of the full
target and actual Human reference. It records CandidateContinuationReady only.
It does not reset or overwrite the original intent, Submit or repair counters.

Ordinary Advance rechecks the complete target and saves a distinct
CandidateContinuationIntent before any remaining effect. Only adopted paths
with actual worktree/untracked changes are staged; already staged clean changes
and staged deletions are skipped. Typed Git/UoW then collects a new pending path,
diff and index-tree candidate. Actual base/ownership/unrelated-work checks remain.
A successful new candidate enters the existing selected validation. Stale targets
fail before effects. A loaded new intent waits; it never implicitly retries or
certifies the old effect. Matching recovery redelivery returns the retained
receipt without executing the authorization again. No consumer Session,
Start, accepted Submit, commit, publication or historical successful validation
is repeated by this operation.
