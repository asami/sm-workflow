# Runtime-Owned RepositorySync for a Directory List

Date: 2026-10-31
Status: accepted target specification; implementation and installed acceptance pending.
Delivery: [Phase 6.11](../phase/phase-6.11.md).
Foundation: [single-repository orchestration](repository-sync-runtime-orchestration.md).

## Ordinary entry and responsibility

The ordinary sm-repository-sync entry accepts a finite list of existing repository
checkout directories and the user's synchronization intent. One normal invocation
must resolve configuration, execute eligible synchronization and return aggregated
results without intermediate AI decisions in the configured, authorized, conflict-free
case. A caller transmits user intent and renders results; it does not inspect each
repository, assemble operation bindings, choose Git commands, or run an AI-owned
per-directory loop. Mechanical runtime progress may be displayed through the
existing presentation without additional AI calls or periodic caller monitoring.

Runtime normalizes directory identities and avoids executing the same selected
checkout twice. Different worktrees of one repository remain distinct selections;
shared Git resources use existing exclusion/ownership rules, not a blanket new
machine lock. Respect configured tracking targets, including non-main branches;
never assume origin/main. Resolve normal PATH Git and explicit overrides using the
existing CNCF typed Git route and ordinary authority. Missing/ambiguous settings
produce a concrete setup/question result; the skill does not invent grants.

Use declared ordering constraints when relevant. Otherwise preserve input order;
independent pushes do not require inferred dependency analysis. Do not discover or
add unrequested repositories, create worktrees, or introduce project-development
management merely to synchronize an explicit list. Logical request identities and
retained per-repository continuation reuse existing workflow facilities; no new
scheduler, consent ledger, cache protocol or generic retry controller is required.

## Reported entry gaps: target and validation resolution — 2026-10-31

The user reported App/Core/TFAF stopping with remote-tip exit 2 on work branches
without upstream, and CNCF stopping after fetch because a program-affecting merge
needed validation configuration. These are observed caller reports, not a confirmed
root-cause diagnosis or evidence of successful synchronization. B1/B2 must trace
actual operation inputs, typed results and configuration before changing producers.
The prohibition on direct caller Git is correct; runtime preparation must close
the gap rather than adding a skill-side Git workaround.

### Work branch without upstream

Resolve the selected integration/push destination before executing remote-tip.
For a new request, use the explicit authorized destination first, the ordinary
repository sync-target setting next, and the current branch's upstream when neither
is supplied. A retained Session keeps its captured target; new intent/config is
adopted through the supported public continuation, not silently substituted.
A configured default branch may supply a destination only under an existing explicit
project policy. Otherwise return one precise unresolved-target question. Never
assume origin/main from an absent upstream or silently push the work branch itself.

Keep source checkout/branch, integration branch, remote and remote ref distinct.
A work branch with no upstream must still support an explicitly selected or normally
configured origin/main destination. Observe/fetch that ref and use the existing
integration mechanism to preserve both histories, validate the resulting candidate,
and perform only an admitted non-force push. Do not reset/switch a dirty checkout,
force-push, or install an upstream merely to make observation work. Existing normal
workspace handling remains reusable; Phase 7 worktree lifecycle is not a prerequisite.

Do not classify exit 2 alone as missing upstream. Retain the actual command/result
and distinguish unresolved input, absent remote ref, authentication/network failure
and genuine execution error. When the existing CNCF typed operation cannot accept
a resolved destination without implicit upstream, include the smallest producer
correction and tests in B1 rather than inventing a second Git executor.

### Program-affecting merge validation setup

Before required validation dispatch, resolve effective check bindings from normal
project/workspace configuration and the existing validation metadata/discovery and
policy. Reuse the project's ordinary compile, focused/FULL selection, typed SBT
workspace/grants, JVM settings and estimate-derived execution limits. Runtime owns
this resolution; the AI must not reread build/config files and hand-assemble normal
validation programs for every repository or every sync invocation.

A configured ordinary validation route must be usable by RepositorySync without
a duplicate sync-only registration. Existing impact/coverage rules decide focused
versus FULL and applicability of prior results. A missing focused-specific binding
may use an already-authorized ordinary selection route where the policy covers it;
never skip required checks, invent test selectors or switch to blanket FULL merely
to conceal unresolved coverage. Distinguish missing configuration/capability from
a compile/test failure, which requires actual execution evidence.

If essential settings truly cannot be resolved, return a structured bounded setup
request naming the missing items and keep the captured merge/fetch and candidate
facts. Apply supplied configuration/covering authority through the normal public
route in the same Session, then continue unfinished validation/integration. Do not
re-Start, replay accepted work or repeat successful effects just to rebuild setup.
Reobserve changed inputs when actually required; retained observations are not
proof that a remote or candidate cannot have changed. A known normal binding needs
no extra AI gate; a genuinely new permission still uses its existing owner.

## Automatic main route and validation

| Observed relationship | Runtime action | Additional validation solely for synchronization |
| --- | --- | --- |
| Equal | Return already synchronized | None |
| Local ahead | Non-force push and convergence confirmation | None |
| Remote ahead | Fast-forward and convergence confirmation | None |
| Diverged, Git can merge automatically | Prepare merge, perform applicable validation, finalize commit, push and confirm | Required impact-selected validation; successful checks and deterministic acceptance return no AI work |
| Actual conflict or unresolved meaning | Retain candidate and issue bounded semantic work | Existing repair/validation route after the answer |

Program-affecting automatic merges use focused tests or FULL where the impact
requires it. A successful textual merge is not evidence of behavioral correctness.
Document-only changes use relevant lightweight structural/document checks, not
blanket product FULL. Determine actual consumed build/runtime inputs rather than
assuming that any docs path is harmless. Reuse applicable retained evidence under
the existing contract; represent reuse honestly, never as newly executed PASS.

Consume the qualified focused-selection/estimate route where available: FOCUSED
selects required cases across classifications without removing HEAVY labels.
Ordinary FULL excludes HEAVY. Explicitly necessary HEAVY/scripted checks retain
that classification and their execution requirements; do not introduce them solely
because a batch or merge exists. Runtime owns selected SBT execution, deadlines,
retained diagnostics, conditional review and correction. Tests passing alone do
not waive an already-required semantic review. Do not add a universal AI review.

## Dirty input and bounded semantic handback

Reuse Phase 6.9's purpose-based preparation. Inventory changes and reuse recorded
Task/Step/purpose and validation data. Request AI only for unknown purpose,
inseparable groups, actual conflicts or unresolved semantic impact. Aggregate
independent currently-known questions with stable repository/request attribution;
do not require all future questions to be known before the first handback.
Do not commit excluded ongoing work, discard changes, or silently stage everything.

The default dirty route is **group by change reason -> accept and commit each
coherent purpose -> synchronize the resulting committed history**. It is not an
optional special mode, a blanket checkpoint, one commit per file, or a dirty-tree
merge. One purpose may span files; several purposes may share a file. Use existing
content-aware selection to preserve excluded remainders. If a split would create
invalid intermediate states, keep the inseparable changes together and record why.

Reuse attributable Task/Step/Finding/Journal reasons and prior acceptance first;
known coherent purposes need no fresh AI classification. For each group, reuse the
ordinary Step-equivalent validation and conditional review contract, and commit in
dependency order with a purpose-bearing message. Reuse applicable successful checks;
add no blanket FULL or review just for grouping or crossing into synchronization.
For that repository, complete admitted purpose commits before merge/fast-forward/
push; read-only target resolution and fetch may occur earlier. A commit or check
failure retains earlier successful commits and unfinished groups. A later sync
failure reports local preparation success separately; resume without recommitting.
If excluded work prevents normal integration, preserve it and return that specific
unfinished condition rather than stash/reset or include it without authorization.

An answer resumes only the outstanding work through its existing public context.
A normal configured batch with deterministically accepted work returns only the
final summary. A partial batch returns completed, waiting, failed, uncertain and
not-started entries accurately, together with the necessary semantic requests.
Independent ready repositories may proceed; a declared dependent waits when its
prerequisite has not completed. This is runtime policy, not caller AI scheduling.

## Retained results and partial completion

Record each selected directory/identity, local branch and tracking destination,
observed relationship, actual operations, commit/tip references, checks executed
or reused with reasons, and final convergence or remaining question/error.
Aggregate success requires every requested target to satisfy its admitted
completion condition. Local commit, publication and remote convergence remain
separate facts. Observe any generated in-scope difference before claiming local
closure; unrelated changes remain preserved and explicitly unfinished.

Use retained results and existing effect recovery after interruption or repeated
collection. Do not repush, remerge, rerun accepted checks or reset history to rebuild
a missing response. Unknown/live effects remain unknown/live until observed;
elapsed time is not permission to replay. There is no cross-repository rollback
or atomic remote transaction: completed repositories remain completed if another
fails. A new synchronization intent against later repository changes is distinct
from collecting a previous terminal result.

## Keep lower-level operations available

Retain the existing individually callable push-only, fast-forward-only and other
qualified synchronization operations. Inventory their exact supported public
names during implementation; this specification invents no new API names or
claims about installed availability. Explicit skill-level adjustment can use
these operations under the same authority, preconditions, observation, effect
recording and recovery rules. It must not fall back to shell Git or bypass checks
by selecting an operation inconsistent with the observed relationship.

The ordinary directory-list entry composes the same execution mechanisms. Keep
single-repository and lower-level callers working; do not duplicate Git execution
or remove these APIs when simplifying the skill. Low-level flexibility is not a
requirement for the AI to assemble ordinary synchronization on every invocation.

## Phase boundary and durable record

Phase 6.9 supplies single-repository clean/dirty integration; Phase 6.10 retains
its active AI-profile/metric work. Phase 6.11 supplies this finite directory-list
entry, native orchestration, result collection and thin skill. Phase 7 owns
project-local managed worktree/subtree development, dependency delivery/FIFO,
artifact selection and integration; it consumes this batch route as a component.
No Phase 7 workspace-management prerequisite is added to ordinary batch sync.

Preserve existing Session/Plan, histories, repair counters, accepted work and
successful verification. Adopt changes for active work only through supported
public paths; no re-Start, accepted Submit replay or storage rewrite. At completion
save an English Git-tracked summary of actual tested routes, source/installed
revisions, selected/reused checks, semantic-call counts and limitations. Disposable
Evidence links supplement rather than replace this record. This plan does not
execute Git operations, switch a runtime, or authorize completion claims.
