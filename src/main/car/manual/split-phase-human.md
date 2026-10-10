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
Canonical output uses `schemaVersion` and `payload`, with `choice.value` in the
payload. The choice alternatives are closed: `ProvideResolution`, `Defer` and
`Cancel`. ProvideResolution requires a `resolution` object with canonical,
bounded `identity` and `revision`; canonical Defer/Cancel output omits it.
The decoder consumes these typed fields, not an exact set of JSON keys: unused
annotations on the envelope, payload, choice or resolution reference are
ignored. Defer/Cancel also ignore an unused resolution field. Missing or
malformed consumed fields, a different schema and undeclared choices are
rejected. Ignored data grants no Human consent, command permission or executable
authority. The encoded answer payload remains bounded to 4 KiB of valid UTF-8.
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

The reasoning input reader consumes typed context and saved presentation strings,
not an exact set of unused JSON annotations or a reconstructed copy certificate.
Owning Workflow selection resolves the actual reasoning assignment and saved
result. Separate interpretation and stopped-terminal readers likewise ignore
unused fields without turning those fields into Human consent or execution
authority; required typed meaning and actual selected targets still apply.

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

Retained ProvideResolution requires its declared Built proposal, ownership and
handoff documents to exist and be typed-readable at their selected addresses,
including after physical storage reopen. Their complete bodies need not match
historical checkpoint copies. Compatible documentation changes do not recompute
the saved result, request new consent, rewrite output or advance native state.
Missing, malformed or differently addressed deliverables fail at the existing
document-read boundary; unrelated files are outside that lookup. This does not
certify the necessity of other remaining source/copy checks.

Human publication creates no application result-copy row. Retained reads derive
the native address from the genuine Human completion and consume the actual
native result and receipt states. Old full, malformed, crossed-reference or absent
application copies cannot select or reject that result and are not rewritten.
Old human-checkpoints.* metadata is unused; only actual document targets are
required. Native payloads ignore removed Question/Completion/Issued/prior-Scope
certificate fields. Original checkpoint and invocation mode come from the
independently observed Human source. Missing native receipt is actual absence;
native state/CAS prevents stale replay after native progress. Real native target
and required generated-document readability remain actual consuming boundaries.
This does not certify remaining source/frontier/resource checks as necessary.

This no-application-copy correction is authored but UNVERIFIED pending fresh
complete executable suites and a new independent connected review; earlier
bounded PASS evidence does not cover this changed input.
That authoring cutoff is now followed by bounded executable validation and an
independent connected source assessment. Its sole stale empty-row fixture finding
was corrected without restoring a copy row; the complete changed managed suite
then passed142/142. Runtime Help and full Phase acceptance remain open.

The owning Human successor selects storage using the actual current Subsystem
configuration, without comparing it to a saved construction target set. This
does not grant another collection or new consent; managed storage still owns
actual access and transition effects. This owning binding has bounded complete
executable validation and a fresh independent connected source assessment;
broader predicate removal, runtime Help and Phase acceptance remain open.

HumanResolution's native payload now carries SavedResult calculation data
without nested before/next histories. Old nested state members are ignored,
including absent or unreadable copies; actual states come from CNCF's outer
receipt. The payload decoder does not manufacture state or select a definition
from copied histories. Private preparation still supplies its genuine native
intent and actual effect owners handle state/CAS. Current SavedResult behavior
has bounded direct-Operation/executable validation and independent source review;
other predicate removal, runtime Help and Phase acceptance remain open.

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
