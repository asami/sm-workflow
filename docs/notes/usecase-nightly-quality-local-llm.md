# Use Case: Nightly Quality Improvement with a Local LLM

Date: 2026-10-06
Status: future driver use case
Scope: sm-workflow
Related:
- docs/notes/managed-test-suites-and-verification-cost-guard.md
- docs/journal/2026/10/2026-10-06-overnight-development-supervisors-and-provider-escalation.md
- docs/phase/phase-6.md

## Goal

Use otherwise-idle overnight compute to perform bounded software-quality work with a local LLM while sm-workflow provides deterministic validation, convergence control and Human-in-the-Loop boundaries.

The initial purpose is quality improvement, not autonomous feature development. The local model is an optional execution provider whose usefulness must be demonstrated against cheap cloud alternatives such as Luna-class execution.

## Actors

- Long-lived supervisor: future Dots/OpenClaw-like process or equivalent scheduler.
- sm-workflow: authoritative Workflow/StateMachine, validation and convergence controller.
- Local execution provider: future OpenCode/Ollama-class adapter running a local coding model.
- cbd-support/CAR lint/static analysis: possible sources of quality findings.
- Cloud AI provider: optional escalation/review provider.
- Human developer: morning review/admission/decision authority.
- Control Center: future operational summary and intervention surface.

## Preconditions

- Phase 6 generic ExecutionRequirement / Provider Selection / ExecutionEvidence contract is available.
- The project has CNCF Validation/Test Metadata sufficient for SMOKE/FOCUSED/ADMISSION/FULL/HEAVY selection as applicable.
- sm-workflow can record Candidate revisions, FixIssue evidence and Convergence Guard observations.
- Local-model work is constrained to admitted work classes/scopes.

## Initial eligible work

The first rollout SHOULD prefer low-risk, measurable quality work:

- HYGIENE findings;
- TRIVIAL_COMPILE_FIX findings;
- lint/static-analysis cleanup;
- test hygiene;
- bounded warning cleanup;
- clearly attributable HEAVY-test failures suitable for TEST_FIX investigation;
- other explicitly admitted quality findings whose scope can be validated deterministically.

Ordinary feature implementation, architecture redesign and broad COMPLEX_LOGIC work are not initial targets.

## Normal nightly flow

~~~text
Nightly supervisor
  -> obtain admitted quality backlog
  -> start/resume one sm-workflow item
       -> local semantic work (HYGIENE / TEST_FIX etc.)
       -> Candidate
       -> SMOKE
       -> Slice FOCUSED when applicable
       -> Convergence Guard
       -> review/admission policy
  -> terminal/admitted: record and move to next item
  -> Human Decision / BLOCKED / DIVERGING: park item and move to next
  -> infrastructure wait: retain resumable state
  -> continue until nightly window ends
~~~

The supervisor does not implement Test/Fix/Review semantics. It only drives/resumes sm-workflow and reacts to typed outcomes.

## HEAVY validation path

HEAVY validation is a particularly useful overnight source of work:

~~~text
HEAVY
  -> PASS
       -> record evidence
  -> FAIL
       -> create bounded FixIssue/TEST_FIX work
       -> local LLM Candidate
       -> SMOKE
       -> FOCUSED / failure-focused deterministic validation
       -> Convergence Guard
       -> repeat bounded Fix loop as policy permits
       -> required final HEAVY rerun before claiming HEAVY success
~~~

The complete multi-hour HEAVY suite SHOULD NOT be blindly rerun after every small Fix if cheaper admitted validation can reject the Candidate first. Final required HEAVY evidence is never skipped.

## Safety and convergence

Local-model output has no special authority. The same Candidate-Admission and Fix Convergence rules apply regardless of provider.

A nominal HYGIENE task that expands into SIMPLE/STANDARD/COMPLEX logic, grows source/external-resource surface, repeatedly reopens findings, stalls, diverges, reaches the hard Fix limit or reports AI BLOCKED is stopped/escalated according to policy.

The initial rollout SHOULD prefer producing validated Candidates for morning human review rather than automatically expanding authority merely because the work ran unattended.

## Morning Human-in-the-Loop

The overnight process should leave a concise operational summary suitable for Control Center:

- attempted items;
- validated/admitted Candidates;
- Candidates awaiting review/admission;
- Human Decision items;
- STALLED/DIVERGING/BLOCKED items;
- HEAVY status and failures;
- provider/model usage and elapsed time;
- relevant Convergence/Finding evidence.

The human inspects exceptions and high-value Candidates rather than supervising each AI turn.

## Escalation

A local-model failure does not imply immediate stronger-model retry. sm-workflow first uses deterministic validation and Convergence Guard.

Future policy may escalate selected work to Luna/Codex or use a stronger reasoning model such as Astra for high-leverage semantic review, design proposal or analysis of STALLED/DIVERGING work. Such escalation is Provider Selection policy, not a Workflow semantic dependency on a named model.

## Evaluation KPIs

Evaluate the complete overnight window rather than model token price alone:

- quality items attempted;
- validated/admitted Candidate count;
- SMOKE/FOCUSED/ADMISSION pass rates;
- local completion rate;
- cloud escalation rate;
- TEST_FIX/REVIEW_FIX cycles;
- convergence trend and hard-stop rate;
- Human Decision rate;
- HEAVY failures resolved;
- elapsed machine time;
- local/cloud provider cost;
- useful Candidates available by morning.

A useful primary economic indicator is cloud AI cost per admitted/usable Candidate compared with an equivalent Luna-first workflow.

## Rollout

1. Candidate-only HYGIENE/nightly lint work; human reviews all results.
2. Add TRIVIAL_COMPILE_FIX and bounded TEST_FIX.
3. Add HEAVY-failure follow-up.
4. Use accumulated evidence to define which classes may receive greater unattended authority.
5. Consider Dots/OpenClaw supervisor integration and selective stronger-model escalation only after the core provider/validation path is proven.

## Success condition

The use case is successful when an unattended overnight run can create useful validated quality Candidates, stop unsafe/non-converging work without human babysitting, preserve complete evidence for morning review, and measurably reduce cloud-agent work without increasing defect or review burden.
