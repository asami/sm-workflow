# SplitPhase Human direction contract

This contract describes the separately assembled Human profile introduced in v7
and retained in the current v12 definition, with a separate reasoning question.
The normal three-workflow factory includes this paired connection; the older
planning-only SplitPhase assembly does not. A host must supply canonical storage, authenticated
participants and an independent Human answer verifier before installing it.
An operation declaration or configuration flag does not supply those grants.

See the [reference manual](index.md), [user guide](user-guide.md) and
[handoff scope contract](split-phase-scope.md) for prerequisites and the preceding
semantic task. These are authored sources, not proof of runtime Help, MCP,
separate-process operation or complete split success.

## Operations and progression

`SplitPhaseService.advanceSplitPhase` observes the original context and current
native instance. For a completed scope result, the Human profile publishes the
complete candidate frontier. Only a genuine Human need issues a Decision; a
frontier consisting entirely of Built candidates is not substituted with a
Human task. The Decision retains the original context, first checkpoint,
scope-publication reference and complete bounded frontier. Its declared Human
SPI is `SplitHumanService.presentSplitHumanDirection`.

`SplitPhaseService.submitSplitHumanDirection` receives the common CNCF Decision
result envelope for that exact issued Decision. The actual owning completion
validates it, obtains independent Human evidence and commits the canonical
answer. Its acknowledgement contains the original issued Decision and committed
completion. It is not Terminal. Clients must use the assigned completion route
and preserve the handle, continuation, expected revision, snapshot, actor,
host-assigned result reference and execution evidence; an application answer
payload alone is not a complete submission.

| Recorded answer | Subsequent public advance in the Human profile | What it does not establish |
| --- | --- | --- |
| Defer | Completes this run with a typed BLOCKED Terminal. | Successful partition planning, document application or Phase completion. |
| Cancel | Completes this run with a typed CANCELLED Terminal. | Document application, Git effects or cancellation of another workflow. |
| ProvideResolution | Owning advance observes the independently verified answer and actual interpretation data, then continues the saved whole frontier through planning/reasoning to selected Preview. | The answer acknowledgement alone is not a completed split, applied documents or permission for the client to execute stages. |

The native issue/answer sequence retains HumanPending then HumanAnswered.
Defer/Cancel termination is a separate native successor after the answer; a
submission acknowledgement must not be mistaken for that later Terminal.

## Closed answer payload

The application answer schema is `sm-workflow.split-human-direction-answer.v1`.
Its envelope has only `schemaVersion` and `payload`. The payload contains
`choice`, whose only field is `value`. Valid values are `ProvideResolution`,
`Defer` and `Cancel`. ProvideResolution also requires a `resolution` object
with exactly `identity` and `revision`; the other two choices have no resolution
field. Extra fields and undeclared choices are rejected. The encoded answer
payload is bounded to 4 KiB of valid UTF-8; references are canonical and bounded.
CNCF separately owns the enclosing Decision-result contract.

A resolution reference names separately stored interpretation data. It is not
the Human answer result reference, a command permission, a new AI scope result
or executable planner authority. The current public API does not offer a
client operation for writing that data or its checkpoint rows. Do not access
SQLite or inject storage records from an AI skill.

## Independent Human evidence

### Reasoning-specific question

In the connected three-workflow assembly, a saved reasoning `DecisionNeeded`
issues `SplitHumanService.presentReasoningHumanDirection`, with Required
capability `reasoning-human-direction-capability` and input type
`SplitPhaseReasoningHumanDirectionRequest`. This is not a scope question: its
closed input retains the original context, complete unknown-item WorkOrder,
saved AI result and whole planning frontier. The input schema is
`sm-workflow.split-reasoning-human-input.v1`, bounded to 1 MiB of valid UTF-8.
Return only the answer through the question's unchanged public
`submitSplitHumanDirection` completion; clients do not choose subsequent work.

ProvideResolution names separate host-supplied
`sm-workflow.split-human-reasoning-resolution.v1` data, linked to that exact
question and saved AI result. Its interpretation classifies every requested
unknown item in order, with rationale and evidence present in the canonical
Human answer. Workflow validates this data, selects planning/demand versions and
calculates packing. The original AI answer remains DecisionNeeded. Missing or
crossed evidence and incomplete classifications refuse before a native write.
There is no new client write Operation, storage grant or AI-skill DB access.

Reasoning Human completion atomically saves the canonical answer, adjacent
native state and selected successor. Its Defer/Cancel choices save the stopped
state in that same completion, unlike the scope route's later terminal append.
An identical reply observes the saved acknowledgement; a changed one refuses.
Subsequent advance, including another JVM, observes the saved Preview or
BLOCKED/CANCELLED Terminal without another AI issue or document effects. The
answer acknowledgement is not itself the Preview or Terminal. This direct
Operation contract does not establish Phase6 CLI/skill integration.

### Actual answer verification

An authenticated relay proves its participant identity, not Human consent.
Fresh completion requires the host's independent verifier to bind evidence to
the exact component instance, complete issued question, complete submitted
result and submitting participant, with the declared Human-answer purpose.
The evidence must come from that verifier, identify its issuer and Human
participant, and be valid against the current owning execution clock. A receipt
decoded from request JSON, an old verifier observation or a skill assertion is
not accepted instead.

Without valid evidence the completion must not append HumanAnswered or return a
successful acknowledgement. Verification is scoped to the actual owning call;
it is not reusable command, storage or execution authority.

## Observation and uncertain recovery

Pending advance returns the same canonical issued Decision rather than issuing
another. An identical retained submission is an observation of the original
complete answer and its native history; it requires the original participant,
but does not ask the verifier for new consent. Changed answers, changed evidence
references, foreign participants, crossed contexts and corrupt retained facts
are refused rather than treated as a fresh submission.

Defer/Cancel Terminal observation restores the exact committed result without
repeating the terminal append. These paths do not allocate another task or
perform document/Git effects. A postcommit readback or acknowledgement failure
is indeterminate: preserve the error and later use supported observation. It
does not prove rollback and must not trigger automatic resubmission.

ProvideResolution processing preserves the original complete native lineage
and all candidates. Its owning publication and retained reconstruction are
connected in the paired Human assembly selected by the normal factory. Restoration
copies saved downstream facts without fresh interpretation, allocation or
ranking and does not grant a new native transition or Terminal. Do not use a
passive decoded checkpoint as an execution capability.

## Remaining acceptance

Additive scope/Human-derived planning/reasoning-to-Preview has positive Service/
CLI and physical SQLite-reopen evidence. Five selected APPLY/reopen cases also
passed in981 and whole-three-family982 passed1,171/1,171 across138 suites. The
new APPLY cases start from labelled private known-work Start setup; they do not
establish every Human-derived APPLY branch, independent ingress or default entry.
The normal factory is installed in current direct-Operation examples, including
scope-to-reasoning and distinct reasoning-Human successors. These use labelled
test-owned Human evidence, not a deployed production interaction channel.
Document-conflict semantic/Human completion is not installed. Integrated checks,
independent acceptance and rendered/packaged manual verification remain open.
Phase5/6/8 retain mapping/skill/broader cost and transport work; measured comparative
AI-cost acceptance is not a Phase2 gate. Supporting tests alone do not supply
practical PHASE-2 independent acceptance.
