# Skill Specification, Skill Logic, and Execution Control

Date: 2026-10-02
Status: corrected design principle

## Correction

The earlier formulation "Skill is a human-readable procedure" was too broad. Human-readable top-level work procedure belongs to the **Skill Specification**, not to executable Skill Logic.

The corrected separation is:

- **Skill Specification**: human-readable purpose, responsibilities, semantic work, expected inputs/results, and top-level work description.
- **Skill Logic**: thin semantic worker / adapter. It connects to Workflow, performs only the AI-native or ambiguous semantic work requested by the current WorkOrder/Continuation, and returns typed Result/Evidence.
- **Workflow / StateMachine**: executable procedure and control semantics: sequencing, branching, iteration, closure, continuation, admission, retry/recovery policy, and execution coordination.
- **Runtime**: execution ownership, exclusion/locking/lease, persistence, scheduling, job/recovery infrastructure.

## Skill Logic execution model

Skill Logic MUST NOT reproduce the top-level procedure from the specification as its own control flow.

Its normal shape is deliberately thin:

```text
Workflow
  -> Continuation / WorkOrder
  -> Skill Logic
       interpret bounded request
       perform semantic AI work
       produce Result / Evidence
  -> completion Operation
  -> Workflow decides next action
```

A Skill Logic invocation handles the work assigned to it locally and sequentially. It does not coordinate concurrent Skill executions. "Sequential" here is a local execution assumption for one WorkOrder, not ownership of the overall procedure.

## Responsibilities

Skill Logic is appropriate for:

1. AI-native semantic work such as reading, generation, review, classification, and judgment support;
2. ambiguous/non-routine semantic work that cannot yet be represented deterministically;
3. adapting the bounded Workflow request/context to that semantic work and returning typed Result/Evidence.

Human-readable top-level procedure is documentation/specification. When it becomes executable control flow, Workflow/StateMachine owns it.

## Prohibited responsibilities

Skill Logic must not own:

- Workflow sequencing or next-action choice;
- loops such as review -> repair -> re-review;
- closure/admission decisions;
- concurrency, locks, leases, execution ownership, or deadlock handling;
- retry/recovery coordination;
- durable progression;
- local substitutes for Workflow state.

If Skill Logic needs any of these to be correct, treat it as a Workflow/runtime modeling gap.

## Evolution

Ambiguous semantic work may initially be performed by Skill Logic. As behavior becomes deterministic, move it to Operation/Workflow/StateMachine. This makes Skill Logic thinner; it does not preserve the old control flow merely for readability.

The human-readable explanation remains in Skill Specification and related process/design documentation.
