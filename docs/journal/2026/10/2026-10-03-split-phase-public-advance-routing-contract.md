# SplitPhase public advance: routing and production connection

Date: 2026-10-03
Scope: existing sm-workflow Phase 2 and unchanged Goal
Status: source-grounded technical design handoff; not ordinary PLAN or acceptance

## Continuity

The latest human direction permits read-only independent review, keeps
implementation in the parent, and keeps tests in the registered runner.
The repaired AI-checkpoint frontier passed representative 826 (11 tests) and
affected 827 (49 tests), followed by independent diagnostic closure PASS.
Those results remain evidence for their original repaired bytes, not for the
public Operation described here. See the
[repair closure](2026-10-03-split-phase-ai-checkpoint-repair-closure.md).
This inspection ran no test, review, commit or runtime command and changed no
production source. The existing Goal, Phase identity, comparison bases and
unknown ordinary review-history counters are preserved, not reconstructed.

## Current connection gap

`src/main/cozy/sm-workflow.cml` already declares the generated
`SplitPhaseService.advanceSplitPhase` command and its result. The earlier
[consumer plan](2026-10-02-split-phase-public-advance-consumer-plan.md)'s
statement that the declaration is absent describes its earlier source state.
The declaration is not a production implementation: the actual dispatching
advance adapter inspected here is still a private executable-spec probe.

`SplitPhaseManagedServiceAssembly.bindHostEntrySliceC` assembles only Start
and proposal assessment. `SplitPhaseFixtureServiceAssembly` deliberately does
not advertise advance. The default `impl/ComponentFactory` still selects
`GoalPhaseLauncherAssembly.factory`, whose configured factory installs one
GoalPhase service after initialization parameters have resolved. A working
internal checkpoint reader does not close any of these connections.

`SplitPhaseManagedPlanningCheckpoint.Port` returns an internal observed record
and native instance, not `WorkflowInteraction`. Its checkpoint may describe an
active instance awaiting later planning work. CNCF `projectTerminalC` requires
the matching **Completed**, unsuspended instance. Neither a checkpoint nor the
pure planner's `Planned` disposition establishes application completion.

## Public advance ownership

The request remains the existing generated `SplitPhaseAdvanceCommand`:
instance identity and exact context reference only. No caller-selected stage,
candidate winner, command selector, permission or completion flag is added.

The production Operation must validate its generated ABI and one JSON command,
then run under its owning native call. It selects current instance, context,
participant and the original purpose bindings before admitting work. A pending
interaction is loaded from retained issued work before any fresh enumeration,
allocation or issue. Missing or inconsistent suspended work is an error, not
permission to recreate it. An already completed instance is observed only with
its retained matching result. An indeterminate write/readback never triggers
an implicit retry inside the call.

For active unsuspended planning, the Workflow absorbs deterministic work until
one genuine external requirement or a completed application result. Mechanical
and AI-origin checkpoint observations remain distinct. Decoded passive snapshots
are not reconstructed into privately admitted executable planner outcomes.

| Planner disposition | Owning next action; not an AI-selected route |
| --- | --- |
| `PartitionSemanticRequired` | Issue the existing typed unknown-item/adjacency request; preserve known estimates. |
| `AllocationRequired` | Allocate mechanically using a distinct mechanical anchor, then consume the same enumeration. |
| `ProjectionRequired` | Separate semantic scope gaps from repository/numbering Decisions; preserve the whole mixed frontier. |
| `HostEvidenceRequired` | Read and evaluate declared host evidence; do not ask AI to manufacture evidence. |
| `ObjectiveRecordRequired` | Select the required host policy/data record; do not ask AI to choose policy or prices. |
| `ReasoningRequired` | Issue only unresolved reasoning judgments; rerun deterministic ranking after their admission. |
| `HumanDecisionRequired` | Issue a genuine native Decision retaining competing authority projections. |
| `PartitionUnavailable`, `CandidatesUnavailable` | Retain the refusal and route the required human decision; do not claim successful application. |
| `Planned` | Validate and publish/apply the selected complete documents under existing authority before projecting Terminal. |

This is a routing contract, not a claim that all rows have current public
capabilities. CML currently declares partition semantic assessment and the
older novel-work assessment. It does **not** yet provide purpose-specific
contracts for scope-gap/reasoning follow-on work. The older request's
digest-oriented work/edge fields must not be relabeled as those new requests.
Those action/input/result/completion contracts, and the corresponding native
Decision/application transitions, must be defined in CML before implementing
their public branches. No hand-written endpoint may substitute for generated
public declarations.

## Observable executable-spec obligations

1. Invoke the real generated public Operation through normal service dispatch,
   not the private test probe; decode its output with CNCF's canonical
   `WorkflowInteractionJsonV1` variant codec.
2. Repeated advance with pending work returns the same retained interaction,
   including original continuation, input, expected revision and completion
   route; history and issue count do not change.
3. All-known input performs no semantic issue. Unknown input issues only its
   exact gaps. Admission of one semantic reply exposes the next real need,
   including mixed scope/Decision and reasoning cases, without losing other
   candidates or returning the saved checkpoint as Terminal.
4. Reopen uses original purposes and complete retained data, not current naming
   policy or new allocation. Foreign instance/context/participant, missing rows
   and same-reference fact drift reject without effects. Pre-commit failure
   rolls back; committed-readback uncertainty returns no success or retry.
5. Only actual authorized document publication/application can establish the
   completed instance and matching Terminal result. A pure selected proposal
   remains nonterminal.
6. Default post-resolution factory assembly exposes GoalPhase and SplitPhase
   without duplicate services/operations and advertises only installed MCP
   operations. Both borrow the Subsystem's single initialized pool; neither
   opens SQLite or builds another pool in `create_Core`.

CLI/server/MCP and separate-process recovery, genuine human ingress, generic
packaged `sm-*` skills, legacy `cncf-*` coexistence, Workflow-owned external
commands, ordinary Step acceptance and final Phase release remain required.
The next implementation frontier must cover its generated action contracts
and real Operation/assembly consumers together; this journal does not admit a
read-only facade as completion or reset any retained review/repair budget.
