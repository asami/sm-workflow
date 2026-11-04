# RepositorySync conflict materialization

## Complete conflict result transport

`RepositorySyncService.submitRepositorySyncWorkResult` accepts exactly one of
inline `--result` or `--resultResource file:///absolute/authorized/result.json`,
with the existing `--sessionId` and `--resultReference` metadata. The Resource
file contains the complete `RepositorySyncConflictResult` JSON object, including
every resolution text; it contains no submission wrapper. It is read as UTF-8
by the owning ProcedureActionCall after the actual Session and current issued
assignment are loaded. Selection performs no file I/O. Missing, ambiguous,
malformed, unauthorized and over-budget inputs refuse before semantic completion.

```text
sm-workflow org.simplemodeling.textus.SmWorkflow.RepositorySyncService.submitRepositorySyncWorkResult --sessionId <actual-issued-session> --resultResource file:///absolute/authorized/result.json --resultReference '{"identity":"actual-conflict-result","revision":"1"}' --json
```

Use an absolute local file URI without authority, query or fragment. Normal
Resource roots (`textus.resource.url.file.roots`), finite read budgets
(`cncf.resource.read.max-bytes`) and UTF-8 decoding remain authoritative. No
helper-side result file read, arbitrary application byte cap, automatic
transport substitution or split submission is supported. The thin caller's
`submit-repository-conflict` submission metadata uses
`{"resultResource":"file:///absolute/authorized/result.json","resultReference":{"identity":"actual-conflict-result","revision":"1"}}`;
its other actions retain their existing metadata contracts. See the
[native host contract](native-host-execution.md) for exact helper usage.

The Resource transport retains the same DTO, context, revision, result reference,
participant and coverage rules as inline completion. Accepted Submit only saves
the result; it never calls Advance and grants no write, command, commit or push
authority. Existing native progression owns subsequent materialization and review.

## Complete Purpose result transport

The same deferred Resource boundary also applies to the actual issued
`RepositorySyncService.submitRepositoryPurposeResult`. The thin caller has no
Purpose submission action; invoke the normal installed Operation directly:

```text
sm-workflow org.simplemodeling.textus.SmWorkflow.RepositorySyncService.submitRepositoryPurposeResult --sessionId <actual-issued-session> --resultResource file:///absolute/authorized/complete-purpose-result.json --resultReference '{"identity":"actual-purpose-result","revision":"1"}' --json
```

Supply exactly one of inline `--result` and `--resultResource`, with unchanged
Session, result reference and optional completion facts/execution evidence.
The local absolute authorized file URI names the complete
`RepositorySyncPurposeResult` JSON object, including decision, rationale, every
group's full intended content text and excludedPaths, without a submission
wrapper. The owning ProcedureActionCall loads the actual Session and current
assignment before its Resource/UoW reads UTF-8. Configure the real allowed root
and finite Resource read budget for that host; the common default stays 1 MiB.
No URI grants authorization, and no helper reads the file, substitutes a
transport, splits the result or omits intended contents. Existing context,
revision, reference, participant and Purpose coverage admission remain intact.

Accepted Submit retains only the Purpose proposal. A separate normal Advance
owns candidate preparation, selected validation/review, acceptance, the Purpose
commit and subsequent push/fetch convergence. The isolated installed
`large purpose Resource` specification uses one complete 2,170,000-byte document
and a finite 16 MiB read budget, 8 MiB write/Git I/O/artifact budgets and a 32 MiB
work area. Its existing independent-document applicability rule permits no
product checks or mandatory detailed review; this does not represent a passed
validation. It specifies seven refusal cases with unchanged native revision and
history, HEAD, index and worktree; one accepted Submit followed by one Purpose
installation/commit; exact local and pushed blob contents; and a passive terminal
Advance without repeated effects. Qualification results belong to the actual
parent-owned run. Existing conflict transport proof remains applicable.

## Retained materialization evidence

Date: 2026-10-06
Status: connected boundary validated by999; practical delivery not accepted

The candidate selects `repository-sync-conflict-materialization-v6`; old v5
instances are not automatically rebound. Runtime file capability and each fixed
path staging definition are installed explicitly by the owned native test host.
The public owning-advance connection and interruption/foreign-edit examples
passed999. Actual998 failed main compilation on three references to the same
package-private relative-path predicate. Parent changed only its visibility from
private[admission] to private[smworkflow], retaining all path validation. No test
ran in998. Actual999 compiled current main/Test inputs and passed316/316 in25
completed suites, zero failures/aborts/canceled/ignored/pending. SBT0/wrapper0,
sharedlock released, current typed result and consumed permission parent verified.
Complete653 consumed commands through999 retain998's failure and all history.
997 remains earlier v5 evidence, not current v6 proof. No counter/frame changes.

[Actual999 log](/private/tmp/skill.cncf.d/a-a52d190f-ad6b-4058-bcc2-e57f46af6b67/64006-20261006T030115Z.log).
Admission: `.codex-workflow/phases/PHASE-2/three-workflow-command-999-validation.json`.
Three real interruption episodes use one assigned path (before files, after
files, after staging) and two physical reopens. Separate runtime-program cases
cover two-file partial installation and all-file preflight. This is not proof
of a full native multi-conflict scenario, normal installed configuration or
separate-process Workflow storage reopen. Human handling and final closure stay open.

Continue native v5 issue/completion with Workflow-owned application of the actual
saved resolved proposal. Completion itself grants no filesystem or Git command.
Owning advance loads actual issued native input and committed semantic result.
DecisionNeeded remains pending Human handling; it never takes this route.

A runtime-fixed JVM program observes/writes only configured ordinary text paths
under its fixed repository root. It accepts typed expected/replacement text as
application data, not an immutability certificate. Before writing, owning advance
persists MaterializingConflict with that concrete overwrite intent. Preflight all
files; reject symlink ancestors, special files, nonordinary text and changed
preconditions. CNCF enforces configured acquisition limits while reading; the
application consumes complete ordinary UTF-8 without another text-size ceiling.
Atomic replacement preserves file permissions. Multiple files
are not crash-atomic. Reopen observes ready/partial/applied/conflict and may apply
only missing replacements of this actual saved intent; contradictory edits stop.

Each configured path has its own runtime-fixed Git add capability and zero
dynamic argv. Workflow chooses only the original assigned paths; AI supplies no
path, command, endpoint, grant or transition. Before staging, verify actual HEAD,
tracking tip and MERGE_HEAD and original unmerged stages. During staged recovery,
accept only original remaining stages or actual stage-zero blobs matching the
selected proposal. Final write-tree plus native observation records the pending
uncommitted merge. No merge replay, commit, push, reset or Human override occurs.

Persist intent before an external effect. Uncertain effects are observed on a
later call; acknowledgement loss does not authorize another merge or overwrite.
Canonical Subsystem storage/UoW and ProcessExec own effects. Git/filesystem and
database are not one atomic transaction. Default configuration installation,
Human completion, final validation/review/commit/push/convergence and integrated
three-route acceptance remain required. Keep original9/R5/A7/null/3 closed of3.

Connected executable examples must use actual native conflicts, public completion
and owning advance, physical reopen before/after write or stage, foreign edits,
runtime path/grant refusal, unchanged unrelated files, retained MERGE_HEAD/HEAD,
and no duplicated semantic work/merge. No user repository is mutated by tests.
