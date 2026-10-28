# Release dependency validation

An optional `goal.phaseDependencyValidation` binding permits ordinary Advance to
capture a different, genuinely phase-approved dependency purpose after every
Step has closed and an actual Release repair Submit has been accepted. It also
handles an already saved primary candidate whose original whole-Phase checks
have not selected or started. The original candidate, its source revision,
successful local publication, closed Steps, Plan, base, generation and consumed
repair counts remain unchanged.

This is separate from `goal.dependencyValidation` and the same-Step
`dependencySourceRevisions` route. Absence preserves existing behavior. Explicit
null, malformed bindings and a binding for a different actual phase reject.
There is no alias or automatic fallback between the fields. The native
declaration validates the new field again after ordinary host decoding.

## Explicit host binding

The host supplies a finite binding through its ordinary
`sm-workflow.runtime.definition` configuration. The following is an illustrative
fragment; replace its root, Component/workspace identities, actual immutable Git
base, approved paths and fixed check declarations with the exact selected values.
Keep existing Step bindings, selected programs and historical evidence intact.

```json
{
  "goal": {
    "phaseDependencyValidation": {
      "identity": "cncf-release-help-validation",
      "revision": 1,
      "step": "phase:6.9",
      "requester": {
        "identity": "org.simplemodeling.control",
        "repository": "textus-control-center",
        "workspace": "sm-workflow-repository"
      },
      "dependency": {
        "identity": "org.goldenport.cncf",
        "repository": "goldenport-cncf",
        "workspace": "cncf-source"
      },
      "root": "/workspace/goldenport-cncf",
      "branch": "main",
      "paths": ["src/main/scala/Help.scala", "src/test/scala/HelpSpec.scala"],
      "base": {
        "head": "1111111111111111111111111111111111111111",
        "tree": "2222222222222222222222222222222222222222"
      },
      "baseAdoption": "Explicit original dependency base retained by the approved ancestor",
      "checks": [
        {"identity": "release-cncf-compile", "ordinal": 5, "kind": "compile"},
        {"identity": "release-cncf-help", "ordinal": 6, "kind": "test-only"}
      ]
    }
  }
}
```

The new binding supplies no approval. The walker reconstructs actual
`ApproveDependencyScope` results for `phase:<actual phase>` and matches their
requester, dependency, Usage Contract and exact proposal-path union. A closed
old Step must supply a genuine nonempty accepted prefix in the same Session,
Plan, Component/repository/workspace, root, branch and original base. New paths
must be disjoint from that prefix. An old Step approval, copied JSON capture or
different-purpose old approval cannot authorize the new paths.

The original primary candidate must be retained at actual PhaseValidation,
after actual Release repair completion, with all Steps genuinely closed. Any
prior validation selection, metadata state, check intent, result or outcome
for that candidate excludes automatic succession. A failed precondition before
the first check intent does not create a check result or qualify any new bytes.

## CNCF Git and check declarations

The dependency workspace requires an explicitly granted ordinary repository
`stage-full-state` **base definition**. The runtime never executes that whole
operation: it submits `StageSelectedAll` with a fresh `goal-release-path-1..N`
identity and exactly one typed path from the authenticated phase binding.
CNCF admits and narrows the base definition. Both preflight and actual UoW
receive the identical workspace, selection identity, exact one-path vector and
operation. Old fixed `goal-path-1..9` definitions are never a fallback.

Merge this illustrative base definition and grants into the existing ordinary
CNCF process plan; retain all existing observation and check definitions. The
real host must use its actual root, branch, endpoint and Component identity.

```json
{
  "schema": "cncf.runtime-process-execution.v3",
  "workspaces": [{"identity": "cncf-source", "root": "/workspace/goldenport-cncf"}],
  "workspaceGrants": [{
    "componentId": "org.simplemodeling.textus.smworkflow",
    "workspaceId": "cncf-source", "access": "manage"
  }],
  "gitPrograms": [{
    "target": "cncf-source", "operation": "stage-full-state",
    "identity": "cncf-release-stage-base", "capability": "cncf-release-stage-base",
    "gitExecutable": "/usr/bin/git", "localBranch": "main",
    "remote": "origin", "remoteBranch": "main", "endpoint": "/workspace/upstream.git",
    "selectionId": null, "selectedPath": null, "commitIdentity": null, "commitMessage": null,
    "limits": {
      "launchTimeoutMillis": 10000, "executionTimeoutMillis": 30000,
      "terminationGraceMillis": 1000, "stdinBytes": 2097152,
      "stdoutBytes": 131072, "stderrBytes": 16384,
      "argumentCount": 512, "argumentBytes": 131072,
      "artifactCount": 1, "artifactBytes": 131072, "workAreaBytes": 4194304
    },
    "environment": {"PATH": "/usr/bin:/bin"}
  }],
  "grants": [{
    "componentId": "org.simplemodeling.textus.smworkflow",
    "capabilities": ["cncf-release-stage-base", "cncf-release-compile", "cncf-release-help"]
  }]
}
```

The selected paths become the normal CNCF literal one-path Git invocation
`git --literal-pathspecs add --all -- <exact-path>` with CNCF's normal fixed
prefix. No AI argv, generic Process program, private metadata inspection or
whole-stage execution is used. A missing base program, Component grant or
workspace permission rejects preflight with **zero new workflow intent**.

The binding's fixed check identities and ordinals must be distinct from the
selected primary Phase checks. Supply normal typed SBT programs for that exact
dependency workspace and the selected kinds, for example:

```json
{
  "sbtPrograms": [
    {
      "target": "cncf-source", "operation": "compile",
      "identity": "cncf-release-compile", "capability": "cncf-release-compile",
      "sbtExecutable": "/opt/bin/sbt",
      "limits": {
        "launchTimeoutMillis": 10000, "executionTimeoutMillis": 180000,
        "terminationGraceMillis": 1000, "stdinBytes": 2097152,
        "stdoutBytes": 131072, "stderrBytes": 16384,
        "argumentCount": 16, "argumentBytes": 131072,
        "artifactCount": 1, "artifactBytes": 131072, "workAreaBytes": 4194304
      },
      "environment": {"JAVA_HOME": "/opt/jdk", "HOME": "/home/developer", "PATH": "/opt/jdk/bin:/opt/bin:/usr/bin:/bin"}
    },
    {
      "target": "cncf-source", "operation": "test-only",
      "identity": "cncf-release-help", "capability": "cncf-release-help",
      "sbtExecutable": "/opt/bin/sbt", "testClasses": ["org.example.HelpSpec"],
      "limits": {
        "launchTimeoutMillis": 10000, "executionTimeoutMillis": 180000,
        "terminationGraceMillis": 1000, "stdinBytes": 2097152,
        "stdoutBytes": 131072, "stderrBytes": 16384,
        "argumentCount": 16, "argumentBytes": 131072,
        "artifactCount": 1, "artifactBytes": 131072, "workAreaBytes": 4194304
      },
      "environment": {"JAVA_HOME": "/opt/jdk", "HOME": "/home/developer", "PATH": "/opt/jdk/bin:/opt/bin:/usr/bin:/bin"}
    }
  ]
}
```

An existing exact workspace/kind program may already supply these typed
operations. Its immutable old receipts do not count as new check results. The
new validation payload retains the actual new capture, selected checks,
process receipts and dependency executions for normal independent Phase review.

## Native history and freshness

Selecting the phase binding later must still permit reconstruction of an actual
earlier completed Release validation that predates that binding. At the historical
whole-Phase validation cursor, with Release repair active and no active or
historical phase capture, the walker uses a dedicated authenticated reader. It
returns only the saved validation after matching the actual supplied source,
candidate, complete native prefix, current selected check definitions, metadata
selection and contiguous check intent/result chain. The outcome must be `passed`
with every selected receipt successful, or exactly `failed`, `repair-required` or
`repair-limit-reached` with the existing `repairable` predicate proving typed,
terminated nonzero failures. Dependency and dependency-source captures must be
absent, and the result strictly earlier than the actual latest native record.
The ordinary native walker also authenticates the contiguous transition into
the next stage. Changed primary check identities or definitions refuse.

This read creates no selection, check, publication, recovery or dependency
authority. Missing, partial, unknown, current-only, not-started, cancelled, timeout
and incomplete validation do not qualify. Historical eligibility refusals retain
the guard diagnostic prefix and add only the actual source revision, saved outcome
when available and a bounded reason. The prospective guard text itself is unchanged
and its existing check/metadata-intent refusal
remain on ordinary retained validation, execution, preconditions and recovery.
An earlier success cannot satisfy the new current candidate or dependency purpose;
active and capture-bearing historical traversals retain their exact binding checks.

A real SBT failure and a saved validation outcome describe different facts.
An initial failed whole-Phase validation follows ordinary non-Release reading and
the existing initial-phase repair admission. A later failed Release test can have
the final outcome `repair-required`. Its historical reader authenticates that
original outcome and receipts; the unchanged state observer and actual recorded
successors reconstruct Repair, Decision or Human with the original generation and
repair-limit checks. No result becomes success and no later binding applies
retroactively to the earlier validation.

Before staging, the dependency root/branch/HEAD/original base must still match.
All dirty and renamed paths must lie within the protected prefix union the
exact new paths. Protected old paths must be fully staged with no unstaged
changes. Pending paths relative to the actual predecessor candidate tree must
be a subset of the new paths. These checks repeat at effect boundaries.

Each new path has its own committed stage intent, actual typed Git/UoW effect
and adjacent result in the distinct `ordered-release-dependency:` namespace.
Observation likewise has a separate intent/result. Known completed stage
results may continue to remaining work; an intent without its actual result
is UNKNOWN and pauses without automatic replay. Changing or removing the
binding after any succession intent rejects reconstruction.

The closed `sm-workflow.goal-phase-release-dependency-validation.v1` receipt
retains the original primary record/source/snapshot, Session, Plan, base,
generation, repair counters, original native frontier, actual phase admission,
ancestor closure and stage/capture provenance. The walker authenticates
canonical actual native sources, predecessor revisions, causation and payloads.
Decoded JSON alone cannot set the private dependency chain's active capture.

Observation reads the incremental predecessor-tree diff and full current index
twice. Nonempty new-only paths and nonbinary diff are required. The authenticated
active phase capture is last in dependency freshness checks; only its genuine
accepted prefix supersedes the old live full-tree comparison. Same-Step/source
contradictions and conflicting revised-source claims reject. Full tree/diff
and original prefix preservation remain checked before/after checks, on review
issuance and return, and at ordinary closure. New-path drift, old-path drift,
unrelated files and identity changes cannot reuse historical success.

Normal Advance continues with the **original primary candidate** and the new
native validation frontier. Local publication remains keyed by the original
primary source, preserving an existing success rather than publishing again
under a dependency source revision. There is no new Start, semantic repair
Submit, manufactured Human, Plan/base reset or repair refund. The original
whole-Phase UNKNOWN observation recovery is not used or broadened.

## Subsequent genuine Release repair

An actual independent PhaseReview may find that the primary source needs another
repair after the dependency validation passed. Only the contiguous native walker
can retain historical phase-validation authority at that real review's accepted
`repair` completion and ordinary transition to a new Release RepairStep. Its
private proof retains the original phase capture/source/generation, passed native
validation, actual independent review request/result and post-completion native
revision. The failed whole-Phase review is never classified as accepted.

At that transition active phase capture/source and the succession cursor clear;
the actual dependency-bearing validation remains in ordinary review context.
Every chain-construction branch preserves the historical proof. A public result,
decoded capture, claimed successful check or larger revision cannot create it.
Missing/unknown checks, a crossed original primary source or absent real review
return cannot promote dependency validation into this historical authority.

A genuinely later primary candidate following that repair may reuse only this
same complete explicit phase binding and unchanged dependency state. Session,
Plan, original base, actual repair generation/source, approvals, accepted Step
ancestors, dependency root/branch/HEAD/index/full tree/diff/worktree and ownership
remain checked. Removing or changing the binding refuses. The original capture's
generation and observation source stay unchanged. No new dependency staging,
capture or dependency check runs for that unchanged proved input. The new primary
candidate runs its own normal checks and independent review. Its beforeChecks
publication uses its genuinely new primary source and preserves the original
publication fact. Old primary check receipts are historical context only.

This route applies where the retained dependency check's actual relevant inputs
remain unchanged and independent of the corrected primary source/publication.
Known changed or uncertain consumed inputs require separately admitted validation
or provenance work. Image/manifest locator inequality alone is not an input change
when the dependency check does not consume that image. This follows the existing
non-hermetic dependency-validation contract: it does not automatically discover
arbitrary unversioned external host mutation or reconstruct historical executable,
environment and limit values. Host configuration/grants remain with their owners;
no new configuration-history registry, public operation or persisted schema exists.

## Isolated installed qualification

`NativeGoalReleaseDependencyValidationCliSpec` requires explicit properties:

- `smworkflow.spec.installedCommand`: absolute installed executable.
- `smworkflow.spec.releaseDependencyOldInstallations`: selected OLD image root.
- `smworkflow.spec.releaseDependencyNewInstallations`: distinct selected NEW image root.
- `smworkflow.spec.releaseDependencyLocator`: fresh absolute locator under a real nonsymlink parent.
- `smworkflow.spec.releaseDependencyStage`: `setup`, `resume`, then `verify`.
- `smworkflow.spec.sbt`: normal absolute SBT executable for real narrow fixture checks.

Setup creates a fresh disposable primary/dependency fixture, actual closed
Step, accepted Release repair, retained primary candidate, successful
runtime-owned publication and original unstarted ownership conflict under OLD.
Resume selects NEW against the same SQLite and Session through installed public
Operations, validates refusals, captures the new purpose, executes actual checks,
and completes SPECIFICATION-OWNED independent review and ordinary closure.
Verify reopens that exact fixture under NEW and proves terminal no-replay.
No native record is injected or rewritten. Runtime images come only from the
explicit installation properties; this scenario does not select
`sm-workflow-dev` class directories as an installed runtime.

For the real source owner's continuation, first select the exact installed C
image and actual dependency source using that owner's ordinary fixed
runtime/source-selection procedure. Recheck the retained phase approval and
unchanged original prefix, add the explicit host binding and base grant, then
use normal Advance. If the source owner's ordinary contract requires an
independent Human for its runtime/source selection, obtain that specific
direction through that contract. This mechanism supplies no substitute Human
or source-selection consent. These specifications and documentation never
mutate the real consumer Session or its host configuration.

The same installed specification also has explicit
`smworkflow.spec.releaseDependencyMode=coherent-source`. It requires these
additional canonical absolute inputs:

- `smworkflow.spec.releaseDependencySourceManifest`: parent's semantic source list.
- `smworkflow.spec.releaseDependencySourceTemplate`: selected older operational C source.
- `smworkflow.spec.releaseDependencyQualifiedPatch`: parent's exact final thirteen-path patch.
- `smworkflow.spec.releaseDependencyUpdater`: selected ordinary installed updater.

Use a new locator for this mode and retain the same mode/input selections for
setup/resume/verify. Before Start, setup copies only literal source-manifest paths,
keeps its own fresh Git repository and preserves normal C coordinates/build/plugins
and caches. It adds the fixture ProductCheck with LocalTestDeclaration, publication
count instrumentation and normal local-application-update registration to its fresh
fixture image root/admission host. Missing patch paths receive empty tracked baseline
placeholders. Original ownership and typed Git selections cover the exact finite
patch scope. The initially adopted conditional source-coherence requirement applies
only after phase dependency validation is present; the first genuine review repair
still concerns the independently wrong usage document.

After NEW runtime captures/checks the phase purpose, a SPECIFICATION-OWNED independent
review reports the actual missing C-source repair. Only the newly issued ordinary
Release RepairStep permits placeholder removal and exact Git patch application.
The next primary candidate runs real typed TestOnly for the existing pure
GoalPhaseDependencyValidationSpec and fixture ProductCheckSpec. Normal beforeChecks
publication builds the actual C application and activates its produced installation.
The next CLI process selects that actual produced image root and reads the same
fixture Session through fresh review and normal closure. The owned caller retains
actual stdout/stderr and imports only its own successful preparation invocations;
it never invents shared fixture invocation membership or writes native records.

Assertions preserve old accepted Submit/native/candidate/publication facts, Plan,
base and closed Steps, ordinary repair-count increments, single dependency stage/
capture/check execution and genuinely new primary checks/publication. The standalone
dependency build has no C source/artifact/publication dependency; the fixture records
and compares its relevant exact build/test/program/workspace inputs before and after
the actual C publication. Wrong/removed bindings and dependency edits/renames or
unapproved paths refuse without replay. Verify uses the produced installation again.
These are authored qualification obligations; the parent owns actual execution and
acceptance. The original V4 explicit-profile cancellation remains separate uncovered
scope until that profile is selected.

The additional explicit mode
`smworkflow.spec.releaseDependencyMode=coherent-source-release-history` keeps that
same source, publication, produced-image and closure contract and adds a genuine
pre-binding Release cycle. Before Start its fresh fixture marker, Plan repair limit
of three and conditional required UTF-8 condition are adopted. The first independent
Phase review truthfully requests correction of the wrong usage sentence. A real
Release RepairStep changes only that sentence, followed by actual passed native
Release checks and a second independent Phase review. That review resolves all
relevant earlier REVIEW issues, including resolved issues, and truthfully reports
the now-applicable missing own LF line `The encoding is UTF-8.`. The second Release
RepairStep uses the existing new-purpose proposal and Human approval flow before
correcting that line and the approved Help path. The later actual C-source repair
uses the third ordinary allowance; source coherence was also adopted before Start.

The new focused source case in `NativeGoalDependencyValidationCliSpec` uses the
optional `priorReleaseHistory=true` setup and ordinary resume and closure. Both
the source case and installed mode refuse current phase-check identity/definition
drift with no native/check/publication effects, then restore the exact fixture
configuration. Snapshots retain the old passed result, candidate, selected checks,
native facts and consumed repair counters across new succession and closure.
Existing setup defaults, current check-intent refusal and UNKNOWN-stage refusal
scenarios keep their original contract.

An optional `smworkflow.spec.releaseDependencyHistoricalGuardBaseline=true` is
disabled by default and accepted only in the new mode when OLD's ordinary selected
metadata names installation `81a4f26f-733f-49ea-81f8-1023055be13d`. After OLD setup,
the fixture saves its exact host configuration, selects the explicit phase binding
and invokes OLD Advance once. It requires the historical prospective-guard 409
with zero new native/check/publication effects, then restores the exact configuration
before handoff. The locator persists and checks the exact mode and repair limit.
OLD selection remains unchanged; NEW uses the parent's distinct published image root.

`smworkflow.spec.releaseDependencyMode=coherent-source-failed-release-history`
adds a separate fresh fixture with repair limit ten adopted before Start. Its fixed
baseline `PhaseHistoryCheckSpec` exists before the baseline commit and is selected
only by the Phase `test` check at ordinal two; the Step retains its existing
ProductCheckSpec/TestOnly program at ordinal one. Each test records its actual
invocation. The Phase test requires the exact accepted first sentence and the own
LF lines `Line endings are LF.` and `Input has one record.`. Requirements, programs,
finite limits and business check selections all precede Start.

After actual Step closure the first Phase test genuinely fails with outcome
`failed` and initial-phase repair admission. Repair one changes only the first
sentence; the next test fails with `repair-required`. Repair two adds the LF line;
the next test also fails with `repair-required`. Repair three adds the record line;
the next test passes and issues the first independent Phase review. Assertions
retain all three complete failed facts, the later passed fact and issued review,
monotonic generations, counts one/two/three and unchanged Plan, base and accepted
Step. That independent review requests the pre-adopted missing UTF-8 line. Its
subsequent normal dependency proposal, Human approval and accepted repair preserve
both test requirement lines while adding UTF-8. The later genuine C-source repair,
exact eighteen-path patch, publication, produced-image closure and verify stages
retain their existing contract. The baseline Phase test is outside that patch.

In this new installed mode OLD must select v15 installation
`45204b91-ec9e-482c-924b-527c9f37596b`. Setup completes the entire failed/repair/passed
history before adding the phase binding and requiring the OLD guard 409 with no
effects, then restores the exact fixture configuration. This baseline is mandatory
for the new mode; the earlier mode's optional OLD81a4 qualification stays unchanged.
The fresh locator persists the exact mode, limit ten and failed-history snapshot.
NEW is supplied through a distinct published v16 image root. Resume and verify
authenticate the same Session and retained failures without replay or success credit.

The source-focused `failed-history` case uses that optional coherent fixture and
may finish at the genuine dependency ReviewPhase with its source-coherence condition
still unsatisfied. The installed resume stage supplies full source integration and
closure. These authored obligations cover the stated terminal history only; they
do not claim actual consumer success, all possible history variants or separate9051
UNKNOWN recovery.
