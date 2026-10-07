# Native human follow-up: read-only authority assessment

## Scope and execution ownership

Phase 2 continues under the user's parent-directed limited recovery. The
previous turn established the usable serial validation route (631: 196/196).
Parent `/root` owns this implementation and validation; no agents, shared-skill
edits, upstream edits, commit, push or publication were performed. The current
parent model and reasoning effort are not directly observable. This is
implementation preparation, not Step acceptance or a reconstructed PLAN/base
admission. Existing owned and user changes remain intact.

Product changes are limited to `GoalPhaseClosureAuthority.scala` and its
`GoalPhaseClosureAuthorityExecutionSpec.scala`. The affected edges are the
existing pre-reviewed/reviewed stop-only closure path and the future native
accepted-review follow-up. No CML, generated source, wire schema, storage grant,
component bootstrap or human-verifier contract changes.

## Requirement and implementation

Automatic native publication must not use a retained accepted review alone as
proof that current candidate bytes and closure authority still match. Nor may
it route a native semantic request through the old stop-only request validator.

The common read-only guard now admits snapshot bytes, derives the exact intent,
evaluates recorded authorization, then re-reads the current Workflow record,
snapshot bytes, authorization and retained proof before returning. Legacy
Decision selection remains between the initial and final observations, keeping
its exact ordering and refusal behavior. Existing legacy request validation
and commit selection are unchanged.

`assessReviewedC` first admits the issued accepted review through
`planReviewedC`, merges only its reviewed candidate evidence, and invokes that
same guard without a Decision port or commit port. Its typed result is
`Assessment(intent, Recorded | Missing)`. Missing uses existing deterministic
authority-gap routing. Malformed, expired or foreign authority remains a
refusal, never a missing-authority fallback. Neither result is an execution
grant, suspension capability or permission to advance.

Native follow-up must finish this read-only guard before invoking managed
publication. Publication must separately re-admit the exact predecessor and
current actor/lease. There is no atomicity claim joining read-only evidence
observation to a later external command.

## Executable evidence

Three new Given/When/Then behavior scenarios verify missing versus separately
recorded authority, zero Decision/commit/state effects, instance/snapshot/
authorization/retained-proof drift refusal, stale authorization refusal, and
rejection of missing issued reviewed input. These new positive assessments use
already admitted pre-reviewed fixture plans. They do not prove a successful
native automatic follow-up or retained-review integration of the new entry.
Existing managed accepted-review, legacy recovery, native publication,
completion and reopen scenarios run as regression evidence for shared code.

- 632: product/test compile and 46/46 tests in three suites, zero aborted,
  SBT/wrapper exit zero and lock released. Complete summary read:
  `/tmp/skill.cncf.d/a-3e7a176d-60b3-4a57-9788-1b3e1adfd931/65972-20261001T133039Z.summary.json`.
- 633: after a defensive incomplete-plan check, GoalPhase accumulator passed
  199/199 in 27 suites, zero aborted/failed/canceled/pending, exits zero and
  lock released. Complete summary read:
  `/tmp/skill.cncf.d/a-5a584c69-bc38-45b5-8f9a-7a882b36074e/66996-20261001T133233Z.summary.json`.

Both invocations use fresh typed permission artifacts with the full prior
consumed-ID ledger, scoped platform escalation and the common serial wrapper.
No source was edited while either SBT process was live. Generated sources were
reused (89); unchecked generic WorkOrder match warnings remain nonfatal.

## Next boundary and incomplete requirements

Connect the new assessed Missing branch to managed native question publication
after actual accepted-review completion, using the configured host snapshot and
authorization readers. Prove successful retained-review assessment, current-call
authentication, interruption/reopen recovery and stale evidence refusal on that
connected path. Keep legacy automatic preparation independent and reject an
ambiguous simultaneous policy rather than choosing one in the skill.

Automatic follow-up remains unconnected in this slice. Full CML constituent
execution, standalone-process launcher/CAR, resident server/MCP, production
human issuer, command authorization and Phase review/release remain open.
No checklist status or full-goal completion is promoted by these focused tests.
