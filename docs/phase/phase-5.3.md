# Phase 5.3: Common Workflow Operation Loop and Minimal Data Access

Status: completed
Planned: 2026-10-12
Owner: sm-workflow Phase 5.3 owning development task.
Update rule: retain original scope/history; close acceptance only with actual Operation evidence and formal local release.
Depends on: Phase 5.2 complete migration
Delivery order: Phase 5.2 -> Phase 5.3 -> Phase 6 skill integration

## Goal

Align GoalPhase, SplitPhase and RepositorySync with one common Start -> Advance
-> Request -> Submit -> chat display -> Advance loop. Deliver business-typed
inputs, one session ID per execution, chat-oriented submission results and
direct access to only the data required by the current operation. Remove the
superseded routing and unnecessary information retrieval, not just their public
parameters. Replace Phase 5.2's uniform `arg1: string` business carrier while
retaining its removal of control copies and unnecessary checks.

The 2026-10-12 session correction also replaces the many-executions-per-session
candidate lookup with one session identity per workflow execution. Start fixes
and returns that identity. Subsequent calls locate the execution directly, not
by counting candidates and asking the caller for another identifier. This
supersedes the earlier plan to preserve Phase 5.2's ambiguous-target selectors.

Removing repeated control information does not require erasing business input
types. JSON may represent a typed request in a transport or CLI; it is not the
business contract of every Operation. This Phase is an explicit interface
redesign after Phase 5.2, not an extra completion condition retroactively added
to that Phase. Existing implementation, evidence and history remain attributable.

## Common execution loop — 2026-10-12

Adopted priority: reduce AI cost and implementation/debugging complexity before
minimizing transport round trips. Keep Start and Submit separate from Advance
even though fetching the next work requires another call. Next-work selection
has one owner, Advance; do not add eager-advance flags, combined response paths
or batching as a Phase 5.3 optimization. The client can display an accepted
submission and call Advance mechanically, without another AI decision turn.
Actual AI-cost savings are an objective, not an unmeasured performance claim.
Reconsider transport optimization only if later measured cost justifies it,
preserving the same internal responsibilities.

All three Workflow families follow this contract; existing operation names may
remain. Common behavior does not require a new generic Workflow engine or a
second set of generic public endpoints.

```text
start*(business input, optional proposed session ID)
  -> canonical session ID + start result
advance*(session ID)
  -> work Request | Human question | Wait | Terminal
work Request: perform the requested work
  -> specified submit*(session ID, typed result)
Human question: obtain the actual human answer
  -> specified submit*(session ID, typed answer)
submit* -> chat-oriented submission result
  -> display it in chat
  -> on accepted submission, advance*(session ID) and repeat
Wait -> wait for the stated condition; no unconditional polling loop
Terminal -> display the outcome and finish
Error/rejected submission -> display the outcome and handle it; no success-path advance
```

Advance owns next-work selection and deterministic progression. A Request tells
the caller the work/question, required result/answer contract and submission
Operation. The caller does not select internal stages or retrieve additional
control records to discover the return route. Submission consumes the answer
and saves its resulting state; it does not dispatch the next semantic work or
automatically call Advance. Successful submission needs no extra human prompt
before displaying its result and advancing.

Phase 5.3 implements this Operation behavior and connected specifications for
all three families. Phase 6 implements the actual skill/client loop and chat
rendering from that contract. Do not transfer missing runtime progression or
response semantics to Phase 6 or expand the ongoing Phase 5.2.

### Submission results are intended for chat display

Every submit Operation returns a typed result containing its actual acceptance
outcome and a human-readable message intended to be displayed in chat. The
message concisely identifies what was received/handled and the actual outcome;
use the accepted work/answer and completion result already available. It is not
a raw JSON dump, internal identifier list or acknowledgement token that leaves
the skill to reconstruct what happened.

Keep the machine-readable outcome separate from display text. The client displays
the returned message (with ordinary UI formatting) and then advances after
acceptance; it does not parse prose to determine control flow or ask an AI to
invent an acknowledgement. Errors state the actual reason; successful intake
does not claim unexecuted tests/effects, Step/Phase release or whole-Workflow
completion. Advance supplies the next Request/Wait/Terminal.

Produce the display information as part of submission from the actual result.
Do not add a status-query operation, history scan, extra storage read, model call
or another full-result copy solely to compose the chat message. Precise public
type/field names are fixed with the common response table in P53-01.

## Operation contract

Apply this contract to the normal GoalPhase, SplitPhase and RepositorySync
Operations. Fix concrete names/types from their actual business consumers during
design; the roles below are not new mandatory wrapper class names.

| Operation role | Declared input | Runtime responsibility |
| --- | --- | --- |
| Start | Named business parameters or a declared business record; optional proposed session identity. | Establish one execution and its session identity, returning the canonical session ID with the start result. Allocate an ID when none is supplied. |
| Work/result submission | Session ID and the result type requested by the issued work. Use declared alternatives when one endpoint accepts several result kinds. | Consume through the existing completion path and return acceptance outcome plus a chat-oriented message; leave next-work selection to Advance. |
| Human answer | Session ID and the declared answer type, including actual choices or business answer fields. | Apply the answer to the pending decision and return acceptance outcome plus a chat-oriented message; leave next-work selection to Advance. |
| Advance | Session ID; no additional execution/request/context selector. | Load the execution/current continuation directly and return the next work Request, Human question, Wait or Terminal. |

An ordinary string remains valid for a business value such as text or a name.
The rejected design is a universal string containing an undeclared JSON object.
Likewise, a type alias around that universal string, `Any`, an untyped map or an
opaque business-result blob does not restore a declared business contract.

Session ID is an opaque locator, not a copied revision, snapshot, issued envelope
or unchanged-state certificate. It may be carried by the explicit client invocation
context rather than duplicated in business fields; every call still supplies it.

## Remove caller-side ambiguity resolution — 2026-10-12

Apply this change to all fifteen normal public Operations. A usable chat/host ID
may be proposed at Start only when it can identify this one execution. Starting
another execution in the same chat uses a distinct session ID; chat identity may
remain origin metadata. Do not attach a second execution to an existing execution
ID, silently replace it, or return a candidate list for the caller to resolve.
An actual conflicting Start is an ordinary input/state contradiction, not a new
replay, idempotency or tamper-prevention feature.

| Public selector to remove | Workflow-owned replacement |
| --- | --- |
| `selectionIdentity` on Start | Bind the actual business start and its admitted host selection/allocation to this execution at entry. Do not search unrelated chats' matching selections and ask the caller to choose an internal record. |
| `instanceIdentity` on advance | Direct lookup of the single execution identified by the returned session ID. |
| `contextIdentity` on Split advance | Use the current partition/planning context retained by that execution. |
| `requestIdentity` on result/Human submission | Use the current pending work/decision for that execution and the invoked result Operation. |

Retain necessary internal identities and domain references. Removing their public
echo does not remove the storage relationships they represent. If one execution
cannot determine its current context/request, fix the owning state representation;
do not choose the latest/first candidate or recover by requesting another public
selector. Internally spawned work retains its own references with the owner.
No new same-execution parallel-answer protocol is introduced by this change.

Remove the four selector decoders, candidate-count/extra-selector branches,
advertised parameters, examples and tests that require the caller to resolve
those ambiguities. Do not merely suppress errors, make selectors optional, rename
them as opaque continuation data, or move the selection burden into a skill.
Repository/Phase identity, branch, mode, actual result content, Human choices and
genuine artifact references remain business inputs. Missing sessions, incompatible
answers and contradictory configuration fail at their existing consuming boundary.

Processing removal is part of P53-06, not just a public signature change:

- Delete the normal-route processing that enumerates matching Start selections,
  session instances, partition contexts or pending requests to resolve caller
  ambiguity. Replace it with the execution's bound start data and direct owning
  references to its current context/work/decision.
- Delete the associated candidate collections, filtering by optional selectors,
  zero/one/many dispatch, extra-selector errors and dedicated helper/adapter code
  once their normal callers are migrated. A direct lookup may still report a
  missing record; it must not rebuild a candidate-selection pipeline.
- Do not hide the old pipeline behind a new session-ID wrapper, perform it in
  advance on every call, retain it as an unused compatibility path, or relocate
  it into the client. Start establishes the binding once; later state transitions
  maintain the actual current references.
- Remove obsolete fixtures and expectations together with their production
  paths. Keep domain computations, actual business choices and underlying
  authority/storage controls; this is removal of execution-target ambiguity
  machinery, not a blanket ban on collections, queries or business validation.

The independent integrated review traces the normal calls into storage and state
consumers to confirm the old processing has actually disappeared. Use ordinary
source/call-path inspection and the connected specifications already planned;
do not create another integrity checker or a separate review cycle per helper.

## Remove unnecessary information retrieval

Trace each normal call from its input through the state/data it actually consumes.
Keep direct reads of the execution, its current pending request/context, required
business data and actual authority/effect inputs. Remove:

- scans of all sessions, selections, executions, contexts or pending requests
  performed only to rediscover a target already bound to this execution;
- duplicate loading/decoding of the same available data across wrappers and
  delegates, where no intervening state change or owning transaction requires
  a fresh read;
- reads of old histories, declaration/source copies, unused metadata or
  diagnostics performed only for comparison, unchanged-content proof or unused
  output fields;
- client fetches of internal records or status solely to form a submission,
  infer the next operation or display the submit result.

Delete those reads and the adapters, unused data plumbing and dedicated fixtures
they support. Merely caching, prefetching, moving them into Start/client code or
hiding them behind a helper does not satisfy removal. Start still reads the
actual business entry data it needs. Existing transaction/concurrency owners may
read current state as required for correctness; do not substitute a stale cached
value or remove a necessary business/authority check to reduce read counts.
Use call-path review and proportionate connected specs, not a new access ledger,
instrumentation framework or exhaustive performance campaign.

## Scope

| Item | Required outcome |
| --- | --- |
| P53-01 | Define one finite input/response table for all three workflows: Start business fields and returned session ID, typed results/answers, Advance outcomes and chat-oriented submission results. |
| P53-02 | Align CML declarations, generated Operation/Provided metadata and actual parameter binding with those business types; expose meaningful names, fields and required/optional semantics to introspection/help. |
| P53-03 | Connect the normal factory/Operation path to the actual business consumers and existing completion/storage path without reintroducing caller control envelopes. |
| P53-04 | Keep JSON/CLI serialization at the existing transport boundary; interpret it as the declared business input before business execution. Migrate callers, codecs, examples and fixtures together. |
| P53-05 | Remove the superseded generic JSON-string business entry points and their dedicated compatibility machinery; validate the connected contract and hand it to Phase 6. |
| P53-06 | Start establishes and returns one session ID per execution; replace candidate selection with direct execution/current-context/current-request lookup and remove all four caller ambiguity selectors across the normal routes. |
| P53-07 | Align all three families with the common Start/Advance/Submit loop; every submit returns a truthful chat-oriented message and machine-readable acceptance outcome before the next explicit Advance. |
| P53-08 | Remove unnecessary information retrieval and its supporting code from runtime and affected callers; retain only actual current-call data needs and required owning transaction reads. |

For result endpoints that serve multiple issued work kinds, choose supported
business variants explicitly. The selected work supplies the interpretation
context; the client must not echo internal state to prove which variant is valid.
Do not declare a vague result type while privately accepting arbitrary payloads.

## Boundaries

- Phase 6 owns runtime command lifecycle integration and live synchronization
  between Phase documents and the encoded plan. This Phase supplies the common
  loop and typed semantic work/results that it consumes; do not add command
  execution/log-transcription duties or plan snapshots/comparison parameters to
  AI Requests. Those Phase 6 capabilities add no Phase 5.3 completion gate and
  do not expand active Phase 5.2 work.
- Preserve Phase 5.2's minimum actual inputs and control-copy removal while
  replacing its many-target session resolution with P53-06. Required
  business values, choices and genuine evidence/artifact references remain inputs;
  internal revisions, context snapshots and predetermined destinations do not.
  Keep session identity available explicitly or through actual invocation context
  when declaring the new types. Advance has no new business input but requires
  the execution's session ID. Phase 5.3 implements Start identity assignment and
  return; Phase 6 connects optional chat-ID acquisition, caller retention and
  command/REST propagation in its [lifecycle contract](phase-6.md#session-identity-lifecycle--2026-10-12).
- Interpret input at its natural boundary. Missing required fields, incompatible
  business values and actual contradictions fail there; do not add a second
  validation framework, management hashes, copied-state comparison or TTL.
- Use existing CNCF parameter/type/codec facilities. A dependency Component or
  external library change discovered to be necessary is a proposal for developer
  judgment, not an implicit expansion of this sm-workflow Phase.
- CNCF Phase 105 remains hygiene with unchanged external interfaces. Do not
  transfer this public-contract redesign into that Phase or assume it authorizes
  a new CNCF API. Coordinate with its existing completion fixes where applicable.
- Preserve business progression and completion semantics; migrate the routing
  association needed for one session per execution. The redesign does not add workflow
  features, stronger replay/recovery guarantees or a new persistence engine.
- Phase 6 still owns actual skills, thinking modes and CLI/skill wiring. Phase 5.3
  supplies the typed Operation contract and applicable existing adapter behavior;
  it does not build a new general transport system or provisional skills.

## Migration

Deliver one current contract. Migrate all affected normal callers and generated
descriptions; do not retain a parallel `arg1: string` business endpoint, legacy
envelope reader, dual API or silent fallback for future skills to adopt. JSON
serialization of a declared business value is normal transport, not compatibility
with an undeclared JSON-string business contract.

Preserve earlier Phase evidence and historical records. Record any old in-flight
work that requires abandonment and a new execution; do not silently reinterpret
it or add intermediate recovery to meet this redesign. Creating the plan does
not reset any active Goal, base, epoch, counter or review history.

## Connected implementation order

1. Read the Phase 5.2 delivered interfaces/consumers. Define the common input/
   response table and connected Executable Specifications together, including
   one session per execution, chat-oriented submit results and consumed data.
2. First connect the complete GoalPhase Start -> Advance -> Request -> Submit
   -> display-ready result -> Advance -> outcome route through the normal
   factory. Apply the same structure to SplitPhase and RepositorySync, including
   their Human/Wait/Terminal branches, in the same coherent delivery.
3. Align generated metadata, binding, transport codecs, callers and current
   documentation. Delete the old universal-string and candidate-selection paths,
   unnecessary reads and their dedicated helpers/fixtures in that delivery.
4. Run affected specifications and meaningful regressions together, then review
   the integrated implementation independently. Recheck repairs at their actual
   scope, without per-helper review cycles or unrelated repeated Phase testing.
5. Hand the actual public input/response table, common-loop examples, submit
   display contract and evidence to Phase 6. Skill authors consume this contract,
   not Phase 5.2's string carrier or an internal-state query protocol.

## Acceptance

- [x] P53-01: GoalPhase, SplitPhase and RepositorySync have an explicit Operation
  table covering business Start fields, requested results, Human answer types
  and the absence/necessity of Advance inputs.
- [x] P53-02: Generated declarations and introspection expose the same business
  names/types/fields as the actual binders and consumers; no universal JSON-string
  carrier or renamed opaque wrapper substitutes for them.
- [x] P53-03: Each normal workflow progresses from typed Start through requested
  result/answer consumption and the relevant next state using existing storage.
  Session-owned control values are not supplied or compared by the caller.
- [x] P53-04: Existing JSON/CLI representation maps to the declared business type;
  malformed required values fail at consumption. Specs exercise ordinary typed
  Operation use and the actual affected serialization/binding path, not only a
  fixture that unpacks JSON behind a misleading string declaration.
- [x] P53-06: Each normal Start accepts an optional proposed ID, assigns one
  when absent and returns the canonical session ID with its actual start result.
  Advance, result submission and Human answer use that ID and business data only.
- [x] P53-06: Two independent developments with different session IDs can
  progress through interleaved normal calls in shared runtime/storage without
  asking either caller for another selector or affecting the other's target.
  Cover one chat starting distinct executions with distinct IDs as applicable.
- [x] P53-06: Public CML/metadata/binders/callers and current examples contain
  no selectionIdentity/instanceIdentity/contextIdentity/requestIdentity ambiguity
  selectors; their internal owning relationships remain. No renamed selector,
  candidate-list fallback or first/latest-target choice survives.
- [x] P53-06: Integrated source review confirms removal of the old candidate
  discovery/filter/count/selector pipeline and its dedicated helpers/adapters/
  fixtures, including hidden wrappers and unused compatibility branches.
  Normal calls follow the session's direct execution/current-state references.
- [x] P53-05: Connected callers/examples and metadata use only the new contract;
  obsolete generic-string business paths and dedicated fallback tests are removed.
- [x] P53-05: Affected validation and independent integrated review pass with
  actual evidence. Phase 6 dependencies and handoff describe the delivered types.
- [x] P53-07: All three normal families execute Start -> Advance -> Request ->
  typed Submit -> display-ready result -> Advance through the intended outcome.
  Request, Human question, Wait, Terminal and errors have consistent control
  meaning; Submit does not dispatch the next work or silently call Advance.
- [x] P53-07: Work and Human submissions return an actual acceptance outcome
  and useful chat message. No raw-JSON-only acknowledgement, fabricated success,
  additional status fetch or model call is required to display it. Phase 6 owns
  actual chat rendering; the Phase 5.3 spec consumes the same returned content.
- [x] P53-08: Integrated source/call-path review confirms removal of unnecessary
  scans, repeated loads/decodes and comparison/unused-data reads on the connected
  routes, with obsolete plumbing removed. This is code removal, not caching or
  relocation; required current business and transaction reads remain valid.

## Completion — 2026-10-13

The complete P53-01–08 delivery covers all fifteen normal GoalPhase,
SplitPhase and RepositorySync Operations. The canonical acceptance checklist
above is closed by the actual evidence in
[Phase 5.3 closure](phase-5.3-closure.md), not a reduced subset or generation-only acceptance.

- Accepted implementation Step: `4401aa131c2ad41e9f0a608352079c11e3c08e91`,
  S53-common-operation-loop / S53-01, 112 paths.
- Exactly one epoch-1 Phase full review; all five original blockers closed
  through focused repairs. Latest independent focused03 PASS confirms the
  final twelve test/probe corrections without new findings.
- Actual final repository-full02: 95 suites, 616 PASS, zero failed/aborted/
  canceled/ignored/pending; SBT/wrapper exit0, shared lock released.
  Earlier full01 (599/616) and focused14 (110/111) remain failures in history.
- Actual managed scripted evidence: three real SBT-effect scenarios passed.
  Current CAR input: Cozy56OK269WARN0FAIL/static24OK11WARN0FAIL; warnings
  retain their existing nonblocking dispositions.
- Formal LOCAL release closure binding: `smwf-phase53-closure@1`.
  This completed status is release content, effective with the matching
  verified native release commit, not merely by editing this working file.
  Component version remains `0.1.0-SNAPSHOT`; no artifact publication or push.

Cumulative Phase repair3 / Step repair1, all prior records, original base and
predecessor nine/history/exhausted3-of-3 are preserved. No counter reset or new
repair frame. Phase5/5.1's explicitly approved-unverified FORMAL releases and
Phase5.2's formal predecessor meaning remain valid; no retrospective checks.

The two accepted nonblocking Hygiene records are persisted in the
[canonical Hygiene journal](../journal/2026/10/2026-10-12-phase-5.3-hygiene-follow-up.md).
There are no accepted Development Candidate items or unpersisted findings.
Authored packaged-ABI reference maintenance remains Hygiene, not a claim that
publication/client/CLI readiness was tested.

Concurrent Phase6–9/cost planning and its shared Phase index are preserved
outside this release; index synchronization is deferred to that planning
owner. This canonical Phase/checklist and closure remain authoritative.
Phase6 supplies actual `sm-*` skills/client wiring and use-based adjustment;
it is not started by this completion.

Historical planning note: creation of this Phase alone performed no
implementation, generation, SBT execution, Goal activation, commit or release.

## References

- [Phase 5.2: minimal interface and complete migration](phase-5.2.md)
- [Phase 6: thinking modes and skill integration](phase-6.md)
- [Current Operation declarations](../../src/main/cozy/sm-workflow.cml)
- [CNCF Phase 105: Workflow validation hygiene](../../../../dev2025/cloud-native-component-framework/docs/phase/phase-105.md)
