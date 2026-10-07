# GoalPhase human interaction boundary

Status: Phase 2 implementation in progress. Explicit host-gated preparation and
answer Operations exist; this note itself admits no grant or production human
verifier. The automatic stop-question implementation validated by 569 is
retained unchanged.

Internal interface progress: focused-572 packaged and passed 191 tests/24 suites
for a separate typed answerable question, closed answer and host-only current-call
verification seam. See the [interface journal](../journal/2026/10/2026-10-01-human-question-verifier-contract.md).
Successful verification examples remain fixtures. Managed immutable question
retention passed focused-573 (197 tests/25 suites); managed answer-plus-receipt
retention, exact retry and physical reopen passed focused-576 (201 tests/26
suites), using the shared DataStoreSpace and independent answer Create grant.
See the [answer retention journal](../journal/2026/10/2026-10-01-managed-human-answer-retention.md).
Focused-582 additionally packaged and passed 204 tests/27 suites for the CML
`prepareGoalPhaseHumanDecision` and `recordGoalPhaseHumanAnswer` bindings, gated
by explicit host presentation/verifier dependencies, with actual managed
Service/ordinary CLI retention and physical reopen. See the
[public Operation journal](../journal/2026/10/2026-10-01-public-human-decision-operations.md).
Those scenarios still use fixture verification. They do not establish native
pending-question publication, actual human issuer/verifier, production default
activation, automatic human progression or answer/history resume.

Native publication had a concrete CNCF prerequisite, not a skill-routing issue.
Focused-586 now validates a separately kind-bound `WorkflowManagedIssuedDecision`
and `WorkflowComponentStorage.bindDecisionC` on the actual shared managed SQLite
route (83 tests/5 suites). Existing WorkOrder wire/API remains kind-restricted.
Focused-594 validates a separately kind-bound `WorkflowManagedDecisionCompletion`
and `WorkflowComponentStorage.bindDecisionCompletionC` (130 tests/9 suites),
including exact answer/history retention, physical reopen and owned-claim
release after pre-native failure. Default WorkOrder completion refuses Decision.
Focused-595 additionally validates the separately declared native
`WorkflowDecisionCompletionServiceOperation` through actual Service execution
(131 tests/9 suites), including committed answer retention after response
projection failure and replay refusal. Consumer question/answer integration
remains incomplete. Invocation 596 refreshed the producer API into the consumed
SNAPSHOT; focused-597 compiles the consumer and validates its closed native
answer payload codec (38 tests/7 suites). This is not a native suspension/resume
binding. A Decision wire codec alone is insufficient.
See the [native boundary inventory](../journal/2026/10/2026-10-01-native-human-decision-boundary.md)
and [publication progress](../journal/2026/10/2026-10-01-native-decision-publication.md).
See [completion and validation recovery](../journal/2026/10/2026-10-01-native-decision-completion-validation-recovery.md).
See [public native completion](../journal/2026/10/2026-10-01-public-native-decision-completion.md).
See [consumer artifact and answer payload](../journal/2026/10/2026-10-01-native-decision-consumer-artifact-and-answer-codec.md).
Focused-602 defines the new `goal-phase-human-decision-v3` CML revision with
distinct HumanPending/HumanAnswered projections, a human required capability
and `submitGoalPhaseHumanDecision` Provided Operation (187 tests/26 suites).
Old v2 ABI fixtures are preserved separately; no stored instance migration or
native runtime activation is claimed. See the
[native CML contract](../journal/2026/10/2026-10-01-native-human-cml-contract.md).

## Evidence and responsibility

CNCF `SecurityAuthenticationProvenance` records provider/trusted-local origin
in-process, separately from public value equality. Its own contract explicitly
excludes human consent and effect authorization. `GoalPhaseParticipantAdmission`
admits exact provider/trusted-local User or Service principals for actor-only
work. This is suitable for ordinary semantic submissions, not a proof of human
choice. A Service principal may be an AI worker or an authorized relay.

The implemented `GoalPhaseClosureDecisionTemplate` creates a question with
`resultType=None`, `human-stop-only` completion and host routing metadata.
`GoalPhaseClosureDecisionRecord` verifies that same stopped intent and absence of
a result type. Its stored question is not an accepted answer or authorization.
The CML still does not declare `recordGoalPhaseDecision` or
`authorizeGoalPhaseClosure`. The separately declared human preparation/answer
Operations retain answerable questions and independently verified receipts;
they do not upgrade these V1 stop records or issue closing authorization.

Responsibilities must remain separate:

| Boundary | Responsibility | Must not infer |
| --- | --- | --- |
| CNCF ingress | Admitted current submitting principal and authentication origin | Human choice, consent, or command authorization |
| Host interaction verifier | Independently issued, attributable exact human interaction evidence | Human status from request flags, roles or an AI's statement |
| Workflow | Resolve actual pending question; compare answer, actor, revision and receipt; retain outcome | Caller-selected storage, progression or an implicit grant |
| Command authority | Independently admitted bounded command intent/catalog and validity | Authorization from a question or an ordinary answer |
| Skill | Return the issued typed result or relay the attributable reference requested by Workflow | Select storage writes, assert verified human origin, or choose transitions |

## Host verifier seam

Use a provider-neutral, current-call-scoped host verifier. The host, not a
skill or JSON payload, supplies this dependency when assembling the service.
It resolves an opaque interaction reference from its authoritative evidence
source and verifies issuance. It must not accept a caller-provided receipt
body, `explicitHuman=true`, a principal attribute, copied authentication data,
or a caller-selected verifier/provider. Its result is usable only in the
current owning Component/Subsystem call, not a context-free reusable grant.

The expected binding is derived by Workflow from retained records, not accepted
as command input. It includes:

- owning Component instance, Workflow identity/version and instance;
- current instance revision, exact question identity and question schema;
- complete intent/snapshot/candidate reference and the submitted answer;
- the authenticated submitting principal and independently attributed human;
- interaction purpose (selection, ordinary Decision answer or bounded command
  authorization), issuer/evidence identity and validity interval;
- the exact human-visible question/choice contract and relevant target scope.

The verifier must prove that the attributed human saw this exact admitted
question/scope and chose this exact answer. A relay may submit a reference,
but the human and relay remain distinct identities and the verifier must
admit that relay for this receipt. Successful ordinary actor admission is only
a prerequisite, never a default human-verification result.

The verification outcome is one of verified, refused or indeterminate. Missing,
expired, wrong-purpose, wrong-actor, mismatched-answer/snapshot/question,
unavailable or throwing verification cannot publish an accepted answer or
authorization. Verification alone cannot write storage or execute commands.
No real verifier implementation or human channel is currently proven. A test
verifier remains labeled a fixture and cannot establish production readiness.

## Question contract before accepting answers

Do not reinterpret an existing V1 stop-only question as an answerable native
Continuation. Neither change `resultType=None` in place nor manufacture a
`ContinuationResult` with an invented type/operation. Replaying an ordinary
review Result also cannot answer it.

An answerable question requires a separately admitted typed contract: exact
declared choices or bounded answer schema, human-visible scope, result type,
completion Operation and expected revision. The Workflow determines the next
transition from that declared contract. The verifier validates the same
contract; it does not choose Workflow transitions. Preserve old stop records
as observations rather than silently upgrading them during reopen.

This contract is a prerequisite to exposing the human entry, not a reason to
disable ordinary issued-work completion or the existing automatic stop path.

## Immutable answer and effect separation

Retain one immutable answer record under the exact question identity. It
contains the admitted typed answer, derived Workflow/intent correlation,
independently verified interaction reference/attribution and derived receipt.
An answer and its receipt are one record; do not introduce a second mutable
status row solely to separate them. Declare a separate answer collection and
explicit Create/read grants; the existing Decision-metadata collection/grant
does not automatically authorize answer writes.

Equal retry returns the same committed answer after exact readback, without
re-verifying a different identity, overwriting the record or replaying a
semantic result. A conflicting answer at the same question cannot replace the
winner. The host verifier must reject receipt reuse for another scope; exact
Workflow binding also rejects another question. If Create or committed
observation is uncertain, withhold success and recover the same row through
ordinary observation. Do not compensate by deletion or reallocation.

The answer-record Operation never grants Git authority or executes Git. A
separate bounded authorization entry must prove human authorization purpose,
permitted managed command, exact candidate/intent and validity. Only the
Workflow's managed command executor may consume that authority, with its own
guarded admission, retained effect receipt and uncertain-effect recovery.
An ordinary answer cannot refresh an expired authorization.

Answer acceptance and Workflow advancement must not be confused. If the
admitted implementation uses separate immutable answer retention followed by
advance, interrupted follow-up must recover the accepted record without
resubmitting human choice or duplicating effects. Any promised joint
answer/history transition instead needs an explicitly granted canonical native
transaction; no postcommit append or dummy cursor is a substitute.

## Executable acceptance requirements

Before enabling the entry, prove all of the following on the shared managed
storage route, using the actual verifier as well as adversarial fixtures:

1. Exact separately issued human evidence accepts the declared answer; a
   provider-authenticated User or Service without it is refused.
2. Request flags, principal attributes, authentication copies, forged receipt
   bodies and foreign providers cannot supply human provenance.
3. Wrong question, answer, actor/relay, purpose, snapshot, revision, target,
   expiry or missing/unavailable verifier refuses before a Create/effect.
4. Exact current-call Component/grants/lease checks remain required; an escaped
   call, foreign collection or pool cannot write an answer.
5. Create/readback/equal retry/conflicting races, uncertainty and host reopen
   retain exactly one immutable answer/receipt with no overwrite.
6. Interrupted follow-up and stale advancement use actual retained answer and
   current Workflow records; they never fabricate approval or repeat effects.
7. Service, ordinary launcher CLI and MCP/server use the same admitted input,
   verifier and typed outcome. Transport syntax confers no authority.
8. Old stop-only records remain stop-only. Ordinary semantic submission and
   automatic question preparation continue without requiring human approval.

Phase completion remains unproven until the actual human issuer/verifier,
answerable question contract, managed writers, authorization/command recovery
and required production transports satisfy these requirements.

## Native question payload and adjacent publication preparation

`GoalPhaseNativeHumanQuestion` now projects the full native question into one
typed Decision wire: candidate/context/routing in its request, and exact bounded
choices plus a distinct immutable intent identity in a closed presentation
envelope. There is no separately written question side record on this route.
The generated human Required/Provided declarations are checked exactly; the
existing observation and V1 stop-only routes remain distinct.

Preparation derives a separate native identity and adjacent pending revision
from an exact unsuspended fixture base, with `HumanPending` as the generated
composite marker. It is pure preparation, not proof of accepted-review
provenance or execution of the complete CML constituent lifecycle. Its caller
must independently supply the retained current-call accepted review; production
integration is still open.

Actual managed shared SQLite publication/reopen and hostile contract/stale/
foreign-owner cases pass in the new six-scenario spec. Focused 606 passed 20/20;
GoalPhase accumulator 607 passed 193/193 in 27 suites. These tests use an honest
synthetic predecessor and do not establish production human verification,
native completion or CLI/server/MCP activation. See the
[payload and reopen journal](../journal/2026/10/2026-10-01-native-human-question-payload-and-reopen.md)
for exact evidence and required next integration boundary.

## Native current-call publication adapter

The private `GoalPhaseManagedNativeHumanQuestion` adapter now derives native
publication from the actual retained accepted review and host selection under
current-call participant/grant/lease checks. Its adjacent successor recovery
checks complete canonical history and immutable payload; no historical prefix
is persisted. Copied authentication, foreign collection, escaped call,
conflicting choices and later advancement are refused.

The integration spec runs actual managed Start, review issue/completion and
SQLite reopen, with an explicit test-only selection installer and invocation
probe. Focused 612 passed 29/29; accumulator 613 passed 194/194 in 27 suites.
This proves the private adapter on a real managed call, not production
follow-up activation, a public native answer endpoint, human verification or
CLI/server/MCP completion. See the
[current-call publication journal](../journal/2026/10/2026-10-01-native-human-current-call-publication.md).

## Native answer successor preparation

`GoalPhaseNativeHumanAnswer` separates pure issued-question/result/suspension
checks and adjacent `HumanAnswered` calculation from host human-verifier
admission. The successor remains Active and grants neither Step closure nor
command permission. No native answer writer or production verifier is enabled.
The new pure scenario and existing GoalPhase accumulator pass (614: 10/10;
615: 195/195 in 27 suites). Actual managed human admission, atomic completion,
receipt retention and reopen recovery remain required; see the
[answer preparation journal](../journal/2026/10/2026-10-01-native-human-answer-preparation.md).

## Native answer managed completion and explicit Factory opt-in

`GoalPhaseManagedNativeHumanCompletion` connects the generated native Provided
Operation to current-call selection/participant admission, an explicit host
human verifier, pure successor selection and CNCF atomic Decision completion.
The configured product Factory opts in only with `nativeHumanVerifier`; this
does not synthesize an issuer or change the observation answer Operations.
The typed resultReference identifies independently issued human evidence.
The selected closing remains event-free, leaves the Step Active, and grants no
Git or other command authority.

Actual Start/review/question/completion/reopen runs pass through configured
product Factory and Subsystem dispatch. Unissued reference and authentication
copy refuse; one typed answer and adjacent HumanAnswered history survive pool
reopen. Current accumulator 620 passes 195/195 in 27 suites. Human issuance is
still test-only. Native receipt query/uncertain response recovery, production
issuer/verifier, automatic question follow-up and actual CLI/server/MCP remain
open. The native completion retains answer/evidence reference, not an extra
full-receipt side record. See the
[managed completion journal](../journal/2026/10/2026-10-01-native-human-managed-completion.md).

## Read-only native completion observation

CML now declares QUERY `GoalPhaseService.getGoalPhaseHumanDecisionResult`.
The configured host-record Factory binds it independently of a human verifier.
Actual call/selection/participant/grant/lease admission precedes pure historical
projection of the retained native Decision, answer/evidence reference and exact
completed history prefix. Later current progression does not rewrite the saved
completing revision. Missing or inconsistent records refuse; no human evidence
is reissued, expiry renewed, prefix persisted or command permission granted.

Normal CncfRuntime CLI dispatch and Subsystem dispatch return equivalent stored
results after SQLite reopen without a human verifier. This is in-process CLI
evidence, not standalone launcher/CAR or server/MCP acceptance. The result is
historical completion observation, not recovery of the original full receipt.
Native completion's current-call reads/verifier admission now precede pure
closing selection. Current accumulator 627 passed 196/196 in 27 suites; see the
[query and pure closing journal](../journal/2026/10/2026-10-01-native-human-completion-query.md).

## Public native mechanical advance

CML COMMAND `GoalPhaseService.advanceGoalPhaseHumanDecision` now exposes the
managed current-call question publication/recovery path. Its own native COMMAND
type carries only the retained review identity. Workflow owns choices,
candidate/context derivation, adjacent suspension and immutable retry recovery.
The configured host-record Factory binds issuance independently of human answer
verification, leaving the observation preparation Operation unchanged.

The successful native-answer integration path now issues through configured
product Factory and ordinary in-process CncfRuntime CLI, rather than a private
publication probe. Service retry is equal; caller-injected choices and copied
authentication refuse. Accumulator 630 passed 196/196 in 27 suites. Automatic
review follow-up, complete CML constituent execution and standalone launcher/
server/MCP remain open; see the
[public advance journal](../journal/2026/10/2026-10-01-native-human-public-advance.md).

## Read-only authority assessment for automatic native follow-up

`GoalPhaseClosureAuthority.assessReviewedC` separates current snapshot and
recorded-authorization assessment from legacy stop-only Decision construction.
Its `Assessment(intent, Recorded | Missing)` is read-only information, not an
execution grant. Both assessments recheck instance, candidate bytes,
authorization and retained proof before return. Stale or malformed recorded
authority refuses rather than becoming a missing-authority question.

The shared legacy path retains its lookup order and request restrictions.
Native publication must occur only after the read-only assessment finishes,
with separate managed predecessor/actor admission; this seam is not yet wired
to automatic review follow-up at the assessment-only slice (633). The
[assessment journal](../journal/2026/10/2026-10-01-native-human-authority-assessment.md)
records the 46/46 focused and 199/199 GoalPhase regression evidence and limits.

## Connected automatic native follow-up

The configured host selects `AcceptedReviewFollowup.NativeHuman` to connect
accepted review completion to fresh read-only authority assessment and managed
native question publication. Existing `StopOnly` remains default, and an
ambiguous simultaneous legacy automatic preparation/native policy is rejected.
The Skill does not select this branch or supply an assessment.

Only Missing assessment reaches publication, with a new current-call check of
the exact assessed predecessor intent. Recorded remains read-only information,
not execution authority. Review and question are separate managed commits:
recovery of review revision three repeats fresh assessment; recovery of its
exact pending revision four observes the retained question without fresh
snapshot/authorization claims. Neither path reruns semantic review.

Configured Factory/Subsystem completion, normal in-process CLI recovery,
interrupted snapshot acquisition, changed-byte refusal, equal retry and physical
reopen pass with lease-checked fixture bytes and zero Git effects. Accumulator
635 passed 199/199. At that slice, native real-Blob integration and actual
standalone/server/MCP transport acceptance remained open; see the
[automatic follow-up journal](../journal/2026/10/2026-10-01-native-human-automatic-followup.md).

## Automatic native follow-up with actual Blob and retained catalog

The native integration executable now registers submission and artifact catalog
through public Operations and uses the configured Factory's actual Blob reader
over retained SQLite records. Normal completion publishes a native question;
interrupted completion retains accepted review only. Changing the actual pinned
payload bytes makes recovery refuse without question publication. Restoring
those bytes permits in-process CLI recovery of the adjacent native question.

Equal retry and pending-question recovery after physical database pool reopen
do not read Blob again, including when Blob access is unavailable. Existing
manual and fixture-byte branches remain. Focused validation 636 passed 46/46;
GoalPhase accumulator 637 passed 199/199 in 27 suites, with zero Git effects.
This test-only slice changes no production code or CML/ABI and grants no command
authority. Production human issuance, complete CML constituent execution and
standalone launcher/server/MCP acceptance remain open. See the
[real-Blob follow-up journal](../journal/2026/10/2026-10-01-native-human-automatic-blob-followup.md).

## Public advance shares fresh assessment and pending recovery

Configured `advanceGoalPhaseHumanDecision` now uses the same retained-review
follow-up execution body as native review recovery, with its own unchanged
generated Operation and bounded command decoder. Factory-owned configuration
selects the entry; neither Skill nor caller supplies policy, choices or an
assessment. Accepted review revision three receives fresh snapshot and
authorization assessment before question publication. Its exact pending
revision four is observed without renewing bytes or authority.

Manual real-Blob and fixture paths now refuse changed candidate bytes before
publication, succeed after exact restoration, and recover the same pending
question after pool reopen even with Blob access unavailable. Focused 639
passed 46/46 and GoalPhase accumulator 640 passed 199/199 in 27 suites. Default
automatic StopOnly and legacy observation/preparation behavior remain. No
command permission, production human issuer or standalone transport acceptance
is claimed. See the
[advance assessment journal](../journal/2026/10/2026-10-01-native-human-advance-assessment.md).
