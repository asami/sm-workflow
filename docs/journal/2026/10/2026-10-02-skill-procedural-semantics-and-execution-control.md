# Skill Procedural Semantics and Execution Control

Date: 2026-10-02
Status: design principle

## Decision

Skill is a human-readable procedural description of work. A Skill executes the work it is given as a conceptually single-threaded sequential procedure.

A Skill MUST NOT own or model concurrency control. It does not acquire/release locks, coordinate competing workers, manage leases, resolve deadlocks, or decide retry/recovery caused by concurrent execution.

Concurrency, exclusion, execution ownership, durable coordination, continuation, admission, retry/recovery, and scheduling belong outside the Skill, in sm-workflow/CNCF Workflow and runtime mechanisms.

This strengthens the existing boundary:

> Skill owns planning semantics and human-readable procedure. sm-workflow owns software-development execution semantics. CNCF Workflow/runtime owns generic execution control.

## Why procedural

The top-level procedure should remain readable as an ordinary work instruction:

1. read the target;
2. understand the requested outcome;
3. perform the semantic work;
4. verify the result;
5. submit result/evidence.

This form is intentionally close to how a human explains work and is also suitable for AI execution. The Skill should not become a second state machine or concurrency runtime.

"Single-threaded" is semantic, not an implementation restriction. A provider may internally parallelize implementation details, but the Skill contract observes one sequential unit of work and must not coordinate concurrent Skill executions.

## Skill responsibilities

Skill has three complementary roles:

1. **AI-native work** — semantic reading, generation, review, classification, judgment support, and other work where AI capability is intrinsic.
2. **Ambiguous/non-routine work** — work not yet stable enough to encode as deterministic Operation/Workflow/StateMachine behavior.
3. **Human-readable top-level procedure** — preserve an understandable description of how the work proceeds even when lower-level steps are implemented by deterministic Operations, Workflows, human approval, or sub-Skills.

As recurring work becomes deterministic, its mechanics should move downward into Operation/Workflow/StateMachine rather than making the Skill more complicated. The Skill may remain as the readable top-level procedure.

## Execution boundary

```text
Human / AI-readable Skill
  sequential procedure
        |
        +-- semantic AI work
        +-- Operation
        +-- Workflow / StateMachine
        +-- Human Approval
        +-- Sub-Skill
        |
        v
sm-workflow / CNCF Workflow
  execution ownership
  concurrency / exclusion
  continuation
  admission
  retry / recovery
        |
        v
CNCF Runtime
  locking / lease
  persistence
  job / recovery infrastructure
```

The Skill does not need to know whether another Skill or agent is touching the same repository, Aggregate, resource, or file. The execution layer must arrange safe execution before exposing work to the Skill.

## Consequence for Skill authors

Do not add lock/retry/wait-for-other-worker logic to Skill instructions. If correct execution requires such logic, that is evidence that the responsibility belongs in Workflow/runtime support.

This principle also supports the broader evolution path:

```text
ambiguous work
  -> AI/Skill provisional operation
  -> identify deterministic parts
  -> move deterministic parts to Operation/Workflow/StateMachine
  -> retain Skill as human-readable procedure where useful
```
