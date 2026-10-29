# Phase 6.10 M2: named selection and retained work capture

Date: 2026-10-29. Status: semantic source implementation; native checks pending.
No independent review, live model call, provider availability fetch, publication,
Git mutation or workflow-store operation was performed by the worker.

## Configuration and actual selection

The registry contains `standard`, `cost` and `quality`, logical revision `1`.
Standard borrows the existing CNCF `workflow.execution.profiles` mapping and
`sm-workflow.execution.origin`. The two opt-in Component keys are
`sm-workflow.goal-phase.profiles.cost` and
`sm-workflow.goal-phase.profiles.quality`. Their values use the existing
`cncf.workflow-execution-profile-mapping.v1` schema and decoder, either as normal
structured configuration or a JSON string. No new model table, config file reader
or provider mechanism is introduced. The configured logical default remains
`sm-workflow.goal-phase.profile`, with absence resolving to standard.

The host must explicitly declare supported concrete candidates for the requested
semantic reasoning classes. Cost's intended bounded-work Terra High / complex Sol
and quality's stronger Sol assignments remain host mappings to qualify; this
change installs no active Terra or stronger-effort setting. Missing opt-in mapping,
class or origin fails explicitly. No provider fetch is needed to discover known
configuration absence. The present native environment has no opt-in mappings or
execution origin enabled; actual worker model and effort remain unknown. Packaged
agent TOMLs still configure gpt-6.1-sol / medium, which proves no observation.

## New boundaries and preserved work

Start's explicit logical intent is retained as an optional native Start payload
field. Its original schema and defaulted constructors remain compatible. At fresh
semantic issuance, current explicit Advance input wins, then saved explicit Start
intent, then current config, then standard. A saved config-origin Start choice does
not pin later configuration for unissued work. An Advance parameter applies to
new work in that call, without mutating the retained Start. The normal finite
caller forwards the optional profile on Start and GoalPhase Advance.

`GoalPhaseNativeWorkContinuation._issue_assigned_c` invokes the new selection
adapter only after the missing-issued-row and native preconditions. It delegates
disposition to the existing CNCF resolver and saves the capture with the same
WorkOrder through `issued.suspendC`. Review independence and context isolation,
capabilities, risk, reasoning class, Plan, acceptance, checks, repair limit and
completion authority stay with their existing owners. Standard forced-inline
behavior is preserved; opt-in forced substitution that differs from the requested
mapping refuses explicitly.

The capture records logical identity/revision/source, semantic class, mapping
identity/revision, configured descriptor and initially selected descriptor.
Descriptor controls retain configured effort, without treating it as observed
execution. Standard's explicitly declared finite fallback is retained only when
its disposition supports that existing path. Opt-in profiles have no alternate
substitution. Failed/unavailable attempts are still explicit caller outcomes.

New requests carrying this field use native work-request schema v5. Historical
v2/v3/v4 requests keep the field absent and decode with their original facts;
profile data cannot be smuggled into those older schemas. The actual retained
pending-work branch does not rerun selection, so a current config/profile change
cannot replace an already issued request or disposition.

## Public caller and observed attribution

The generated Goal Work view exposes optional `operatingProfile`; its Record is
copied from the retained business input. The existing extensible
`WorkflowIssuedWorkContext.context` Record carries the same optional capture.
The `WorkflowIssuedWorkContext` constructor, generic protocol members and required
execution schema remain unchanged. The shared direct-operation fixture compares
the original generic context unchanged and separately checks capture presence.

The real `NativeWorkflowCallerExecution.prepareC` bridge consumes this capture
before preparing its existing CNCF harness Session. It reconstructs the retained
class mapping using CNCF's normal mapping constructor, so a later caller mapping
cannot replace the selected model/effort. Restore validates the Session against
the same capture, without resolution. The public metadata reader accepts its
finite camel/snake aliases and rejects contradictions; opaque controls are not
renamed. Historical requests without this metadata follow the existing route.

At semantic completion, native admission checks observed profile identity and any
supplied observed model against the retained permitted selection. Missing mapped
execution observation fails instead of becoming a successful fallback. Effort is
not present in the existing execution observation schema: captured controls remain
configured facts, and actual effort remains unknown. No new evidence, genuine
execution or observed usage is fabricated by the implementation.

## Authored executable specifications

The runtime-selected `Phase610OperatingProfilesSpec` adds precedence across all
three names, actual canonical opt-in mapping decoding and Start/Advance receiver
consumption, fresh WorkOrder capture, baseline/independence/isolation preservation,
normal WorkOrder codec round-trip and historical absence, rebind refusal, missing
mapping/class/forced-substitution refusal, observed-profile mismatch admission,
and actual caller consumption despite a later changed mapping. Its unavailable
caller case proves opt-in has no alternate selection; a separate standard case
retains the configured fallback. The finite Python caller spec covers resume
profile forwarding. All descriptors/outcomes are specification-owned values.
No validation command was executed by the worker. Runtime Submit/Advance owns
checks, retained outcomes and acceptance. No Phase checklist box is changed.
M3/M4 endpoint metrics, usage acquisition and close projection are outside M2.
