# Phase 6.10 M1 standard baseline inventory

Date: 2026-10-29. Scope: authored M1 source and executable specifications;
no validation, installed-route acceptance or formal checklist acceptance claimed.
Original source HEAD: `3e7d7e20b991151867ae231e79a4f8d9cc467f1f`.

## Configured selection and observation limits

All five packaged role definitions in `agents/` (`sm_workflow_analysis`,
`sm_workflow_design`, `sm_workflow_implementation`, `sm_workflow_repair`,
`sm_workflow_review`) declare `model = "gpt-6.1-sol"` and
`model_reasoning_effort = "medium"`. These are configured role assignments,
not observations of a particular executor, provider, response or token usage.
Parent model/effort and actual worker model/effort remain unknown without an
attributable execution observation. No Terra setting or live configuration was
changed.

The repository `.textus/config.json` and the current Phase 6.10 native runtime
configuration at `target/sm-workflow/runtime-phase-6.10-20261029/.textus/config.json`
were inspected only as configuration files. Both omit
`sm-workflow.execution.profiles`, `sm-workflow.execution.origin` and
`sm-workflow.goal-phase.profile`. This is a bounded local inventory, not a
claim about every installation or environment override. No workflow database,
retained history or execution observation was read for this inventory.

`GoalPhaseNativeExecution` already supports either no execution mapping/origin
or a complete configured pair, rejects a partial pair and delegates concrete
selection to CNCF's `WorkflowExecutionProfileMapping.resolveC` for new work.
Without a pair, the existing WorkOrder execution requirement is retained.
The standard route preserves both forms, including the configured origin's
preferences, reasoning class, independent review and existing result admission.
It does not infer concrete execution from the packaged TOMLs.

## Authored M1 route

CML declares optional `profile` on the public `startGoalPhase` Operation beside
`input` and `sessionId`. Keeping it an Operation parameter preserves the existing
two-argument generated `GoalPhaseStartInput` constructor, its wire fields and
exact Phase/repository host business-input admission. The ABI manifest's Start
input/output types and fields are unchanged; normal CML generation owns the new
Operation parameter and Help. Generated Scala is not edited.

The normal Component parameter catalog declares optional string
`sm-workflow.goal-phase.profile`. `ThreeWorkflowRuntimeParameters` resolves it
through canonical initialization parameters and passes it to the Goal declaration
as a trailing defaulted source-compatible option. The actual native Start receiver
consumes `profile` using the existing named request reader. Resolution reports
explicit parameter, config or default provenance, and the selected standard route
borrows the existing concrete mapping/origin unchanged. The finite caller
forwards `--profile`; the skill documents the same parameter. No separate config
reader, raw model override, model table or executor is introduced.

M1 registers `standard` only. Omission and explicit standard are baseline
equivalent. Unknown, empty, padded, case-changed, non-string and not-yet-registered
cost/quality values fail explicitly without fallback. Invalid consumed config
also fails. Selection identity/version/source are resolver values, not fabricated
historical execution facts. M2 owns cost/quality registration and retention at
new Task/Step issuance; this change does not add capture or rewrite saved Starts,
issued work, active configuration, Plan, acceptance or repair counts. Resume
selection controls and mixed-profile attribution remain later work.

## Executable specification scope

`Phase610OperatingProfilesSpec` (M1-E1, P610-01/P610-02) exercises baseline
mapping/origin preservation, typed Component configuration, the actual Start
receiver and generated optional metadata, exact target admission, explicit/config/
default provenance, malformed public/config input and legacy constructor defaults.
The finite caller specification adds exact `--profile standard` forwarding with
unchanged business input. These are specification-owned inputs; they prove no
live model usage or successful installed Phase execution. No commands or tests
were run by the implementation worker. Native runtime checks remain pending.

No formal Phase checklist box is changed. P610-02 is only partially implemented
until M2 registers cost/quality and captures selection for newly issued work;
P610-03 and all endpoint/usage work remain with M2–M4.
