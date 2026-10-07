# Managed UoW integration scope boundary

Status: proposed central integration stopped; no code applied

Previous turn progressed with the typed host completion adapter and 270/270
focused specs. Current source inspection identified the missing explicit UoW
native uncertainty disposition and delivery-after-completion boundary. The
standard UnitOfWork/EventEngine Unit-returning commit and public completion
postcommit resume/history paths are not joint managed atomic completion.

A combined central opt-in patch was rejected before application because its
commit/termination/delivery blast radius was not covered by exact approval.
Read-only Git status/diff and source searches prove the central files remain
unchanged and the proposed new file does not exist. No bypass, indirect write,
SBT invocation, commit, public activation or sandbox relaxation occurred.

The concrete proposal records observed source gaps, three native dispositions,
memory-only discard semantics, affected consumers and a participant-first
conformance path before central dispatch changes:
[managed UoW proposal](../../../notes/managed-uow-completion-integration-proposal.md).

Central integration needs explicit scope approval and independent platform
acceptance. Goal remains active, not achieved; first occurrence of this blocking
condition, not a three-turn blocked audit. S12/P2-U15/P2-U16/P2-U05 remain open.

## 2026-10-01 correction

The final approval requirement above was the parent's mistaken classification,
not a requirement for another user message or `/approve`. Preserve the rejected
combined patch as historical evidence; continue the in-scope isolated internal
participant alternative without bypassing review or activating central UoW.
The later approval-based blocked audit was likewise invalid. Production native
enlistment, actual delivery and Phase acceptance remain unproven.
