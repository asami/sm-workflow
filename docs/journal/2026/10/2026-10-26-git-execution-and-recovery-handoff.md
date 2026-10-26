# Git execution defaults and RepositorySync recovery handoff

Date: 2026-10-26 (Asia/Tokyo)
Recipients: sm-workflow development and SimpleModeling development.
Status: specification/plan adopted; consumer correction pending.

## Current work and actual incident

SimpleModeling development owns an active CNCF Git execution-route repair.
Session `8f27ea7624b104a81e0b97e8ebe5eb50` started successfully but its first
Advance failed with `git.execution.program-definition`. Preserve the same
Session, original scope, history, repair counters and all accepted/failed results.
The actual immediate cause is missing per-operation execution definitions.

The user requires optional executable configuration: use an explicit Git when
provided, otherwise ordinary PATH Git. CNCF owns typed DSL argument construction;
users must not register every operation. A repository-level convenience grouping
must not retain mandatory executable setup for ordinary usage.

Additional source findings concern blanket authentication/config disabling,
unhelpful failure projection, pre-fetch tip equality and incomplete intermediate
effect recovery. These are separate findings, not a claim that all caused the
observed incident or that the currently installed product already fixes them.

## Implementation sequence

Follow [RS-GIT-1–5](../../../spec/repository-sync-git-continuation.md) and
the [RS-C1–4 plan/checklist](../../../phase/repository-sync-git-correction.md).
CNCF's corresponding contracts are `docs/spec/git-execution-defaults-and-diagnostics.md`
and `docs/phase/git-execution-route-correction.md` in goldenport-cncf.

1. Finish and consume the ongoing default-Git route repair first. SimpleModeling
   development owns its normal delivery and real synchronization continuation.
   Include authentication/diagnostics when actually required for that route;
   later recovery work must not become a new prerequisite to finish it.
2. Adopt the actual fetched revision. A remote push between advertisement and
   fetch is not by itself failure or a reason to repeat user approval.
3. Reconcile completed commit/push/fetch effects through existing public Advance
   and adoption. Collect original results, recognize intended Git outcomes and
   continue unfinished work. Do not rerun successful mutations or infer no effect
   from parser/transport failure. Keep a live or uncertain execution's handle.
4. Remove redundant observations and prove normal/interrupted operation through
   controlled local Git scenarios and the installed public route. Reuse producer
   guarantees and unaffected verification instead of running another broad suite
   merely to recover a result.

## Adoption and coordination

The new specification explicitly supersedes earlier no-intermediate-recovery,
new-Start and advertised-tip-equality requirements in the affected scope. Those
historical documents/results remain as evidence for their original inputs; no
old PASS is promoted to acceptance of the new behavior.

Schedule consumer work at a coherent delivery boundary. Preserve ongoing
source-history/RRC and Phase work rather than restarting them or introducing
a new cross-project waiting loop. Mechanical recovery does not consume another
semantic repair cycle or reset counts. Keep original uncertainty; record the
new recovery observation separately, and distinguish recovery from cause repair
using existing Journal/Evidence/Finding without an extra AI call.

No new numbered Phase, code change, runtime invocation, publication, Session
mutation or completion claim is made by this documentation update. Return one
outcome-oriented handoff with actual version, verification, runtime availability,
same-Session adoption result and concrete remaining limitations.
