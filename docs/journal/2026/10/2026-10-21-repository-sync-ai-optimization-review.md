# RepositorySync AI Optimization Review for Phase 6.5 Candidate

Date: 2026-10-21
Status: Design review / Phase 6.5 candidate input
Scope: sm-repository-sync / RepositorySyncWorkflow

## Purpose

Review the current RepositorySync specification and implementation from the
viewpoint of AI optimization, especially the prospective Phase 6.5 direction:
strong deterministic harnesses, Bad Smell detection, optional cheap AI triage,
and expensive Detailed Review only when semantic uncertainty remains.

This review is about allocation of work between runtime/lint/AI. It does not
change current accepted RepositorySync behavior by itself.

## Overall assessment

RepositorySync is already strongly AI-optimized in its fundamental architecture.

The normative design explicitly makes AI-cost minimization a goal. Equal,
local-ahead, remote-ahead fast-forward and Git-conflict-free integration are
largely runtime-owned. Git execution, snapshot/dirty-state observation, relation
classification, checkpointing, validation, commit, non-force push and convergence
verification are deterministic/provider responsibilities. The semantic skill
does not choose commands, branches, remotes, retry state or the next Workflow
state.

Conflict handling is also already layered:

```text
Git merge conflict
  -> deterministic conflict classification
       -> non-collision/composable case: runtime
       -> modification collision: semantic AI
       -> authority / multiple valid choices: Human
```

This is close to the desired Phase 6.5 principle: AI handles semantic residuals,
not mechanical workflow progression.

## Main optimization opportunity: mandatory pending-merge review

The largest remaining AI cost is the current pending-merge review gate.

The normative flow is effectively:

```text
MERGE_STARTING
  -> MERGE_RESULT_PREPARING
  -> INTEGRATION_VALIDATING
  -> MERGE_REVIEW_PREPARING
  -> MERGE_REVIEWING              # semantic AI
  -> MERGE_REVIEW_ADMITTING
  -> MERGE_COMMITTING
```

The native implementation reflects this contract. A retained
`PendingMergePrepared` candidate proceeds through selected validation and the
installed independent review port before merge commit/convergence. Repair is
also review-driven: a genuine review decision `repair` issues repair work,
materialization creates a changed candidate, selected validation runs again, and
a fresh independent review is required.

This is safe, but it means even a mechanically clean conflict-free diverged merge
pays for a detailed semantic review.

## Proposed Phase 6.5 direction

Do not delete RepositorySync review/repair machinery. Change the ordinary
requirement from mandatory Detailed Review to conditional escalation.

A target flow is:

```text
pending merge
  -> selected integration validation
  -> Scala lint
  -> CAR lint
  -> RepositorySync/Git-specific deterministic smell detection
  -> deterministic assessment
       |
       +-- sufficiently CLEAN
       |      -> optional AI smell triage may be skipped
       |      -> merge commit / convergence
       |
       +-- semantic uncertainty
       |      -> AI Bad Smell Triage
       |            -> CLEAN -> merge commit / convergence
       |            -> SUSPICIOUS / DECLINE -> Detailed Review
       |
       +-- explicit high-risk semantic route
              -> Detailed Review
```

Detailed Review retains the existing convergence meaning:

```text
Detailed Review
  -> blocking/in-boundary findings
  -> REVIEW_FIX
  -> affected validation/lint
  -> Re-Review
  -> repeat until the acceptance boundary converges
```

A review does not continue merely because further optional improvements can be
imagined.

## Repository-specific smell evidence

RepositorySync is a particularly good first Phase 6.5 application because it
already owns high-quality base/local/remote/candidate observations.

In addition to Scala-lint and CAR-lint results, deterministic smell evidence can
include signals such as:

- both sides modifying the same semantic target even when Git produced no textual
  conflict;
- public API/ABI/CML contract changes introduced by the combined result;
- generated and hand-written representations changing in suspicious combination;
- dependency/component boundary expansion;
- unexpectedly broad validation scope relative to the merge delta;
- duplicated implementation/state/validation emerging only after composition;
- new lint/CAR findings introduced by the merged candidate;
- effect/UoW/Capability boundary changes;
- implementation growth disproportionate to the actual repository-sync purpose.

These signals are review evidence, not automatic repair authority.

As in the general 6.5 proposal, the AI detector should receive the complete
Scala-lint and CAR-lint results even when each reports zero smells. It should
also receive the bounded RepositorySync merge evidence needed to judge the
candidate relative to its purpose.

## AI triage should itself be optional

Do not replace mandatory Detailed Review with mandatory AI smell review.

If deterministic evidence is strong enough, for example:

```text
selected validation PASS
+ Scala lint clean
+ CAR lint clean
+ no RepositorySync semantic-overlap smell
+ no contract/boundary expansion
```

the workflow should be able to proceed without an AI smell-detector call.

Use cheap independent AI triage only where deterministic evidence leaves a
semantic question. Luna is the first lightweight-model control; Local LLM
evaluation follows only if the bounded triage is viable at the Luna class.
SUSPICIOUS or DECLINE escalates to the strong Detailed Reviewer.

The intended cost ladder is therefore:

```text
deterministic
  -> stop if sufficient
  -> cheap independent AI only if useful
  -> expensive semantic review only if required
```

## Conservative initial exception: AI-generated conflict resolution

A merge whose semantic content was created by
`ResolveRepositoryMergeConflicts` deserves a stronger initial policy than an
ordinary Git-conflict-free merge.

For the first Phase 6.5 experiment, keep Detailed Review mandatory after an AI
semantic conflict resolution has been materialized and validated. This provides
independent scrutiny of AI-generated semantic content while allowing ordinary
conflict-free merges to exercise the optimized route.

This can later be relaxed to smell-based escalation if evidence supports it.

An initial policy matrix can therefore be:

| Merge path | Initial review policy |
| --- | --- |
| equal/local-ahead/remote-ahead | no semantic review |
| conflict-free diverged merge with clean deterministic harness | no Detailed Review by default |
| deterministic/lint smell or unresolved semantic uncertainty | AI triage and/or Detailed Review |
| AI semantic conflict resolution materialized | Detailed Review required initially |
| Human semantic resolution | explicit policy; do not infer AI acceptance |
| candidate changed by REVIEW_FIX | affected validation and Re-Review until convergence |

## Skill/prompt optimization

The public `skills/sm-repository-sync/SKILL.md` correctly states that the
runtime owns Git execution and the skill performs only issued semantic work.
However, the current skill is large and contains substantial host/runtime
operational material: visible command exchange policy, SBT lifecycle, signal and
termination handling, task routing, journal lookup, host execution handoff and
continuation procedure.

Those rules may be necessary at the host/product level, but they should not all
have to become reasoning context for every semantic merge judgment.

Phase 6.5 should inspect whether semantic worker input can be reduced toward:

```text
issued semantic purpose
+ immutable bounded merge/conflict context
+ applicable validation/lint/smell evidence
+ acceptance/review boundary
+ typed result schema
```

while host/runtime contracts remain enforced by their actual owners.

Do not achieve prompt reduction by removing required safety/authority behavior
from the product. Move mechanically enforceable control out of semantic reasoning
context when the runtime already owns and enforces it.

## Implementation impact

The optimization is not only documentation. Current native progression and
review continuation explicitly model independent merge review and repair
transitions. Phase 6.5 must preserve those routes while making review selection
conditional.

Likely change areas include:

- RepositorySync native progression after successful merge validation;
- review applicability/selection policy;
- typed deterministic smell evidence and its admission;
- optional AI smell-triage request/result if deterministic evidence is
  insufficient;
- direct clean transition toward merge commit/convergence when review is not
  required;
- preservation of existing review/repair/re-review path when selected;
- evidence/provenance that records why review was skipped, triaged or required
  without creating another whole-state ledger.

Do not duplicate the Git state machine or create a parallel optimized
RepositorySync workflow.

## Evaluation

RepositorySync is a good Phase 6.5 pilot because the existing architecture
already minimizes semantic AI in most normal Git paths. Compare the optimized
pending-merge route against the current mandatory-review baseline using existing
evidence where possible:

- fraction of conflict-free pending merges that avoid Detailed Review;
- AI triage invocation and escalation rates;
- important defects later found in candidates classified clean;
- Detailed Review findings after deterministic smell escalation;
- REVIEW_FIX volume and review-induced implementation growth;
- prompt/context reduction for semantic workers;
- false-positive smells and false-negative clean classifications.

Do not add a large measurement subsystem solely for this experiment.

## Recommendation

Use RepositorySync as an early Phase 6.5 proving case.

Its existing design already follows the core optimization rule for Git mechanics.
The next step is to apply the same rule to semantic assurance:

> Do not perform a detailed AI review merely because a merge occurred. Use
> deterministic validation and smell evidence first, invoke lightweight semantic
> triage only when useful, and reserve strong Detailed Review/FIX convergence for
> candidates whose risk or uncertainty actually requires it.

This preserves the existing strong review machinery while reducing routine AI
work and review-induced scope expansion.
