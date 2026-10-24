# Start using the three workflow skills

The Phase 6.3 release scope has three supported tiers: `sm-split-phase` for
ordinary usable split planning, `sm-repository-sync` for one selected repository,
and `sm-goal-phase` for limited development within one repository. Related-project,
worktree and artifact orchestration belongs to [Phase 7](../phase/phase-7.md);
CBD integration belongs to [Phase 10](../phase/phase-10.md). Formal
[Phase 6.3 closure](../phase/phase-6.3.md) remains a prerequisite for production
acceptance. This guide does not declare the unfinished Phase complete.

These limits narrow the work selected, never the required checks, independent
review, Admission, actual local commit or closure. Use the existing host and
normal command path for real tasks. The client has one lightweight normal
connection qualification; follow the exact
[normal client recipe](../spec/native-client-installation.md#use-the-normal-native-entry).

## Prepare and start one task

1. Prepare the current component using `cozyPrepareRuntime`,
   `Compile/packageBin` and `skillBundle` through the registered SBT command
   runner. Follow [component preparation](../spec/native-client-installation.md#prepare-the-current-component)
   for the exact argument vector, current runtime artifacts and selected ZIP.
   Wait for the invocation's actual termination and retained receipt
   (`process=terminated serialization=sbt-native`).
2. Admit the owning host's normal configuration, assembly, persistence and
   actual subject using [host admission](../spec/native-client-installation.md#admit-the-owning-host)
   and [host configuration](../spec/normal-three-workflow-host.md#host-configuration).
   Fix the typed [Git resource and capability plans](../spec/normal-typed-git-workflow.md)
   and [SBT resource and capability plans](../spec/normal-typed-sbt-validation.md)
   for the selected workspace, branches, endpoint and checks. CNCF and the
   current ExecutionContext own configuration and grants; Operation parameter
   parsing does not install them. Native UoW owns typed `git_`/`sbt_` work using
   normal binaries and sbt-native locks. Independent development SBT invocations
   use the registered runner and those native locks; there is no machine-wide
   wrapper lock. Product SBT launch, waiting and collection remain runtime-owned.
3. Obtain genuine Start selection and, for Goal, its complete ordered Plan.
   Use the existing [Goal](../spec/native-client-installation.md#prepare-actual-selected-goal-input-and-plan),
   [Repository](../spec/native-client-installation.md#prepare-actual-selected-repository-start)
   or [Split](../spec/native-client-installation.md#prepare-actual-selected-split-start-consent)
   preparation recipe. The [normal passive preparation Operations](../spec/normal-host-preparation-operations.md)
   convert actual selected inputs; normal StartupImport provisions the selected
   host input. Conversion and import do not create consent, review or completed
   work. Keep private Workflow state out of preparation.
4. Extract the selected ZIP into a fresh task-private destination following
   [bundle installation](../spec/native-client-installation.md#install-the-selected-skill-bundle).
   Verify the exact seven files: three `SKILL.md` files and four supporting
   contracts. This checkout guide is not an additional ZIP member. Load the
   actual installed skill for the semantics issued to that task.
5. Use [normal command Start](../spec/native-client-installation.md#use-the-normal-native-entry)
   once. Retain its actual returned canonical session ID in that already-owned
   task's exact routing file, including when it differs from a proposal.
   Retrieve that file for each independent call; each execution owns a separate
   task directory. Use explicit Advance to obtain the next actual business view.

No additional installer, caller loop, product main, Operation or process fallback
is needed. Read configuration through its normal owners rather than parsing it
into a replacement execution path.

## Respond to the actual business view

A **Request** assigns semantic work. Read its issued context and declared result
contract, perform the actual work with the required independence, and supply the
genuine typed result and consumed evidence. Split supplies requested estimates
and semantic boundaries; Workflow owns mechanical partitioning. Repository
supplies the issued review, repair or conflict proposal; Workflow owns Git
effects. Goal supplies the issued development, repair or review within its
selected Plan and saved acceptance boundary. A skill does not choose another
route from prose or execute mechanical effects itself.

For a compatible supported result, return control to the existing host's
[`complete` action](../spec/native-goal-command-task.md#return-the-scoped-semantic-result)
with the actual Request and submission. The host performs one normal Submit,
checks the actual matching ID, Boolean `accepted=true` and `outcome=accepted`,
displays the actual message, and only then performs one outer Advance. A Goal
child uses its issued child Submit ID and retains the outer Goal Advance ID.
The action stops at the returned view. Rejection or uncertain Submit stops
without Advance; uncertain Advance preserves observations without replay.
Other issued routes retain their existing explicit host procedure.

**Human** is an actual question and answer through the independent human
channel, with genuine evidence. **Wait** follows the actual stated condition;
it does not invite blind polling or replay. **Terminal** ends that execution
with its actual disposition. Unknown effects require observation at their owning
boundary, never an assumed failure followed by repeating the effect.

An actual [known-unexecuted validation](../spec/native-goal-command-task.md#retained-native-validation-nonexecution-continuation)
can continue after preparation correction through the next explicit Advance.
An actual [known-no-commit selected precheck](../spec/native-goal-command-task.md#retained-selected-step-precheck-continuation)
can continue after the selected correction under standing scope and authority.
Retain earlier failures, counters and applicable checks. Missing success alone
establishes neither case. A real scope change or remote effect still needs its
actual authority.

## Consume common Evidence and manage retention

Phase 6.4 E2/E3 accepted the minimum automatic Evidence/Journal route. E4's
producer delivery and installed consumer qualification are complete and accepted
in commit `808bc324`; batch/gzip/retention operations below are delivered.
At the 2026-10-22 P64-E5 documentation snapshot, affected Phase review, the
changed candidate's fixed full validation and ordinary closure retain their own
gates. See [accepted observations and limits](../spec/evidence-storage.md#accepted-consumer-observations--2026-10-22)
and the [Phase checklist](../phase/phase-6.4-checklist.md).

Use the actual business response's Evidence and Journal references. Keep the
Action outcome, archive outcome, Journal outcome and current availability
separate. Read a returned Entry with `JournalService.readJournalEntry`, then its
Evidence with `EvidenceService.readEvidence` through the installed ordinary
command. The Evidence ID is the lookup key for both UTF-8 text and its selected
gzip representation. A caller needs no absolute archive path. Reading a saved
reference neither reruns validation nor establishes a new successful result.
Automatic recording needs no AI transcription or separate archive task.

The E4 public operations use that same common service and configured collection:

| Operation | Intended use and boundary |
|---|---|
| `archiveEvidenceBatch` | Explicit supplementary registration of bounded, released text material. Retain every returned source/output, Evidence and individual storage/Journal result; a partial batch is not all-stored. It grants no Action provenance, acceptance or retry authority. |
| `describeEvidence` | Observe lifecycle and payload-read results separately. An expired item can still be found; a previously stored item can now be missing. Preserve the historical storage observation, Journal and accepted work. |
| `manageEvidenceRetention` | `track` explicitly enrolls an existing legacy ID without copying its payload; `hold` requires a reason and future review instant; `release` requires a reason. A review date does not release a hold automatically. No caller action sets workflow completion. |
| `findDueEvidence` | Query `expiry` or `holdReview` using the exact repository and optional Phase/Session filters. Keep the bounded limit, actual `hasMore` and returned continuation identity; an unavailable query is not an empty successful result. |

Retention starts from the supported runtime's actual producing workflow/Phase
completion, with thirty days after that recorded instant. Active work has no
expiry; an untracked legacy ID remains explicitly unmanaged until supported
tracking. Step closure, a completed test, storage and lookup do not substitute
for producing workflow completion. Holds and due queries delete no material.
Use the normal host's managed File workspace and datastore; Operation inputs
provide no alternate storage selector, arbitrary path or filesystem permission.

Consume a Journal-only continuation only for its retained actual storage
observation. It must preserve the existing Evidence identity and payload and
must not repeat the Action or an uncertain archive. A storage/read/Journal
failure retains its actual outcome for the owning recovery path. For example,
Phase 6.4's full receipt 235 is formally PASS while its stdout Evidence save is
indeterminate and Journal recorded that observation. A Journal Entry does not
prove payload availability; do not rerun that Test or archive to fill the gap.
The accepted consumer scenario proves active/hold/due/release through installed
public calls; exact completion/expiry/missing behavior reuses declared producer
controlled-clock/specification coverage. Observe a real producing Session's
completion basis only after its ordinary closure. No caller sets or fabricates
that completion time, and this post-closure observation cannot precede its gate.
Phase 6.5 deterministic assessment and three-workflow optimization remain
successor work; their handoffs replace none of these Phase 6.4 gates.

## Review, effects and completion

Independent review supplies a semantic verdict. It grants no CommitPending,
Push or FinalFetch authority. The operator separately authorizes the actual fixed
workspace, branches and endpoint; the host provisions the existing passive
[RepositoryEffect input](../spec/native-client-installation.md#prepare-actual-selected-repositoryeffect-input)
and [closing authority input](../spec/native-client-installation.md#prepare-actual-closing-authority-input)
where required. Start admission, review, mechanical validation and effect
permission remain separate requirements.

For GitHub use, prepare normal Git credentials and connectivity for the fixed
selected origin before starting. Isolated acceptance used a file origin; that
result does not establish GitHub authentication or push acceptance. It adds no
remote Operation and does not require repeating earlier validated branches.

Reuse genuine prior independent review as its original evidence. The narrow
[terminal-LF completion contract](../spec/native-goal-command-task.md#complete-the-original-reviews-terminal-lf-conditions)
allows covered completion only when the original independent verdict explicitly
settled all semantics except those exact conditions, the native path/mode/blob
proof fulfills them, and fresh complete selected checks include Compile and
Test. Other edits require affected review and current checks. The separate
Closing EOF-only precheck continuation keeps its own prerequisites. Neither
route creates a fresh PASS, an omission certificate or a general promise to
skip review.

An accepted Phase review may be reused at the repository/Final boundary only
where its actual scope and current evidence remain applicable. Preserve its
original identity and conclusions; obtain affected review for uncovered meaning.
Required Step commits, actual local release, aggregate full validation and Phase
closure remain with their existing owners. Practical use follows that formal
acceptance, with observed limitations kept explicit.

## Phase 6.5 current delivery evidence (2026-10-24)

Q1 through Q3-S have accepted independent reviews and native Step commits in
the existing Session. Q4 client integration, full validation, integrated Phase
review and local closure are pending. See the
[actual evidence and Phase 7 handoff](../journal/2026/10/2026-10-24-phase-65-delivery-and-phase-7-handoff.md).
