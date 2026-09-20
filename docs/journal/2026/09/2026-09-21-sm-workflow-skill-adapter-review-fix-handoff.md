# sm-workflow Skill Adapter Review Fix Handoff

- Date: 2026-09-21
- Status: Review-fix complete; focused re-review passed
- Source review identity: `sm-workflow@8b4eca3980536441d615dcf869aae6f0d48ccb7f`
- Review disposition: `FINDINGS`
- Fixed blocker set: `CB-SMWF-SKILL-001`, `CB-SMWF-SKILL-002`,
  `CB-SMWF-SKILL-003`
- Primary design:
  [sm-workflow Skill Adapter Design](../../notes/sm-workflow-skill-adapter-design.md)

## Purpose

The 2026-09-21 focused review confirmed the three-layer ownership model but
found that the Skill-facing invocation surface was not yet singular. This
handoff records the bounded documentation repair and the exact contract that
subsequent implementation and executable specifications must consume.

The repair does not implement WorkflowMapping persistence, planning-document
commit orchestration, public Skills, runtime providers, or a new transport.

## Fixed contract

### Human-selected start

`sm-goal-phase`, `sm-split-phase`, and `sm-repository-sync` are separate
human-selected entry points. Direct Skill selection or explicit host/client
selection supplies invocation authority. `SkillBundleManifest` resolves that
selection to exactly one registered profile-specific start Operation:

```text
sm-goal-phase       -> StartGoalPhase
sm-split-phase      -> StartSplitPhase
sm-repository-sync  -> StartRepositorySync
```

Recommendations such as `SPLIT_REQUIRED` and child-goal recommendations are
advisory. They may carry a typed source reference, but neither a Skill,
Workflow, terminal result, nor recommendation may create the selection record
or start the next WorkflowInstance.

### Continuation-selected completion

A profile start Operation maps its application input into CNCF
`WorkflowStartRequest` and returns the framework `WorkflowHandle` plus first
`Continuation` as `WorkflowInteraction`.

A non-terminal Continuation identifies the exact registered completion
Operation and typed result contract selected by the Workflow. The Skill
executes the one materialized semantic request and returns its typed result to
that Operation. Initial completion Operations include profile-specific work
result submission and application-specific Decision resolution.

The completion Operation performs common Result/Decision admission and invokes
the CNCF runtime's bounded internal advance evaluator. The evaluator absorbs
automatic transitions and admitted deterministic Operations until the next
Continuation or terminal result.

There is no separate Skill-facing generic `StartWorkflowRun`,
`AdvanceWorkflowRun`, or `advanceWorkflow(handle, response?)` protocol.
`advance` remains an internal runtime progression concept.

### Transport boundary

The Skill-facing contract is the registered typed sm-workflow application
Operation contract. MCP and CNCF launcher/CLI are adapters over the same
Operations:

- Phase 1 uses launcher JSON as a one-shot executable-specification fixture;
- Phase 2 adds MCP as the normal interactive transport and proves transport
  equivalence;
- public Skill semantics contain no launcher-specific argv grammar and no MCP
  routing logic;
- neither adapter may select the next Operation, duplicate Workflow state, or
  define a second Start/Continuation lifecycle.

## Blocker disposition

### CB-SMWF-SKILL-001 — fixed

The conflicting generic start/advance, profile-specific submission, and
Continuation handling descriptions were consolidated. Profile-specific start
and Continuation-selected completion Operations are now the only Skill-facing
progression surface. Runtime `advance` is internal.

### CB-SMWF-SKILL-002 — fixed

The public Skill no longer depends on a CNCF launcher command grammar. The
canonical contract is transport-neutral typed application Operations; MCP and
launcher/CLI are equivalent adapters with different deployment roles.

### CB-SMWF-SKILL-003 — fixed

The primary Skill adapter lifecycle and generation checklist now require exact
human-selected invocation authority, manifest binding, and rejection of
recommendation-only start.

## Updated documents

- `docs/notes/sm-workflow-skill-adapter-design.md`
- `docs/notes/cncf-workflow-protocol-application-specialization.md`
- `docs/notes/workflow-handle-and-advance-operation-boundary.md`
- `docs/notes/sm-workflow-design.md`
- `docs/notes/sm-goal-phase-workflow-definition.md`
- `docs/notes/sm-split-phase-workflow-definition.md`
- `docs/notes/sm-repository-sync-workflow-definition.md`
- `docs/phase/phase-1.md`
- `docs/phase/phase-1-executable-specification-checklist.md`

Historical `phase-1-checklist.md` and
`phase-1-pre-reconciliation-design.md` remain explicitly superseded records;
their old generic operation names are not current implementation authority.

## Development Candidates not implemented

### DEV-SMWF-SKILL-001 — WorkflowMapping persistence and recovery

Define a durable WorkflowMapping persistence and recovery contract that binds
planning source revision and stable planning references to WorkflowHandle,
Result, and Evidence references. Recovery must not depend on conversation
history, duplicate start, or manufactured completion.

- Owner: sm-workflow public Skill/integration phase
- Dependency: stable CNCF handle/status/history query and planning revision identity
- Resume condition: selected persistence/correlation contract plus executable
  restart and reconstruction scenarios
- Prohibited workaround: storing authority only in conversation history or
  copying the planning model into CNCF Workflow state
- Review-record SHA-256:
  `20804255086a6f2c0f96120a4103a636e6f38fd6e6bb1b30ae33eec849f4b444`

### DEV-SMWF-SKILL-002 — planning mutation and commit boundary

Define the planning-document mutation and commit boundary after Workflow Step
closure so reconciliation updates acquire workspace mutation authority, use
source-revision compare-and-set, and reach a durable clean-tree state without
making semantic Skill work execute the commit sequence.

- Owner: sm-workflow and sm-* Skill operational integration
- Dependency: `WorkspaceMutationLease` plus the selected planning persistence
  and closing flow
- Resume condition: executable Step commit -> reconciliation -> planning
  persistence/commit scenarios
- Prohibited workaround: leaving untracked planning mutations or silently
  auto-committing outside a typed Workflow contract
- Review-record SHA-256:
  `2c7f03854b8a2c84f40b2877457b7892722a60c4432c163596eabca00070fe70`

### DEV-SMWF-SKILL-003 — generated Skill acceptance

Add a post-Phase-1 executable acceptance plan for generated sm-* Skills
covering human-authorized profile start, bounded context projection,
WorkflowMapping drift/recovery, exact Continuation Operation submission, and
semantic planning reconciliation.

- Owner: sm-workflow public Skill generation phase
- Dependency: stable operation/transport contract and WorkflowMapping recovery
  contract
- Resume condition: an owning Phase/checklist adopts these acceptance scenarios
- Prohibited workaround: treating Phase 1 deterministic Provider fixtures as
  evidence that a production Skill can recover and reconcile planning state
- Review-record SHA-256:
  `4dd7f0a4474c86e116c9a6d97e6bf4ddd9485cefc00708693e9fe98c3161078f`

## Validation and re-review handoff

This repair is M2 because it reconciles multiple normative documents and the
protected Skill/Operation/transport boundary. Required closure evidence is:

1. targeted search showing no current normative document exposes a competing
   Skill-facing generic start/advance protocol;
2. link/source inspection across the primary Skill design, application
   specialization, three profiles, Phase 1, and the handle/Continuation note;
3. `git diff --check`;
4. one focused read-only re-review of the complete repair delta.

The first focused re-review identified and repaired
`SBR-SMWF-SKILL-001`: examples had placed `WorkflowStartRequest[...]`
directly in the profile Operation signature even though the selected contract
accepts `GoalPhaseStartInput` / `SplitPhaseStartInput` /
`RepositorySyncStartInput` and maps that payload to the common StartRequest
inside the Operation. The examples now use the profile input signature and
show the generic mapping as a separate step.

The same focused re-review also identified and repaired
`SBR-SMWF-SKILL-002`: the main architecture diagram still described CNCF as an
optional runtime adapter. The current design now names the CNCF common Workflow
runtime as canonical and treats one-shot/server bootstrap plus MCP/launcher as
deployment and transport differences, not alternate Workflow runtimes.

The second focused re-review identified and repaired
`SBR-SMWF-SKILL-003`: the Skill lifecycle required Manifest-based start
Operation resolution while the prior Manifest description bound only a
Workflow definition. Each entry binding now records the exact registered start
Operation identity and compatible application Operation contract version in
addition to the Workflow definition identity/version.

The same re-review identified and repaired `SBR-SMWF-SKILL-004`: the handle
design required invocation authority while the Phase 1 Operation signatures
showed only profile input. All profile start signatures now carry both their
typed input and `WorkflowInvocationSelection`; the Operation validates that
selection before mapping the profile input into the common StartRequest.

### Closure evidence

- Focused re-review disposition: `PASS`
- Current Boundary Blockers: none
- Minor Conformance Repairs: none
- Same-Boundary Repairs closed in this repair: `SBR-SMWF-SKILL-001` through
  `SBR-SMWF-SKILL-004`
- Targeted legacy-name search: pass; current occurrences of
  `StartWorkflowRun`, `AdvanceWorkflowRun`, and `advanceWorkflow` are limited
  to explicit rejection, migration, or negative executable-specification
  language
- Cross-document inspection: pass for the primary Skill adapter design,
  application specialization, three profile definitions, Phase 1 definition,
  Phase 1 executable-specification checklist, and handle/Continuation boundary
- Manifest/start binding inspection: pass; all three profile bindings identify
  an exact registered start Operation and compatible contract version
- `git diff --check`: pass
- Executable tests: not run; this is a documentation-only repair
- Review class: `M2`
- `RE_REVIEW_REQUIRED=no`

The Development Candidates above remain intentionally unimplemented and are
not Current Boundary Blockers for this documentation repair.

Do not commit from this handoff. A later commit workflow must bind the final
reviewed tree and the selected documentation paths explicitly.


## Follow-up: Step close request and proactive review evidence

A later boundary clarification refines DEV-SMWF-SKILL-002. The Skill is responsible for bringing program artifacts and its planning/management files to the latest state before requesting Step close. It may perform review proactively and submit the resulting typed, scoped review evidence with the close request.

`RequestStepClose` establishes closure intent. sm-workflow evaluates supplied evidence against closure policy, including scope, reviewed artifact revision, freshness, disposition, unresolved findings, validation and commit prerequisites. A scoped review may therefore be accepted as evidence while the Workflow still returns a full-review Continuation. Fresh full-review evidence should be reused rather than duplicated.

After the initial close request, requested Review/Repair/ReReview results resume closure evaluation automatically; the Skill does not repeat the close request. When requirements are satisfied, Workflow-owned deterministic validation/commit closes the program and management-file state together.

This supersedes the earlier interpretation of DEV-SMWF-SKILL-002 as planning mutation occurring after Step commit. The required ordering is planning/program state update -> close request with evidence -> missing semantic work as Continuations -> deterministic commit -> Step closed.
