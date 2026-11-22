# Select the lint checkout from the Session and the lint policy from the candidate

Date: 2026-11-22 (Asia/Tokyo)

Status: agreed design direction and proposed repair. Implementation and qualification are pending.

## Decision

When a Step selects `FixCheck`, run it in the checkout of that Step's candidate.
A code Step requires Compile and focused TestOnly. Its `scalaLint: required`
policy additionally requires FixCheck. Phase closure selects its checks
separately from `phaseChecks` and runs a selected FixCheck in the Phase worktree. During repository
sync, run it in the checkout containing the integration candidate, which is
local `main` in the Phase 9 example. Both checkouts can belong to the same
repository and legitimately declare different lint policies.

A Step assesses lint findings against the diff from the start of that Step.
Phase closure assesses the cumulative diff from the start of the Phase to the
closing candidate. For the same project configuration, both use the same
rules, severities and failure thresholds. Unrelated pre-existing findings
remain outside the acceptance scope at both boundaries.

| Owner | Responsibility |
| --- | --- |
| sm-workflow Session | Resolve the current operation's candidate, workspace root and Git diff base. Use that checkout for project discovery, diff capture and SBT execution. |
| Candidate project | Its `project.yaml` selects lint enablement and profile or strictness. Its optional `.scalafix.conf` supplies project-specific rule adjustments. |
| User-managed host | Only the user updates `.textus/config.json`. AI and ordinary workflows read the admitted workspace scope, typed operations and process limits. An independently located canonical clone can still require an explicit user-managed mapping. |
| CNCF | Enforce the admitted execution scope and validate the typed FixCheck input and its limits. |

## Current coupling

Validation initialization looks up numbered workspaces at the target root and
requires a per-program `lintInput: true` for shared lint. Lint preparation also
derives its root from that program binding, while profile selection reads resolved
runtime parameters. This makes moving a check from a Phase worktree to local
`main` depend on duplicate host registration. A missing binding must not prompt
the caller to edit persistent grants during sync.

## Repair order

1. Add a connected regression case with two checkouts of one repository, different
   project policies and one user-managed host configuration.
2. Resolve the execution target once from the current Session candidate. Pass it
   to project discovery, Git and SBT. Check that the observed root and candidate
   match before using the diff or accepting a result.
3. Preserve the existing `validation.fixCheck` and `validation.sharedScalaLint`
   declarations. Resolve profile selection from the target project's declaration,
   retaining existing defaults when the new selection is absent. Reuse the shared
   profile resolver and project rule overrides.
4. Qualify the existing workspace initialization route for applying a previously
   admitted typed FixCheck to that Session candidate. Extend CNCF only if this
   qualification shows a missing capability. Keep input validation and process
   limits in force. Routine worktrees should require no per-checkout lint flag,
   numbered validation slot registration or new grant.
5. Update sync guidance and setup diagnostics. Before execution, report the
   checkout, requested operation, missing management setting and the public
   continuation the user can use after correcting it.

## User procedure to restore the host configuration

The following paths are relative to the sm-workflow checkout. The pre-sync
copy is `original-host-config.json`. The last sync copy is
`with-separate-validation-slots.json`. Both are in the directory assigned to
`snap` below. The latter matched the live file byte for byte on November 22.
Recheck at restoration time. Choose a point between workflow operations when
the temporary bindings are no longer needed and no one is editing the file.
Keep the snapshots until restoration is verified, since cleaning `target/`
can remove them.

For a full rollback, first confirm that the entire original-to-last difference
is intended to be undone. The user can then run this guarded sequence locally:

```sh
snap=target/sm-workflow/phase9-sync-20261122.AbULNR
live=.textus/config.json
if cmp -s "$live" "$snap/with-separate-validation-slots.json"; then
  backup=$(mktemp "$live.before-restore.XXXXXX") &&
    cp -p "$live" "$backup" &&
    python3 -m json.tool "$snap/original-host-config.json" >/dev/null &&
    cp -p "$snap/original-host-config.json" "$live" &&
    cmp "$live" "$snap/original-host-config.json" &&
    printf 'Restored. Prior configuration saved at %s\n' "$backup"
else
  printf 'Cannot verify live configuration against last sync snapshot. Inspect differences first.\n'
fi
```

For a partial rollback or a changed live file, save a private copy of the
current file and prepare the restoration in a separate copy. Compare original
to last to identify sync edits, then last to live to identify later edits.
Decode the JSON string in `textus.subsystem.process-execution.plan` for comparison.
Match workspaces by identity and programs by target and operation. Reverse
only confirmed sync changes, including corresponding grants, initialization
sources and canonical-main mappings. Restore an edited existing field to its
original value rather than deleting the surrounding declaration. If a later
edit touches the same field or depends on a new binding, the user decides
which value or binding to keep. Preserve the plan's original encoding, validate
both JSON layers and review the live-to-prepared diff before installing it.
Restoration concerns this host file only. Any subsequent workflow continuation
uses its retained Session and the permissions actually available after restoration.

## Acceptance

- With distinct changes in two Steps and the same lint configuration throughout,
  verify that the second Step assesses its own diff and Phase closure assesses
  the surviving changes from both Steps. A finding in the second Step's change
  must have the same severity and effect on acceptance at both boundaries.
- Observe the actual SBT root, diff base and applied profile for a lint-required Step, Phase closure
  and sync integration. Disabling lint in one checkout affects only its candidate.
- Compare `.textus/config.json` before and after both operations. Its contents
  remain identical, including on a setup failure.
- With a required grant absent, observe zero SBT launches and an actionable
  setup result. After the user supplies the grant, resume the same Session and
  candidate. If the candidate changed, requalify it before using any prior result.

Related specifications: [RepositorySync validation initialization](../../../spec/repository-sync-runtime-orchestration.md#common-validation-initialization)
and [shared Scala lint profiles](../../../spec/scala-lint-profiles.md).
