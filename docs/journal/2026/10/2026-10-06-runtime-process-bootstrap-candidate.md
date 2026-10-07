# Runtime-owned Process Execution bootstrap candidate

## Current state

At 2026-10-06T14:09:18Z the existing full PHASE-2 Goal is ACTIVE.
The previous Goal turn only confirmed the corrected generated-file sentence;
it made no implementation progress. This turn prepared an actual upstream
implementation/spec patch, but its first scoped source-edit submission was
rejected before process creation. CNCF source changes were **not applied**.
The patch is not compilation, execution, test, review or acceptance evidence.

The last accepted SBT result remains1042:1280/1280 all-three-family tests,
148 completed suites. Complete consumed ledger696 through1042, original9,
all originals/history/R5/A7/null/limit3/closed consumed3 remain unchanged.
No1043 request, runner, permission consumption, SBT or local publication started.
No new repair frame, Git mutation, provider/pool/SQL bypass or Goal rebinding.

## Causal production gap and candidate

Current `GlobalRuntimeContext.create` installs HTTP and storage but no Process
Execution driver/admission. Normal runtime initialization does not own one.
Existing workflow fixtures supply these explicitly, which cannot establish the
required normal launcher path. Component-local driver or ProcessBuilder creation
would violate the existing runtime boundary and is not an alternative.

The prepared patch is
`target/sm-workflow/work/runtime-process-bootstrap.patch` in this repository.
Its exact13 targets are8 production files,4 spec files and1 design document in
the already selected CNCF source repository:

- `processexecution/ProcessExecutionModel.scala`: select host grants by actual
  Component.Context identity; preserve existing explicit single-scope admissions.
- `processexecution/RuntimeProcessExecutionPlan.scala` and
  `RuntimeProcessExecutionPlanCodec.scala`: typed host program/grant wire contract.
- `config/SubsystemProcessExecutionConfiguration.scala` and
  `CncfConfigurationParameterCatalog.scala`: confidential Subsystem-only canonical
  parameter `textus.subsystem.process-execution.plan`, owning-runtime file-source
  admission; no CAR, request, environment or CLI grant origin.
- `subsystem/SystemNode.scala`: own and close the runtime driver on shutdown,
  including startup failure; aggregate close failures and keep terminal shutdown.
- `context/GlobalRuntimeContext.scala` and
  `cli/CncfRuntimeInstanceLifecyclePart.scala`: create/install one local driver
  before Component construction and attach it to the owning SystemNode.
- `config/CncfConfigurationParameterCatalogSpec.scala`,
  `cli/CncfRuntimeProcessExecutionInitializationSpec.scala`,
  `processexecution/RuntimeProcessExecutionPlanCodecSpec.scala`, and
  `subsystem/SystemNodeRuntimeResourceSpec.scala` under `src/test/scala`:
  corresponding executable scenarios and catalog cardinality update.
- `docs/design/process-execution-runtime.md`: host ownership and wire contract.

Production/spec paths above are relative to
`src/main/scala/org/goldenport/cncf` / `src/test/scala/org/goldenport/cncf`.
Host programs have absolute executable, fixed prefix/environment, complete
literal/lower-hex templates, all eleven finite process limits and explicit
artifact/input paths. Grants select declared capabilities and canonical
component identity, not a display label. Missing host plan still means no driver
and no grant. V1 does not silently encode resource-tree or grant-limit extensions.
No source content seal, hash, repeated whole-payload comparison or component-local
authority registry is introduced. Actual standard three-workflow factory,
independent Human/review ingress, product SBT bridge and all other original
acceptance requirements remain required after this prerequisite.

## Actual source-edit refusal

The parent selected the installed `apply_patch` executable and the inspected
repository-owned patch via stdin, requesting first-attempt scoped escalation
for only these13 target files. The explanation carried the actual native user
message `msg_01a10d8d-a0be-75a3-bc35-39cc6462ac1b`, observed at native ordinal121836,
2026-10-05T19:32:27.967Z, role=user:
`停止予約を撤回し、3つのworkflowの実装・検証を再開して` (including its observed final LF).
This is existing continuation authority, not a fabricated new security approval.

Actual native tool response:

> This action was rejected due to unacceptable risk.
> Reason: This applies an uncompiled, security-sensitive process-execution driver,
> grant, and lifecycle change across the shared CNCF runtime; the user authorized
> continuing the work but not this exact broad persistent security-boundary
> modification and blast radius.
> Do not bypass this rejection through a workaround or indirect execution.
> Continue with a safer alternative, or carry out checks to prove that the action
> is authorized or low risk before trying again. Complete unaffected work without
> asking for confirmation. Report anything that remains blocked, clarify why it
> was blocked by auto-review, inform the user of the risk and ask for approval.

The tool failed at CreateProcess; no apply process/SBT/test started. Read-only
checks afterward found none of the new production/spec files or new owning-runtime
members in the target repository. Existing dirty changes were not reverted.
No alternate edit mechanism, narrower retry, old request/runner replay, fixture
fallback or source-build output write was attempted.

## Required next action

Obtain the user's informed approval of this exact13-file shared-runtime
program/grant/lifecycle change before a new, normally reviewed source-edit
submission. Approval is not platform execution permission. Preserve this refusal.
After actual application, fresh registered `cncf_command_runner` validation must
cover the new runtime/codec/resource specs plus existing process, configuration,
storage-startup and shutdown regressions through the shared SBT wrapper. Only
actual terminal SBT/wrapper/lock-release and current typed evidence can admit
that result. A distinct unchanged-SNAPSHOT local dependency refresh is then
required before consumer validation; neither operation is started here.
Independent review remains reserved for the integrated usable three-workflow
boundary, not an isolated helper increment. The full Goal remains ACTIVE and
PHASE-2 incomplete; this is the first source-edit refusal, not a repeated impasse.
