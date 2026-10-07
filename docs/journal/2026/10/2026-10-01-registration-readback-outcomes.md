# Registration readback outcome verification

## Frozen scope and direct ownership

IMPLEMENT-REGISTRATION-READBACK-1, parent /root DIRECT, continues Phase 2.
Retain the user-authorized limited recovery and `回避して` exception for the
skill-only old command-route gate. Platform escalation, typed permission and
allocation, normal caches, shared serial lock and terminal evidence remain.
No commit, push, publication, dependency changes or other source roots.

Own GoalPhaseManagedRegistration.scala, GoalPhaseStartExecutionSpec.scala,
GoalPhaseManagedSuspensionOperationSpec.scala and this journal, all under
/Users/asami/src/dev2026/sm-workflow. Direct ownership disclosed before edits.

Extract only the existing deterministic retention algorithm into a package-private
fixed-identity load/Create port. Keep actor admission, captured grant, active lease,
owning component and retained Start checks ahead of production invocation.
No public fault parameter, alternate destination, replacement identity, compensation,
upsert, automatic duplicate classification or Workflow advancement.

Executable Given/When/Then: an initial read failure performs no Create; existing
malformed/conflicting rows are never overwritten; Create failure, absent/failing/
divergent post-Create readback are transaction-indeterminate and publish no receipt.
Use pure tests for outcome classification. Separately wrap the real granted capability
inside a private current-call test probe, perform real SQLite Create, mask only its
post-Create observation, then verify ordinary managed retry and reopen retain the row.
The wrapper simulates observation failure, not a physical database outage.

Validate packageBin plus the retained nineteen-suite accumulator with a fresh
single-use ID, full summary and terminal lock release. This is focused validation,
not Phase acceptance or independent review. Production launcher host/ports remain open.

## Verified result

SMWF-REGISTRATION-READBACK-DIRECT-20261001-538 packaged and passed 147/147
tests in nineteen suites, zero failures/aborts/canceled/ignored/pending. SBT and
wrapper exited zero, shared lock released. Complete summary and both changed
scenario sections were read:
/tmp/skill.cncf.d/a-7f353373-7a6c-4c58-be31-133d17aed397/38168-20261001T064710Z.summary.json.
The fresh ID is appended to the retained ledger; session is terminal.
git diff --check passed. No edits were made while SBT was running.

The extracted package-private retention algorithm preserves production ordering:
actor admission, configuration, fixed-identity granted storage, retained Start,
preparation, then retention. Pure executable specs confirm no Create after initial
read failure or a retained malformed/conflicting row; Create failure remains
indeterminate rather than being classified as duplicate. Missing, failed, malformed
and divergent post-Create observations cannot produce a successful record.

A separate private current-call probe performed three real SQLite Creates using
the granted lease-bound capability. It masked the immediate observation as missing,
failed or divergent, and each returned typed transaction-indeterminate. Removing
the mask allowed the ordinary production writer to recover the exact retained row
at the same identity. After pool shutdown/reopen the production reader recovered
all three rows. Workflow history stayed at Start, with no replacement, compensation
or new identity. This proves real persistence plus simulated observation failure,
not a physical SQLite outage or a fault through the public Operation/CLI.

The skill-only route stop was avoided only under the explicit user exception;
platform review, typed permission consumption and serialized execution were kept.
Phase 2 remains active: production default launcher/host application ports, actual
human selection installation and independent/full acceptance remain outstanding.
