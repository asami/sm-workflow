# Full test execution through limited direct recovery

The user explicitly requested avoidance of the skill-only execution stop.
Continue the retained parent-direct recovery boundary without spawning runners,
changing skills, weakening sandbox review, or bypassing the shared SBT lock.
No acceptance commit, publication or Phase closure is authorized or claimed.

## Initial full validation

Invocation SMWF-FULL-VALIDATION-DIRECT-20261001-550 ran Compile / packageBin
and test through the installed serial wrapper with a fresh permission identity.
Compilation and packaging succeeded. All 66 suites completed: 605 tests, 604
succeeded, one failed, zero aborted/canceled/ignored/pending. Both exits were 1;
the authoritative terminal marker confirmed lock release.

Evidence:
/tmp/skill.cncf.d/a-b6b794b2-7d04-441c-93e2-681b3f013e3e/72448-20261001T080607Z.summary.json
and the adjacent .log. The complete summary and exact failure were read.

GoalPhaseCncfConsumerSpec failed only its whole-CML fixed hash assertion at
line 31. That assertion still bound the historical Phase 1 complete source,
although later services changed outside the selected GoalPhase definition.
The current Cozy outputs retain the fixture's GoalPhase definition and source
coordinates. Historical Phase 1 evidence is preserved, not rewritten.

## Frozen repair boundary

Modify only GoalPhaseCncfConsumerSpec and this journal. Replace whole-source
and fixture hash pins with structural equality against the actual current
Cozy output, projecting exactly one GoalPhase model while preserving every
selected field and all top-level generator/schema metadata. Missing or
duplicate selection fails; there is no fallback to the fixture for producer
output. Policy and source substitutions remain observable and have explicit
negative comparison examples. Fixture bytes, production code and authorization
contracts remain unchanged.

This is a compatibility assertion, not a control identity or a new hash-backed
receipt. Existing receiver admission and mismatched-pair refusal remain intact.
Re-run Compile / packageBin and all tests under a fresh execution identity.
No source edits while SBT is live. Full test success alone does not establish
production catalog writing, default launcher activation, independent review or
Phase acceptance.

## Separate write-path investigation

The existing public ComponentRecordStorage surface supports load/Create only.
WorkflowClosingLocalIntent and StageWorkflowClosingLocalIntent are private to
CNCF, and the current host event policy does not authorize arbitrary catalog
collections. Therefore the production catalog refresh cannot be implemented by
calling these internal types, an independent save/upsert, or a dummy event
cursor. Exact common collection admission and guarded Workflow coupling remain
implementation work; this investigation adds no write authority.

## Repair validation result

Invocation SMWF-FULL-VALIDATION-DIRECT-20261001-551 compiled/packaged and
passed all 606 tests in 66 suites, zero failures, aborted suites, cancellations,
ignored or pending tests. Both SBT and wrapper exits were zero; the authoritative
terminal marker confirmed the shared lock was released. The complete summary
and the repaired suite's four successful examples were read from
/tmp/skill.cncf.d/a-fc58a324-dac9-4ed6-8956-0995a7a7c050/73913-20261001T080831Z.summary.json
and its adjacent log. No source edits occurred during either live invocation.

The complete selected current producer artifacts match the unchanged fixtures,
including source provenance. The added example demonstrates that changed state,
Judgment policy and source coordinates survive projection rather than being
discarded. The former source-wide fixed hash is no longer a development gate.
This confirms the existing generated outputs, not an independent clean
regeneration: sbt-cozy reported its timestamp-based generation skip. Production
catalog write/grant integration and Phase acceptance remain open.
