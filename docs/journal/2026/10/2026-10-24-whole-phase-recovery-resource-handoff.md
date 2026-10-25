# Whole-Phase recovery input through the normal Resource service

Date: 2026-10-24 (Asia/Tokyo)
Status: producer implementation accepted; isolated installed qualification accepted
Recipient: sm-workflow-dev開発

## Adopted public transport

The existing `recoverGoalPhaseWholePhaseCandidate` Operation accepts exactly one
representation: inline `--input` or `--inputResource file:///absolute/input.json`.
The file contains the complete unchanged business DTO, including the full target
and independent Human evidence reference. The normal executing CNCF Resource
service owns allowed roots, bounded reading and failures. Configure sufficient
`textus.resource.read.max-bytes` through the normal host configuration.

See the [public recovery specification](../../../spec/goal-phase-whole-phase-candidate-recovery.md).
No per-test manual registration or alternate execution route is introduced.

## Consumer continuation and ownership

Adopt the qualified normal installed runtime at the existing continuation boundary.
Keep the same Session, Plan/base, accepted results, completed Steps, successful
checks, candidate commits, repair counts and old failure/termination evidence.
Do not restart, resubmit accepted results, repeat successful effects or rewrite
the original target to accommodate a refusal.

The new transport preserves all existing exact-target, current-policy and
independent-Human checks. It grants no recovery authority or automatic Advance.
The owner uses its existing authorization and already prepared evidence through
the normal admission path, then issues the permitted recovery and follows its
actual typed result. A refusal remains a refusal requiring normal continuation.

Production Session recovery and Advance remain with the owning development chat.
The producer used only newly isolated fixtures and performed no real Session
operation. Other uncommitted work remains outside this acceptance boundary.

## Evidence retention

Source review, failed and successful focused attempts, normal publication/runtime
activation and installed qualification are retained outside Git at:
`macmini:~/backup/journal/2026/10/sm-workflow/whole-recovery-resource-20261024/a-fbfeb5ef-39a5-4235-b610-5df2aadaf1de/`.

Retention: 2026-11-23. Active consumer adoption and retained failed investigations
are on hold without expiry; next review 2026-10-31. Live typed records remain at
their original locations while consumers still reference them.
