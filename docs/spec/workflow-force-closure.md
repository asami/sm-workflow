# Human-directed forced closure

`WorkflowForceClosureService.describeWorkflowForceClosure` and
`forceCloseWorkflow` are shared by GoalPhase, GoalTask and DelegateTask.
The implementation belongs to sm-workflow. Skills transport the actual public
description and explicit Human decision; they do not repair or close state locally.

The description contains the complete opaque native frontier, original
participant, family, Component instance and Session. It reads no Git state and
does not require HEAD, candidate, Plan or validation to agree. An independently
provisioned Human direction must bind that exact target, `ForceClose`, a reason,
unfinished work, `NotAccepted` or `AcceptedByHuman`, the original participant and
actual source reference. `acknowledgeUncertainEffects=true` acknowledges the
recorded uncertainty; it does not prove that an external process terminated.

Use ordinary `prepareEvidence` with kind `force-closure` to format the actual
description, answer and source execution observation. Preparation alone supplies
neither consent nor independently provisioned evidence. The normal trusted host
admits the actual Human source under the separate `workflow-force-closure` purpose.

Before terminal CAS, an immutable, create-only admitted-direction record retains
the exact judgment and admission provenance in a separate application-owned
collection, independently of the original host input. The owning host sets
`sm-workflow.force-closure.record-collection` to its canonical Entity collection
and declares the matching Component record Create grant in the normal storage
plan, using the existing workflow store. Workflow instance/continuation and
external-input collections are refused. Missing configuration or admission
refuses closure without changing the native frontier. It is inert preallocation:
it does not by itself mean that closure occurred. A changed existing record is
refused, and an exact retry uses its retained admission time. A duplicate Create
fails safely. If terminal CAS fails or is uncertain, the inert record establishes
no success and authorizes no other effects.

Closing appends one native terminal with the direction's reference as its
causation. The workflow-owned record key binds Component, family, Session,
original frontier and reference; closed collection verifies this actual terminal
association and uses the accepted fact without re-authorizing mutable host input
or the current issuer. Retiring that original input does not erase the judgment.
Every preceding native entry and all original business checkpoints and
issued interactions remain intact. Native `completed` denotes a terminal instance;
the public outcome is **ForcedClosed**, with `normalCompletion=false`. Human
acceptance is retained separately and never becomes a passing check or normal
business completion. Development observation reports `forced-closed`, not normal
completion. Ordinary Advance, delayed Submit and owner-bound publication refuse.

The operation runs no Git observation, candidate capture, Plan adoption, repair,
validation, review, stage, commit, publication or remote effect. It does not cancel
an external process already in flight. Old failures, incomplete execution, pending
questions and UNKNOWN effects keep their recorded meaning. Existing work may have
external effects, but a late result cannot complete the now-terminal native root.

Native expected-revision comparison refuses a changed frontier. The terminal
append is a single managed native update, not a read-after-write content check.
Exact redelivery collects the retained direction and terminal without another
append. A changed answer under the old reference refuses. A normal completed
Session cannot be relabeled through this operation.

Implementation permission does not close any actual Session. Its owning caller
must obtain and supply the concrete Human direction. Isolated specification-owned
directions establish only the tested boundary, not real Human consent or actual
Task/Phase closure.
