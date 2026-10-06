# Repository-based Development Migration and Research Direction

- Date: 2026-10-07
- Status: Design/research direction
- Scope: sm-workflow, sm-repository-sync, Provider Routing
- Related: local-first Provider Routing, Decline Protocol, ExecutionEvidence, corpus/experiment

## Observation

If local LLM execution is practical on more than one developer machine, the benefit is larger than semantic-inference cost reduction. Each physical machine also contributes independent CPU/memory/toolchain capacity for compile and test. A MacBook Air and a Mac mini can therefore execute genuinely parallel development loops rather than merely share one central inference service.

This suggests treating a machine plus its semantic providers and deterministic development environment as an execution resource.

## RepositorySync as a migration boundary

sm-repository-sync can transfer durable project state through Git/GitHub and allow another compatible machine to continue development. The analogy is historical UNIX/process migration:

~~~text
process migration              distributed AI development
----------------------------------------------------------------
process/task                    Goal / Phase / large Slice
process state                   repository checkpoint + workflow state
CPU/resource requirement        ExecutionRequirement + machine capability
scheduler                       Provider Routing / Machine Placement
migration mechanism             RepositorySync via Git remote
resume                          workflow continuation on target machine
execution history               ExecutionEvidence
~~~

The analogy is intentionally limited. sm-workflow does not migrate a live memory image. It migrates durable/reconstructible development state.

## Granularity and migration cost

Repository-based migration is expensive compared with changing an LLM/provider on the same machine. Costs may include commit/sync/fetch, checkout/worktree setup, dependency/cache warm-up, environment reconstruction, context reconstruction, and loss of locality.

Therefore routing should be hierarchical:

~~~text
fine grain:
  Judgment / Implementation / Fix / Review
  -> provider/model routing on current machine

coarse grain:
  Goal / Phase / sufficiently-large Slice
  -> Machine Placement
  -> RepositorySync migration when worthwhile
~~~

A single Fix or Review is normally too small to justify physical migration. A multi-hour Phase/Slice, expensive compile/test workload, a busy primary machine, or a large independent development branch may justify it.

The central optimization question is not whether migration is technically possible but whether:

~~~text
expected parallelism / compute / queue / provider benefit
    >
repository + environment + context migration cost
~~~

No sophisticated optimizer is required initially. Coarse policy plus measurement is preferable.

## Machine capability

Machine Placement may eventually consider:

- CPU/build/test throughput;
- unified/system memory;
- available local LLMs and their measured capabilities;
- compatible JDK/build/toolchain/runtime;
- repository/worktree availability;
- current machine load;
- expected test class (SMOKE/FOCUSED/ADMISSION/FULL/HEAVY);
- dependency/cache state where observable;
- availability window, such as an always-on mini versus a portable Air.

These remain execution-placement inputs. They must not leak into Workflow semantic transitions.

## Experimental platform

A MacBook Air M3/24GB and a Mac mini/48GB provide a useful heterogeneous local testbed. The Air can test whether bounded Judgment, ROUTINE/SIMPLE work, portions of STANDARD work, and first-stage Review are practical on a smaller machine. The mini can handle larger local models and heavier work. Cloud providers remain escalation/DEEP/CRITICAL resources.

This allows experiments on both semantic routing and physical placement.

Potential measurements:

- local COMPLETED/DECLINED/BLOCKED rate by work characteristics;
- validated success after local implementation;
- local-review versus stronger-review disagreement/false-negative rate;
- escalation count and reason;
- compile/test wall-clock by machine;
- migration/sync/setup/warm-up time;
- total end-to-end completion time;
- provider/model cost where available;
- queue/wait time and physical parallelism benefit;
- break-even development-unit size for migration.

## Research hypothesis

The broader hypothesis is that AI-assisted software development can be modeled as distributed computation in which the movable computation unit is durable development work rather than a conventional in-memory process.

A development unit can be viewed conceptually as:

~~~text
DevelopmentWork
  = repository state
  + workflow/continuation state
  + semantic WorkOrder/goal
  + execution evidence
  + execution requirements
~~~

Capability-aware routing chooses semantic providers. Coarse Machine Placement can move sufficiently large development units using repository checkpoints. Providers may self-decline uncertain work, causing graceful rescheduling/escalation. Execution evidence feeds later routing-policy improvement.

This yields a researchable combination of:

- capability-aware semantic scheduling;
- optimistic assignment plus provider self-decline;
- deterministic validation of nondeterministic workers;
- repository-based coarse-grained computation migration;
- heterogeneous local/cloud execution;
- evidence-driven, Human-in-the-Loop scheduling-policy evolution.

## Possible paper direction

A working topic is:

**Repository-Based Migration and Capability-Aware Routing for Distributed AI Software Development**

sm-workflow can serve as the experimental platform rather than merely an implementation artifact. A useful paper should measure migration/routing behavior rather than stop at an architecture proposal.

Before large-scale optimization, preserve reproducible routing and migration evidence so later corpus/experiment work can answer which work classes belong on small local machines, larger local machines, or cloud providers, and at what granularity physical migration becomes worthwhile.


## Project heaviness / machine suitability profile

A practical near-term use of the routing evidence is to decide a project's normal home machine rather than migrate every small WorkOrder. The useful question is: is this project normally light enough for an Air-class machine, or does its observed development behavior justify a mini-class machine?

Project heaviness should initially remain a vector, not a synthetic scalar score. At least three dimensions are useful:

- semantic heaviness: local DECLINED rate by work/reasoning class, stronger-provider escalation, local validated completion, local-vs-strong Review disagreement;
- build heaviness: compile/test-compile and SMOKE/FOCUSED/ADMISSION/FULL/HEAVY duration/resource observations;
- operational heaviness: concurrent services/subsystems, databases/containers/external dependencies and other execution-environment load where typed evidence exists.

The most interesting semantic indicator is local-provider decline rate. It measures effective difficulty for a particular Project x Provider/Machine combination rather than relying on source size or a generic hardware benchmark. Decline reasons should be typed enough to distinguish reasoning/capability limits from context/resource/tool-environment limits.

A complementary metric is validated local completion: the proportion of admitted development units that complete local implementation and required deterministic validation/review without stronger-provider escalation. Decline rate alone is insufficient because a provider may complete work that later fails validation or is contradicted by stronger review.

Conceptually preserve a profile such as:

~~~text
ProjectExecutionProfile
  projectIdentity
  observationWindow
  machine/provider class

  semantic
    declineRate by work/reasoning class
    declineReason distribution
    validatedLocalCompletionRate
    escalationRate
    reviewDisagreementRate

  build
    compile/test duration distributions
    suite duration distributions
    available resource observations

  operational
    typed environment/load observations where available
~~~

Human-facing UI may project this vector to recommendations such as AIR_SUFFICIENT or MINI_RECOMMENDED, but the underlying observations should remain inspectable and explainable. A recommendation is time/provider dependent: a newer local model can lower decline/escalation rates and make the same project suitable for a smaller machine.

The likely practical placement policy is therefore: choose a project home machine from its observed ProjectExecutionProfile, then use RepositorySync migration only for exceptional large development units whose expected benefit exceeds migration cost.
