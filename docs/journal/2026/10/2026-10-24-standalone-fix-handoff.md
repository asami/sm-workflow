# Standalone sm-fix: Step-equivalent acceptance

Date: 2026-10-24 (Asia/Tokyo)
Status: specification and development plan adopted; implementation pending
Audience: sm-workflow producer and consumer developers

## Adopted decision

One repair request is one Step-equivalent work unit. Perform required focused
behavioral tests and applicable compile/lint checks, then use conditional review.
Related files, findings and repair attempts do not create individual review gates.
Do not attach Phase Review or Release Review to an ordinary standalone request.
Commit/push/release is separate explicitly requested work.

The five cncf fix skills need not become five sm repair engines. Plan one thin
`sm-test-fix` entry, dirty/focused selection options, a finding-origin
`sm-review-fix` entry, and an authorized connection from Phase 14 `sm-diagnose`.
Investigation-only requests retain their read-only boundary. These names describe
planned entrypoints, not installed capabilities.

## Implementation instructions

Read the [specification](../../../spec/standalone-fix-acceptance.md) and
[SFIX-1–4 plan/checklist](../../../notes/standalone-fix-delivery-plan.md).
Reuse Phase 6.5 FIX/assessment/review, Phase 6.6 structured diagnosis and batched
information retrieval, runtime validation, WorkOrder/Continuation, common
Journal/Evidence/Finding and existing repair limits. Deliver one single-repository
failure -> repair -> focused PASS -> accepted uncommitted result before widening
entrypoints. Step-equivalent acceptance must not fabricate a GoalPhase committed
Step or relax that existing lifecycle's commit contract.

Use deterministic assessment without an additional AI call wherever the admitted
conditions are established. Independent review is only for explicit requirements
or remaining acceptance-relevant semantic judgment. Retain settled decisions and
existing evidence. Expected-value/test/suppression changes may require judgment,
but are not blanket review triggers. Record review omission truthfully, never as
review PASS. When review is required, subsequent review is limited to findings,
repair delta and affected meaning. Do not invent a full-review loop after each fix.

SBT launch, waiting and actual results belong to the runtime, not a caller-created
runner. Preserve compilation failure versus test non-execution. Keep the existing
counting window across attempts/providers/reviews. Test/recovery success is not
automatically causal Finding resolution.

## Scheduling and preservation

This is a follow-on delivery slice consuming 6.5/6.6, not extra acceptance work for
ongoing Phases or a newly allocated numbered Phase. Keep active recovery fixes
and development moving. Phase 14 investigation remains separately deliverable;
Phase 7 multi-repository work and Phase 9 routing enhancements do not block the
single-repository main path.

Preserve current source edits, Session/Plan/continuation identity, accepted work,
history, repair counts and applicable validation/review. Do not re-Start, replay
accepted Submit, move a Phase repair outside its Session, edit private runtime
storage or rerun successful checks just to adopt this policy. Use the next
supported continuation boundary. Report delivered behavior and remaining gaps
without claiming this planning document supplies execution evidence.

This update changes documentation only. No runtime/Session operation, product
implementation, SBT invocation, commit or push was performed.
