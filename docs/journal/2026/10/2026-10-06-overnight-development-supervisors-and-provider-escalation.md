# Overnight Development: Dots, OpenClaw, Local Models and Selective Astra Escalation

Date: 2026-10-06
Status: exploration / future operation model
Scope: sm-workflow consumer/orchestration environment

## Context

The emerging sm-workflow architecture makes long-running unattended development more practical because semantic AI work is bounded by deterministic Workflow/StateMachine execution, managed validation, Candidate-Admission, Finding/Fix evidence and Convergence Guard.

The important question for local LLMs is no longer simply whether they are cheaper than a cloud model. Luna-class cloud execution has become cheap enough that local operation must justify itself by useful unattended throughput, privacy/offline properties, or the ability to consume otherwise-idle overnight compute.

Promising workloads include large volumes of HYGIENE changes and TEST_FIX work originating from HEAVY validation. These can tolerate latency if sm-workflow constrains the change and stops divergence.

## Layered responsibility

A possible 24-hour development stack is:

~~~text
Long-lived supervisor
  Dots or OpenClaw
        |
        v
sm-workflow
  Workflow / StateMachine
  Candidate-Admission
  Test/Fix/Review
  Convergence Guard
        |
        v
Execution providers
  Codex
  OpenCode -> local/self-hosted model
  cloud model
        |
        v
Control Center / Human Decision
~~~

Dots/OpenClaw should not reimplement Fix/Test/Review semantics. They keep work alive across time, start/resume workflows and move to the next item when a workflow completes or waits for human authority. sm-workflow owns convergence and validation.

## Local-model overnight candidates

### Hygiene

A local model may process a queue of bounded HYGIENE work overnight. The expected safety property is that the work remains HYGIENE; expansion into logic-changing work is visible through FixChangeAssessment/ConvergenceVector and can stop/escalate.

### HEAVY failure follow-up

HEAVY validation may take hours, so it is naturally compatible with overnight operation. A HEAVY failure can create bounded TEST_FIX work. The normal inexpensive validation chain should run before repeating the entire HEAVY campaign; the final required HEAVY validation is not skipped.

This makes local models attractive even when slower than Luna: the relevant metric is useful admitted work accumulated during unattended hours, not interactive latency alone.

## Dots possibility

If Dots supports low-cost long-lived project workers and can drive sm-workflow/tool execution, a useful model is one persistent Dot per active project.

The Dot need not be the sole semantic worker. Routine work can use the Dot's normal model, Luna-class execution or local providers, while selected high-value semantic boundaries can be escalated.

A particularly interesting policy is selective Astra use for:

- semantic review;
- design/proposal work;
- analysis of STALLED/DIVERGING convergence;
- difficult replanning where another implementation retry is unlikely to help.

Astra should not be used merely because a loop failed once. The value proposition is to spend stronger reasoning on high-leverage judgment after deterministic validation has filtered routine work.

Conceptually:

~~~text
routine implementation / hygiene / simple TEST_FIX
  -> cheap/local/Luna-class execution
  -> deterministic validation
  -> only surviving/high-value candidates
       -> Astra review when policy justifies it

STALLED / DIVERGING
  -> Astra analysis/proposal rather than blind stronger-model retry
~~~

The viability depends on actual Dots pricing and whether Astra escalation is available at an acceptable marginal cost.

## OpenClaw possibility

OpenClaw is a competing/complementary long-lived supervisor, especially when multiple projects, schedules or external events are involved.

A possible overnight schedule is:

~~~text
hygiene backlog
  -> sm-workflow runs

HEAVY validation
  -> failure -> bounded TEST_FIX workflow

repository/external event
  -> synchronization workflow

morning
  -> Control Center summary / human decisions
~~~

OpenClaw should remain loosely coupled: it invokes/resumes sm-workflow and reacts to typed terminal/waiting outcomes rather than understanding internal development-state transitions.

## Control Center boundary

Unattended operation must not imply autonomous resolution of every problem. Workflows that hit Human Decision, Convergence Guard stop, authority gap or unresolved design issue remain waiting.

The morning review should summarize facts such as completed/admitted work, waiting decisions, diverged/stalled workflows, HEAVY progress and relevant convergence/evidence. Human attention is spent on exceptions rather than supervising every routine turn.

## Evaluation metric

The useful comparison is not simply local-model token cost versus Luna price. Measure a complete unattended operating window, for example:

- candidates attempted;
- SMOKE/FOCUSED/ADMISSION success;
- TEST_FIX/REVIEW_FIX counts;
- convergence characteristics;
- human-decision/escalation rate;
- elapsed machine time;
- provider/model cost;
- useful admitted candidates available by morning.

This supports later comparison of:

- local model through OpenCode;
- Luna-class cloud execution;
- Dots persistent worker;
- OpenClaw orchestration;
- selective Astra review/proposal/escalation.

The desired outcome is an evidence-based routing policy rather than a fixed belief that local or cloud execution is always cheaper/better.

## Current decision

No implementation commitment is made here. Phase 6 remains focused on generic ExecutionRequirement/Provider Selection/ExecutionEvidence and should close without Dots/OpenClaw/OpenCode integration.

After that foundation is stable, overnight development is a strong driver scenario for concrete providers and long-lived supervisors. The first experiments should emphasize bounded HYGIENE and HEAVY-failure TEST_FIX workloads because their success/failure can be measured clearly by sm-workflow's deterministic validation and convergence evidence.
