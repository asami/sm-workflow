# Skill journal usage: common Operation candidates

Status: source-grounded design inventory, 2026-10-20. No installed skill,
Workflow definition, stored question or accepted result is changed here.
Related proposal: [normalized development journal](development-journal-proposal.md).

## Inspection boundary

Read the canonical development-workstation `common/codex/skills` sources for the
three public sm skills; search CNCF SKILL.md files for journal usage; inspect the
matching clauses and the typed Phase review/journal projection contract. Also
inspect the current sm-workflow finding-recording types, eligibility owner and
public manual. This is not an audit of every helper, plugin or domain skill.
Keyword occurrences include read-only evidence and prohibitions, not just writes.

## Actual uses and proposed common treatment

| Use | Inspected source | Common Operation responsibility | Remaining semantic input |
| --- | --- | --- | --- |
| Transfer accepted HYG/DEV observations to a journal | `sm-goal-phase` / Issued finding recording; `GoalPhaseFindingRecording.Observation/Item`; `GoalPhaseNativeFindingRecording.eligible` | Preserve source identity/revision, observation ID/category, summary/rationale and actual recording outcome; record at the existing eligible closure boundary. | Classification and rationale already returned by the independent reviewer. No extra AI journal writer is inherently needed. |
| Typed review history and accepted finding projection | `cncf-goal-phase`, `cncf-review`, `cncf-workflow-protocol`; `references/phase-review-data.md` | Record/read back admitted review references and project accepted HYG/DEV records deterministically. | Actual independent review judgment and genuine source attribution; recording does not create either. |
| Hygiene follow-up and nonblocking residual work | `cncf-validated-commit` / accepted Hygiene; `cncf-stabilization-cycle` | Shared issue identity, owner, evidence relations, disposition history and projections; no empty entry when no finding exists. | Whether work is maintenance, a blocker or a development candidate remains a semantic assessment. |
| Inventory, grouping and handoff of unresolved Hygiene | `cncf-triage-hygiene` / active batch, inventory, resolution batch; `cncf-goal-hygiene` / automatic triage and closure | Query existing entries, reuse an actually compatible active handoff, retain item-to-batch relationships and actual lifecycle observations. | Contextual classification and dependency grouping may need AI. Reuse its typed result; do not ask a separate AI to maintain markers. |
| Candidate deduplication and strategy/Phase placement | `cncf-triage-development-candidate` / reconcile and Strategy update; `cncf-triage` | Common candidate identity, source/owner relationships, selected disposition and historical references; bounded managed projections when adopted. | Equivalence by behavior and acceptance, scope placement and real Human direction where required. A query is not planning approval. |
| Split planning and repository merge reasoning | `sm-split-phase` / issued semantic work; `sm-repository-sync` / issued semantic work; `cncf-split-phase` / existing canonical finding IDs | Capture the already admitted semantic result and source relationships; reference an existing finding rather than cloning a journal per child. | Estimates, semantic boundaries, conflict choices and uncertainty are common decision content supplied by the existing work, not automatically skill-specific. |
| Validation, commit and release linkage | `cncf-release-commit`, `cncf-commit-execution`, `cncf-step-commit` / journal linkage and finalization | Link actual check/review/commit results and required journal projections; retain historical targets and exact acceptance distinctions. | Actual review and commit authorization remain with their owners. A journal entry cannot supply missing closure authority. |
| Resume/provisional handoff and current work summary | `cncf-goal-task`, `cncf-goal-phase`; provisional variants and `cncf-continuation-cycle` | Present retained business references, last actual outcomes, pending work and source relationships as a resumable view. | The durable provisional manifest or Workflow state remains its own authority. Do not turn a narrative journal into a replacement controller. |
| Agent/model trial observation | `cncf-luna-migration-observation` / Journal Persistence | Reuse common executor identity, model/effort, actual result, evidence and verdict references; update the same trial identity. | Comparison gate and trial-specific assessment are possible optional typed extensions. Only a genuine user verdict can change its decision. |
| Journal as read-only evidence or explicitly prohibited mutation | `cncf-investigate`, `cncf-car-setup`, `cncf-checkpoint-commit`, `cncf-consideration-mode`, `cncf-manage-phase`, `cncf-test-fix` | Query existing records only where admitted; preserve absence and original rules. | No blanket new recording duty. Reading a journal or prohibiting its mutation is not a journal-writing use case. |

## Findings

The inspected uses repeatedly need the same source identity, scope, observation,
rationale, disposition, evidence and history relationships. They support a common
model and managed persistence. A numerical proportion across all skills has not
been measured, and no current implementation or cost reduction is claimed.

Meaning-bearing AI work is not synonymous with skill-specific journal content.
For example, a reviewer may originate an HYG rationale, but the rationale and its
recording are reusable common finding data. Capture it from the normal admitted
review result; do not require a second summary or clerical AI recording task.

There is an actual precedent: `phase_review.py`'s documented `materialize` writer
projects exact accepted typed findings, handles duplicate/conflicting identities
and preserves existing text. It is a workstation-side file projection, not the
proposed journal service or a cross-file transaction. Its behavior demonstrates
a reusable separation between semantic assessment and deterministic recording;
do not move that private ledger into sm-workflow or claim it is already integrated.

The current sm Goal V7 still issues a separate file-edit-only finding-recording
Request. Replacing that clerical execution with Operation-owned recording is a
concrete AI-round-trip reduction candidate. Preserve the existing eligibility:
Step observations wait for the actual reviewed Step commit, Phase observations
for actual accepted Phase review. Missing policy, unresolved effects or recording
failure cannot be promoted to success. A reported finding, recorded entry and
approved implementation remain different facts.

## Information worth retaining from Operations

The Operation often has more accurate detail than a later AI reconstruction:

- Requested business action, actual resolved logical target and admitted scope.
- Relevant effective configuration/policy choices and actual branch reasons.
- Actual check identities, per-check/per-file outcomes, timings, exit/error data
  and uncertainty or cancellation observations where supplied by the runtime.
- Actual result, candidate, artifact and Evidence references; existing related
  entry identities; source participant/worker and actual provenance.
- Actual next condition, pending Human or unavailable capability, without
  manufacturing a future transition or success.

Use the actual typed fields emitted at their owning boundary. Absent data stays
absent/unknown. This list is not a claim that every current Operation supplies
all those fields. Richness is relevant structured information and linked
evidence, not a copy of every raw payload, entire context or log into Git.
Offer a concise human view and bounded detail/query views from the same record.

## Migration and cost priorities

Use the allocation in the [current handoff](../journal/2026/10/2026-10-20-common-journal-phases-handoff.md)
and [design](../design/development-journal.md). Earlier unadopted E2 candidates
remain historical proposals, not execution instructions.

1. Phase 11 J1: persist one actual existing Operation/review result through the
   common writer and read the same Entry in another process. Do not wait for
   Phase 6.4's evidence archive or manufacture an Evidence reference.
2. Phase 11 J2: at an explicitly selected compatible definition boundary, integrate common
   accepted-finding recording, reusing the existing typed review observation
   rather than issuing a journal-only AI task, and provide bounded lookup.
3. Phase 11 J3: align and deploy the three sm skills and verify their actual
   public connection in the Phase 6.4 trial host. Documentation or API presence
   alone does not establish readiness.
4. Phase 11.1: extend common issue/decision/handoff schemas and correction/status
   management for selected callers. This expansion is not a trial prerequisite.
5. Retain optional skill extensions only for meaning that the common model does
   not express. Skill identity by itself does not justify a custom journal type.

Keep existing Markdown authorities and source references until explicit migration.
Do not change an issued V7 recording task or an older Session's definition in
place, remove actual Human/independence gates, reopen accepted work or add a
second canonical store. No skills are edited or installed by this inventory.

Future connected validation should observe journal-only AI invocations, actual
Operation round trips and input/output volume for the same selected work. A
deterministic storage/rendering path is not an independent semantic review and
cannot be counted as one. State cost improvements only from actual observations.
