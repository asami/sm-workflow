# Main synchronization through the canonical local checkout

Date: 2026-11-09
Status: adopted target requirement; implementation and installed qualification pending
Delivery: Phase 8 RepositorySync extensions

## Selected behavior

When the user requests synchronization with `main` from a working worktree,
the default order is:

```text
Selected working worktree
  -> canonical local main worktree
  -> selected GitHub main
```

Integrate the working branch's admitted changes into the local `main` checkout
first. Then synchronize that local `main` with the selected GitHub `main`, using
the existing fetch, relationship classification, merge and non-force push route.
In this default mode, do not substitute a direct working-branch-to-remote-main
push that bypasses local main, or claim integration from publishing an artifact
built in the worktree.

This default applies to main-directed synchronization. Ordinary synchronization
of another configured tracking branch keeps its declared target; do not redirect
every worktree or directory-list member to `main` or assume the remote is named
`origin`. An explicit user-selected different route retains its scope.

## Configuration property

Select the main-directed route with the String property
`sm-workflow.repository-sync.main-integration`:

| Value | Behavior |
| --- | --- |
| `local-first` (default when absent) | Working worktree -> canonical local main worktree -> selected GitHub main. |
| `direct` | Working checkout -> selected GitHub main through the normal single-checkout integration/push route, without automatically updating the separate canonical main checkout. |
| `confirm` | Present the actual source, canonical main and remote target to Human, then execute the selected `local-first` or `direct` route in the same Session. |

Example property value:

```properties
sm-workflow.repository-sync.main-integration=local-first
```

Use the existing home/project and Component configuration resolution; add no
special precedence rules or caller-side parsing. Single-repository and
directory-list entry points resolve the same property for the affected
repository. Empty, malformed or unknown values return a configuration error
before route-dependent mutation; do not silently select another mode.

`direct` still targets the main selected by the request, rather than changing
the target to the source branch's unrelated upstream. It neither requires a
separate main checkout nor silently includes its uncommitted changes or local
commits. Its outcome reports remote synchronization separately from the
untouched canonical checkout. Normal conflict handling, applicable acceptance
and non-force push remain required for both resolved routes.

For `confirm`, use the existing Human question/answer route before commits,
merges or pushes for this synchronization. Read-only inventory and target
resolution may precede the question. Show the actual checkout/branch identities,
selected remote main and the effect of each choice, including whether local
main will be updated. If a local main target is unavailable, explain that
limitation rather than presenting an executable local-first route. Human can
select either available route, defer, or cancel through the existing controls;
silence is not a route selection and must not trigger automatic synchronization.

Retain both the configured `confirm` policy and the actual Human-selected route
with the original request/Session. An answer continues the pending work rather
than creating a replacement Session. Reuse the same decision on continuation;
do not ask again after each operation or retry. A genuinely changed target or
effect scope retains its normal decision handling. Directory-list entry uses
the same policy and batches currently known route choices where possible,
without asking repeatedly for an already selected shared target.

This property selects the integration path only. It does not change
`uncommittedPolicy`, `syncMode`, branch identity, remote selection or execution
authority. A non-main tracking request keeps its target under all three values;
a request from canonical main uses the normal main route once. `confirm` adds
no route-choice question where the two routes have the same actual effect.
Route selection and `uncommittedPolicy=confirm` are separate decisions; choosing
a route does not authorize committing excluded changes or waive dirty-work
acceptance.

Resolve and retain the effective value in the existing synchronization Session
at target selection. A later configuration edit does not silently retarget an
active Session. A genuinely selected route change uses the existing public
continuation/adoption boundary and retains completed effects and history.
Failure in `local-first` never automatically falls back to `direct`.

## Checkout and target resolution

For `local-first`, retain the source checkout/branch, canonical main checkout
and selected remote main as distinct existing participants in the same request.
Resolve a linked main checkout from native Git worktree metadata. Where the
canonical checkout is a separate clone, consume the existing explicit project/
workspace mapping and verify the selected repository and remote identity;
matching directory names alone do not establish the target.

An unavailable or ambiguous canonical main checkout produces a concrete target
selection/setup outcome. Do not create a replacement worktree, choose a backup
checkout, change the source branch to `main`, or update a branch underneath its
checked-out worktree. Directory layouts are environment-dependent.

If the selected checkout is already the canonical main checkout, skip the local
integration hop and use the normal remote synchronization route once. A batch
that names both source and main coordinates their shared target using existing
runtime ownership, without repeating an already completed integration or push.
Different source worktrees remain distinct contributions.

## Local-first integration and remote convergence

1. Inventory the source and main working states. Reuse existing purpose-based
   acceptance and dirty-work handling, including the separately planned
   `uncommittedPolicy` choices. The default automatic commit covers intended
   authorized work only, not another task's excluded ongoing changes.
2. Integrate the accepted source tip through the canonical main checkout. Reuse
   native fast-forward or merge according to the actual relation. Handle actual
   conflicts and uncovered program impact through the normal semantic/check/
   review route; do not add blanket tests just because a second checkout exists.
3. After local integration succeeds, integrate the actual fetched remote main
   and perform any required non-force push from the canonical main route.
   Read-only target resolution/fetch may occur earlier; the integration and
   publication order remains local-main-first.
4. Report source contribution, local-main integration and remote convergence
   separately. Successful synchronization means the final local main contains
   the admitted contribution and converges with the observed selected GitHub
   main. Preserve the source branch name; do not imply its checkout was advanced
   to a later remote result unless that update was actually selected and done.

If local integration is blocked, preserve the changes and explain the blocker;
do not bypass it by pushing directly to remote main. If remote synchronization
fails after local integration, retain that completed integration and continue
only the unfinished remote work. An unknown external effect keeps its actual
collection/settlement path. No reset, forced push, automatic stash/discard,
history rewrite or blanket retry is introduced.

## Runtime and continuation ownership

Connect both hops to the existing RepositorySync workflow and CNCF typed Git
operations. The caller supplies intent and any genuinely missing target decision;
it does not assemble shell commands, switch synchronization skills or run an
AI-owned checkout loop. Extend the normal public input/configuration and retained
target representation as required; do not treat the current single-checkout
input as if it already declared this effect scope.

Reuse existing authority for the selected work and obtain an actual decision
for explicitly configured route selection, genuinely missing scope or unresolved
semantics. Preserve Goal/Session,
accepted results, checks, history and repair counts. Existing Sessions retain
their admitted scope until the public continuation/adoption route selects the
new behavior; the default does not silently retarget active work.

## Acceptance scenarios

- [ ] A working branch with local contribution and a main-only local commit
      integrates both histories in local main before remote synchronization.
- [ ] Remote-main-only commits are integrated after the local contribution;
      the final local main and selected remote main converge without force.
- [ ] A main-checkout invocation uses the existing direct main route once;
      an explicitly selected non-main tracking route is unchanged.
- [ ] An absent property and explicit `local-first` select the same two-hop
      route. Explicit `direct` selects the requested remote main without a
      separate main checkout or implicit canonical-main update.
- [ ] `confirm` presents attributable targets/effects before synchronization
      mutation, follows the actual chosen route in the same Session, and retains
      defer/cancel without an automatic choice or synchronization.
- [ ] A retained Human route decision survives continuation and shared-target
      batch handling without repeated questions, new Sessions, lost history or
      repair-count changes; route choice does not waive dirty-work decisions.
- [ ] Home/project and Component settings resolve normally for single and
      directory-list callers; invalid values fail before route-dependent
      mutation, and active Sessions retain their admitted effective value.
- [ ] Actual linked worktrees and explicitly mapped separate canonical clones
      resolve without machine-specific path assumptions; ambiguous/missing main
      produces a concrete decision/setup outcome without a new checkout.
- [ ] Dirty source/main inputs preserve intended versus excluded ownership and
      follow the selected dirty-work policy; local failure cannot trigger a
      direct remote-main bypass.
- [ ] A remote failure retains successful local integration; same-Session
      continuation does not repeat accepted commits, checks or completed pushes.
- [ ] An explicit directory list containing source and main preserves distinct
      contributions while avoiding duplicate effects for their common target.

Related contracts: [single-repository orchestration](repository-sync-runtime-orchestration.md),
[directory-list synchronization](repository-sync-directory-list.md), and
[Phase 8 uncommitted policy](../phase/phase-8.md#repository-synchronization-uncommitted-change-policy-2026-11-08).
