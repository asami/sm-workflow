# Phase 5.2: Minimal Operation Interface and Complete Contract Migration

Status: completed
Planned: 2026-10-11
Completed: 2026-10-12
Depends on: the released Phase 5 / Phase 5.1 runtime
Delivery order: Phase 5.1 -> Phase 5.2 -> Phase 5.3 -> Phase 6 skill integration

Successor decision, 2026-10-12: [Phase 5.3](phase-5.3.md) replaces the uniform
JSON-string business input contract with declared business types before skill
integration. Phase 5.2 retains its current scope/history; the new redesign is
not added to its completion conditions. References below to the Phase 6 handoff
now pass through Phase 5.3, which preserves control-copy removal and determines
the final business-typed interface.

The refined Phase 5.3 delivery is the common three-Workflow Start -> Advance ->
Submit -> chat-display -> Advance contract, with one session ID per execution,
chat-oriented submission results and removal of unnecessary information reads.
Phase 6 connects the actual client/skill/chat loop. This successor scope does
not become an additional condition on the ongoing Phase 5.2.

## Goal and design principle

Complete the migration to one minimal public Operation contract: session
context and the actual parameters/results needed for the requested operation.
Workflow resolves its own stored requests, state and internal execution context.
Remove caller-supplied comparison material and the unnecessary checks it enables.
The small interface is also a structural means of preventing those checks from
remaining or returning; replacing a hash with a copy or another identity check
does not satisfy this goal.

Skills have not yet been developed. Phase 6 must receive one clear interface,
without old command/envelope alternatives that a new skill could accidentally
adopt. The user explicitly requires complete migration and no compatibility.
This supersedes the compatibility-retention and optional legacy-path disposition
in the [residual-check review handoff](phase-5-5.1-residual-checks-handoff-20261011.md).
Its observations remain the source inventory, not new formal review records.

## Scope

| Item | Required outcome |
| --- | --- |
| P52-01 / OP-01 | All three Start Operations consume genuine start parameters. Resolve authenticated participant and stored selection data internally; do not hide the old full command inside `input`. Keep a selector only where the operation genuinely needs the caller to select among alternatives. |
| P52-02 / OP-02 | Result submission resolves the issued request and stored destination internally. Consume semantic evidence references once; remove the requirement to echo them in outer execution evidence. Keep actual independent execution/reviewer evidence where needed, without manufacturing it from semantic references. |
| P52-03 / OP-03 | Public parameter definitions, decoder behavior and examples describe the same minimal contract. A host-session advance requiring no new input must not advertise mandatory `arg1`. Result submissions must not require an issued-envelope copy. |
| P52-04 / CB-01 | Remove reference equality and other unchanged-result proofs from internally produced planning/partition data and their consuming routes. Do not substitute whole-value, hash, serialized-copy or readback equality. |
| P52-05 / CB-02 | Decode consumed fields and report missing required values, invalid types or actual contradictions. Unused diagnostic/obsolete fields are not a reason to reject otherwise valid input and acquire no control or authority meaning. |
| P52-06 / DEV-01 | Fully migrate the old routes identified in the handoff. Move still-required behavior to the new route, then delete old entry points, full-command/envelope decoders, compatibility adapters, fallback branches, management-hash/copy checks and tests dedicated to their obsolete guarantees. No optional compatibility-retention branch remains. |

Update connected producers, consumers, storage codecs, operation descriptions,
fixtures, executable specifications and current usage documentation together.
Remove alternate identity-only/full-envelope input routes where they are legacy
contracts; preserve the one current session contract and genuine target selectors.
Do not keep a second public API, compatibility flag, dual writer, old-format
reader or silent fallback for the removed contract. Generic tolerance of unused
fields is not a legacy decoder: those fields must not select an old route or
affect execution.

Existing records needing continued use are explicitly migrated to the current
representation as part of the connected change, not serviced by permanent dual
runtime paths. Intermediate execution recovery is not a new requirement. Preserve
historical records and actual evidence; never silently erase, overwrite or mark
old results newly accepted to make migration succeed. Document any old in-flight
execution that requires a new start instead of promising unsupported restoration.

## Necessary semantics and boundaries

- A session identifies the workflow context; when supplied by the host it need
  not be echoed. A selector is legitimate only when actual ambiguity requires it.
- New business input, semantic results and real external artifact/evidence
  locators remain inputs. Stored revision, context snapshot, issued envelope,
  saved participant/key and predetermined result destination are not caller
  certificates. Resolve their required internal uses within Workflow.
- Preserve actual access checks, selected effect scope, normal state transitions
  and underlying concurrency controls. Reject contradictions where the inputs
  are consumed; do not add a separate unchanged-state certification stage.
- Do not add hashes, full-copy retention/comparison, internal TTL expiry,
  verification stamps or new ledgers to compensate for deleted checks.
- Retain Phase 5.1's normal-use limits: no guarantees of duplicate/concurrent
  answer handling, unique winners, complete-history reconstruction, identical
  replay or intermediate recovery. Abandonment followed by a NEW execution
  remains the supported restart route.
- Actual skill/CLI integration and thinking modes remain Phase 6. No direct AI
  executor, general transport redesign or unrelated upstream overhaul is added.

Phase 5 / 5.1 closure and evidence keep their recorded meaning. Preserve existing
Goal identity, original base, PLAN epoch and review/repair history; this plan
neither resets them nor reopens the completed phases. Prior exhausted counts
are not converted into a new project-lifetime prohibition by this document.

## Session identity handoff to Phase 6 — 2026-10-12

Later design correction on the same date: the multiple-target selectors
described below are Phase 5.2 implementation facts, not the final skill contract.
Phase 5.3 P53-06 replaces them with Start returning one session ID per execution
and removes selectionIdentity, instanceIdentity, contextIdentity and requestIdentity
as public ambiguity selectors. Phase 6 connects that replacement contract.
This successor work does not expand the ongoing Phase 5.2 implementation.

Phase 5.2 retains the runtime contract that Start, advance, result submission
and Human answers receive a session identity, either explicitly or through the
actual invocation context. No-business-input advance does not mean no identity:
the host must already have supplied it. Workflow resolves stored instances and
pending requests; its current selectors are inventoried for removal by Phase 5.3,
not carried into new skills as a compatibility contract.

Refine the delivered input table and handoff to identify how each Operation
receives the session and returns any necessary target references. Distinguish
Operation-level validation from actual client integration. Phase 6 owns the
[session identity lifecycle](phase-6.md#session-identity-lifecycle--2026-10-12):
usable chat/host identity, fallback allocation responsibility, caller retention,
command/REST propagation and separate IDs for separate executions. Phase 5.3
owns Start assignment/return and replacement of ambiguous-target lookup.

This clarifies the existing handoff, not a new Phase 5.2 allocator, client,
transport implementation or additional validation campaign. Phase 5.3 defines
business input types and the corrected one-session-per-execution contract. Completed
Phase 5/5.1 acceptance and ongoing Phase 5.2 scope/history remain unchanged.

## Connected implementation order

1. Use the handoff inventory and current callers to define the finite public
   input table for Start, advance and result submission in GoalPhase, SplitPhase
   and RepositorySync. Describe only the new contract. Trace old routes for
   removal and identify any records that actually need conversion.
2. Implement the connected route and matching Executable Specifications in one
   batch, including definitions, internal resolution, result consumption and
   required storage changes. Pass minimal inputs through the normal factory
   first; then address the remaining branches of the same route.
3. Remove superseded routes, compatibility machinery and obsolete test fixtures
   in that delivery. Update current examples so they cannot teach Phase 6 to
   send old commands or comparison fields. Preserve historical journals as
   history with an explicit pointer to this replacement contract where needed.
4. Run affected specifications and meaningful regressions together, then review
   the integrated change independently. Recheck fixes at their actual scope;
   do not create a test/review/commit cycle per helper or repeat unrelated old
   Phase acceptance. Record the delivered interface and hand it to Phase 6.

These steps are work ordering, not separate approval gates. The compatibility
decision is already made by the user; discovering an old caller is work to
migrate, not a reason to ask again whether compatibility must be retained.

## Acceptance

- [x] GoalPhase, SplitPhase and RepositorySync each progress through normal
  Start -> requested work -> minimal result submission -> advance -> intended
  wait/terminal outcome. Fixtures do not wrap an old command/envelope to pass.
- [x] Host-session advance works without redundant input, and public definitions
  reflect the real required/optional parameters. Actual ambiguous-target cases
  use only the necessary selectors.
- [x] Split semantic results work without duplicating their evidence references
  or predetermined destination; genuine independent execution evidence retains
  its distinct meaning.
- [x] CB-01 / CB-02 checks are removed on the connected routes. Unused fields and
  separately constructed valid internal values do not fail solely for those
  reasons; actual consumed-input contradictions and scope violations still fail.
- [x] All old paths in the bounded inventory are removed or their required
  behavior is migrated. No supported legacy input, adapter, storage fallback or
  management-hash/copy proof remains. Source/caller inspection and affected
  specs confirm this; no new integrity checker is introduced.
- [x] Current documentation and generated/public Operation descriptions teach
  only the new interface. Phase 6 consumes this contract, with no compatibility
  wrapper delegated to skills or clients.
- [x] Connected validation and independent review record actual outcomes and
  limitations. Completion does not depend on implementing unrelated backlog or
  restoring the guarantees excluded by Phase 5.1.

## Delivery and closure — 2026-10-12

All P52-01–06 acceptance items above are supported by the sole epoch-1 Phase
full review and its subsequent three-spec focused closure. The implementation
Step `S52-minimal-contract-migration` was accepted and committed at
`4d22f350e3880dc8b9873db42f41915299a75654`, with original Phase base
`fdd016c1a2dfeef67030f5cbfb0a40474c2d7c43` retained.

The final repository-full `sbt --batch test` passed 95 suites / 626 tests,
zero failures or aborts; SBT and shared wrapper exited 0 and released the lock.
The preceding 628-test run remains recorded as 622 PASS / 6 FAIL. Its six
obsolete expectations were corrected in three specs: excluded replay and
partial/intermediate recovery expectations were removed, and an unused
Proposal annotation is accepted. Normal first file effects and concrete file
protection remain. No production or common-framework check was added.

Independent review lineage: `phase52-full-review-01@1` PASS, then
`phase52-phase-focused-review-01@1` PASS; retained ledger revision 7, epoch 1.
There are no unresolved Phase blockers or new Hygiene/Development Candidates.
Existing HYG-S52-STEP-01 remains OPEN and nonblocking in its canonical journal.

The [closure checklist and delivery ledger](phase-5.2-closure.md) records the
actual acceptance basis, validation limits, local release boundary and preserved
planning. It supplements this canonical Phase document; original startup,
authority and Goal/checklist binding are not replaced or reset. The formal
local Phase release includes both records and the three corrected specs.
Native Git history and the release receipt establish its actual commit result.

Use current public CNCF completion and Decision Operations only. No new CNCF
capability, private `completeC` entry, replacement engine, verifier or copied
input proof is required. Apparent need for a new capability is first examined
as possible local deviation. Parallel CNCF development is outside this scope.

The component remains `0.1.0-SNAPSHOT`; this is a formal local Phase release,
not remote publication or version normalization. Phase 5 / 5.1 retain their
approved-unverified formal-release meaning. Original nine findings, complete
history and original repair 3/3 remain; existing Step cycle 6 and accepted
Phase cycle 1 are retained, with no new repair frame. Phase 5.3 and Phase 6
remain successor work, not additional acceptance conditions or automatically
started tasks. Shared Phase-index synchronization is deferred while concurrent
successor planning is preserved.
