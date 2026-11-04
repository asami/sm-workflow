# RepositorySync unanswered Purpose content refresh

The existing public `describeRepositorySyncValidationContinuation --sessionId`
operation explicitly selects
`sm-workflow.repository-sync-purpose-content-continuation.v1` for an unanswered
Purpose WorkOrder whose worktree text changed after issuance.

Eligibility requires an actual available, unclaimed and incomplete native issue
with no accepted grouping, prepared candidate or commits. The target binds the
full original issued Request and current native Purpose capture, Component,
Session/Start principal, repository/root/branch/HEAD/index, source-record
provenance, frontier, original inventory and actual WorkOrder revisions. The
sorted path set, statuses, original contents and indexed contents must match
exactly. At least one ordinary worktree text must differ. Extra/missing paths,
changed metadata/provenance and unexamined bytes refuse.

The choices are `AdoptCurrentPurposeContent` and `Defer`. The shared public
validation-continuation preparation binds the exact target, participant,
reference, ordinary nonempty rationale and lifecycle assertions to independent
host evidence. Adoption requires `priorExecutionEnded=true` and
`acceptOriginalUncertainty=true`. An unclaimed issue can still have external
worker effects; the native supersession retains those effects as UNKNOWN and
does not manufacture a successful semantic completion.

Adoption uses WorkflowManagedWorkOrderSupersession to append
`PurposeContentContinuationAdmitted`. It stores direction only, retaining the
old WorkOrder and original `purpose-inventory` row. The old continuation cannot
complete after supersession. An identical adoption repeat at the same frontier
is observable; stale or conflicting repetition refuses. Defer writes no direction.

A separate ordinary Advance reobserves the exact adopted current Request. If
content changed again, it refuses instead of replacing the adopted target. With
unchanged inputs it creates a new immutable
`purpose-content-<frontier-revision>-inventory` row and issues a fresh WorkOrder
using that row's native revision. Later calls select the latest authenticated
inventory through native history; they do not overwrite the old row or issue a
second replacement. Completion validates the exact fresh issued Request and
continuation identity before accepting grouping. Staging, checks, commits,
review and publication remain with their later ordinary owners.

Native progress v3 adds optional `formalFailure` and `purposeContent` provenance
with historical missing defaults, omitting absent fields and rejecting explicit
null for these new fields. No old row is rewritten.

Authored evidence:

- `RepositorySyncPurposeContentContinuationSpec` binds 36 original/current
  paths with exactly three changed contents and refuses path, status, index,
  provenance and unexamined-byte changes.
- `NativeRepositoryPurposeContentContinuationCliSpec` uses tiny repositories,
  real typed Git/UoW and isolated native SQLite Sessions to exercise public
  describe/preparation/adopt, stale old Submit rejection, separate Advance,
  exactly one fresh inventory/issue, immutable old rows and changed-again refusal.
- `RepositorySyncValidationContinuationSpec` retains historical payload behavior
  and rejects explicit null provenance.

These are authored specifications; the parent owns generation and validation.

For installed upgrade evidence, select
`smworkflow.spec.purposeContentEpisodeStage=before` and an existing absolute
`smworkflow.spec.purposeContentEpisodeDirectory` while
`smworkflow.spec.installedCommand` names the old installed command. The suite
creates the real unanswered 36-path issue, changes exactly three contents and
saves `purpose.json` with fixture root/Session, public host/plan metadata and
actual stdout. After publication, select the same directory,
`purposeContentEpisodeStage=after` and the new installed command. It reopens the
actual native Session through `NativeWorkflowCliFixture.reopenInstalledRepository`
and exercises new public description, evidence preparation, adoption and separate
Advance. No native database row is directly authored or rewritten by the fixture. Omitting the
episode-stage property retains fresh normal-session coverage.
