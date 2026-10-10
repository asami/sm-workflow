# GoalPhase WorkClassification

Status: Phase 5 implementation and executable specification focused-verified
2026-10-08: five classification/codec cases passed, with affected ordered
progression/native run regression in the same 54-test, three-suite successful
run. This is not Skill integration or integrated acceptance.

## R1 — Semantic planning input

Planning supplies one of PLANNING, ANALYSIS, DESIGN, CODING, REVIEW or JUDGMENT,
with TRIVIAL/SIMPLE/STANDARD/COMPLEX complexity, PROGRAMMING/ENGINEERING domain
and SMALL/MEDIUM/LARGE context footprint. Deterministic classification derives
ReasoningClass and reasoning level, preserving capability, risk and the assigned
review responsibility. It supplies no provider, model, effort, executor identity,
execution permission or placement. Physical line count is not an input.

## R2 — Assigned Goal frontiers

Each classified Step binds CODING implementation, CODING repair and REVIEW
independent review separately. Whole-Phase classification is REVIEW. Complexity
TRIVIAL/SIMPLE selects Routine, STANDARD selects Standard and COMPLEX selects
Deep for non-review work. Review selects Critical for COMPLEX or LARGE, otherwise
Standard; review responsibility cannot be waived by classification.

## R3 — Retained semantic data

Selected Plan, native Start and Work request codecs preserve classified data.
Legacy Plans omit the new optional fields rather than injecting nulls into their
original wire format. Reconstructing an assigned logical requirement uses its
retained Plan, not a later Component profile mapping. Concrete profile resolution
belongs to the invocation boundary. A missing mapping is not a default profile
or proof that execution can proceed.

## R4 — Input ownership

Classification rejects incomplete/unsupported semantic members and concrete
model controls. The Skill-facing public interface and actual connection are
Phase 6 work. These executable cases are planning/codec checks, not evidence of
an installed executor, actual AI work, independent review execution or native
Step/Phase closure.

## R5 — Normal native frontier connection

The normal GoalPhase Start saves the complete classified Plan. Owning advance
issues the semantic requirement assigned to implementation, review, repair and
whole-Phase review, and physical reopen reads the original issued requirement
and Plan without requiring a new installer/selection. Classification must not
skip actual candidate checks, independently admitted review or native Step
closure. This semantic frontier connection issues no concrete placement or
executor observation; actual invocation still requires the unfinished runtime
connection and its real execution evidence.

`GoalPhaseNativeRunSpec` E6 exercises that connection through the existing normal
host binder, actual CLI/control operations, SQLite reopen and managed Git/check
programs. Reviewer registration and semantic answers in this scenario are
explicit test ingress, not actual AI execution or proof of a deployed reviewer.
The first E6 run failed at its final command-count assertion: the scenario
counted an unused generic commit name instead of the actual Step-specific
capability names. After correcting only those assertions, fresh focused run11
passed E6 (one test, one suite, 2026-10-08). Parent admitted the current typed
result and actual SBT0/wrapper0/released-lock evidence. The failure remains
recorded; this adds native classification/frontier evidence, not actual AI
executor execution or integrated acceptance. The earlier54-test result does
not cover E6.

## Executable specification coverage

`GoalPhaseWorkClassificationSpec` covers E1 (144 classification combinations),
E2 (assigned frontier separation), E3 (selected Plan storage), E4 (native Start
and Work request codecs) and E5 (invalid input). Connected Operation verification
and runtime execution evidence remain required for the applicable Phase 5 scope;
helper-level success cannot close the integrated batch or reopen Phase 2.
