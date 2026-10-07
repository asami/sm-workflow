# Automatic review question follow-up

Previous turn progressed: 568 packaged and passed 178 tests/23 suites for public
question preparation. Separate skill-side branching is not the intended final
state. Use cncf-goal-phase with retained unchanged reference snapshot, parent
/root direct limited recovery, standard mode, model/effort unobservable.

Freeze owned implementation before edits: shared GoalPhaseReviewDecisionPreparation,
managed review assembly/completion, recovery Operation and host-record Factory,
the existing managed public spec and this journal. No CML, upstream, skill,
agent configuration, approval, Git, commit or publication change.

Extract the exact preparation processing into one shared deterministic follow-up.
The host Factory resolves its optional explicit question template and fixed
Decision collection once, with the same Workflow declaration. Connect that
preparation to accepted review completion (including the repair-capable managed
binding) and ordinary retained-review recovery. The explicit preparation Command
uses the same follow-up; it is not an instruction for skills to choose branches.
No-template hosts retain read-only Decision lookup. Template-enabled hosts require
the captured Create capability even for equal retry, not authority inferred from
existing rows. Semantic result completion remains once-only.

Preflight calls projection without preparation, avoiding recursive writes.
Create/readback/equal retry uses the existing real managed writer. Final normal
projection uses actual recorded Decision and re-observes current Workflow,
candidate bytes, retained review and authority. Retention remains metadata only,
with no approval/result type, implicit grant, Git call or independent history append.

Run the real SQLite/Blob/Service/CLI scenario in two independently owned databases:
without a template (old fail-closed/recovery path), and with a template where the
first accepted semantic review directly returns the question without any prepare
call. Normal Service recovery and reopened CLI recovery repeat the same question.
Keep the later explicit preparation retry and all grant, anonymous, extra-input,
stale-revision, reopened history and no-Git assertions. Exact read counts account
for the preflight/final observation pairs rather than weakening checks.

Validation: package and 23-suite accumulator, expecting 179 examples after the
second public-scenario variant. Full current-tree validation, production human
answer/authority/resume, default launcher/server/MCP, independent review and
Phase acceptance/release remain incomplete.

## Validation result

Fresh invocation `SMWF-AUTO-QUESTION-DIRECT-20261001-569` packaged and passed
all 179 examples in 23 suites on the first validation attempt. SBT and wrapper
exits were 0 and the shared lock was released. Read the persisted summary and
both public-scenario variants, including the automatic first-review response.

- Log: `/tmp/skill.cncf.d/a-6e8f572b-99a5-43aa-b1da-a6fc659a5309/22754-20261001T093842Z.log`
- Summary: `/tmp/skill.cncf.d/a-6e8f572b-99a5-43aa-b1da-a6fc659a5309/22754-20261001T093842Z.summary.json`

The template-enabled first accepted review returned a stop-only question with
the exact accepted candidate, no result type and no commit. Replaying semantic
completion remained refused. Normal Service recovery and reopened CLI recovery
used automatic preparation and returned the retained question; the explicit
preparation command also retried that same metadata. The no-template variant
retained its original fail-closed behavior. Both preserved the exact rev-3
Workflow history until the deliberate competing-writer fixture advanced it.
Stale recovery/preparation then refused publication.

This is focused implementation evidence, not production human-interaction
attribution or Phase acceptance. HumanBoundaryFixture still supplies only
explicit host routing metadata; it is not an implemented production human
answer endpoint. Next implement independently attributed human Decision answer
and exact authority/resume, without synthesizing approval from stop metadata.
The goal remains active; no agents, commit, push or publication were used.
