# P68-D2: current operational host readiness

Date: 2026-10-28 JST
Status: operational host wiring repaired and verified; P68-D2 is unavailable in the current operational release.

## Adopted instruction and preserved work

The user assigned the P68-D2 setup issue to sm-workflow開発 and authorized necessary configuration/connection work while preserving existing Session, history and repair counts. The user subsequently explicitly prohibited using the implementation under development in sm-workflow-dev. That restriction is authoritative: no development runtime selection or API copy is part of this work.

The first proposed isolated development-runtime host configuration was withdrawn before edits. Its assigned editor returned SCOPE_MISMATCH because the frozen input lacked the exact schema reference and parent attribution identity. It created no files and executed no command. There was no Start, Submit, Advance, publication, import provisioning, effect replay or real Session operation. The unused UUID produced during inspection was not adopted as an executor context.

All existing Goal/Session/workflow state, accepted results, histories and counts remain owned by their existing workflows. No claim of resumed P68-D2 is made.

## Actual operational artifacts

Normal installed executable: `/Users/asami/Library/Application Support/Coursier/bin/sm-workflow`.

Normal selection: `/Users/asami/.cncf/installations/sm-workflow/selected`, installation `35f59252-854a-4367-a255-f49e0681c075`, update `54fa2145-5947-4c95-9571-14387ae70f17`. It remains selected. Editing/using either source checkout does not select its compiled code.

The selected CAR ABI contains `prepareExecution`, `prepareGoalTaskStart`, `prepareTaskFixDelivery` and `startGoalTask`. It does not contain `DelegateTaskService`, `prepareDelegateTaskStart` or `startDelegateTask`. P68-D2's prescribed public path therefore cannot be enabled by configuration alone. A substitute GoalTask, fixture profile, development installation or private state mutation is not a supported operational continuation.

## Reproduced independent host wiring defect

The current operational host is `/Users/asami/src/dev2026/sm-workflow`. Its existing `.textus/assembly.yaml` declares normal subsystem `mini-trial`, standalone `asami`, role/capability `user`, convention authentication enabled and fallback privilege disabled.

The same host's config did not select that assembly. Actual installed component Help returned exit1/native403: `Subsystem execution requires fixed-user or authenticated-user wiring; controlled test execution requires an explicit runtime test descriptor.`

Raw artifacts:

- `/private/tmp/p68-operational-help-20261028.json` and `.stderr` (failed; no stdout).
- `/private/tmp/p68-operational-help-wired-20261028.json` and `.stderr` (successful explicit ordinary assembly selection; process exit0).

The positive control used the same installed executable/root and public `command meta.help org.simplemodeling.textus.SmWorkflow --json`, adding only `--textus.assembly.descriptor=/Users/asami/src/dev2026/sm-workflow/.textus/assembly.yaml`. This selected the existing normal user assembly, without changing privileges or providing a test descriptor. It confirmed the causal wiring defect. Native403 is an application admission outcome, not an automatic platform-review rejection.

The bounded repair selects that same existing descriptor through the host's ordinary `.textus/config.json` property. All existing values, imported selections, storage roots and policy remain unchanged. The exact pre-edit config is retained at `/private/tmp/p68-operational-host-config-before-20261028.json`. The repair manifest is `/private/tmp/p68-operational-host-only-20261028-manifest.md`.

## Execution profile limits

Actual deployed regular `sm_workflow_implementation` and `sm_workflow_review` definitions select `gpt-6.1-sol / medium`. A canonical configured mapping snapshot can register those targets; it is not evidence that either role executed or was independent.

The current user explicitly answered `6.1 sol high` to `request_user_input_async` call `call_b68a9c7431284053a9eb23bb11d56cec`. Record the parent profile as user-attested `gpt-6.1-sol / high`. That attribution does not supply a genuine executor context descriptor. App thread inspection exposed thread metadata and turn identity, not an executor context identity. No available tool was found that exposes that descriptor. The normal public configuration decoder requires a genuine supplied descriptor; it does not acquire one.

Accordingly, no execution-origin object was fabricated, no thread/agent/Session/process ID was substituted, and no random context was generated to pass admission. The inactive configured mapping snapshot must not be installed as a broken partial `workflow.execution.profiles`/`sm-workflow.execution.origin` pair.

This genuine-origin host integration remains a separate concrete prerequisite if/when the operational release actually supports P68-D2. Merely filling strings or claiming configured targets executed would not resolve it.

## Verification and next owner boundary

Final ordinary Help without the per-call override succeeded with actual process exit0. Its 4,766-byte stdout is byte-identical to the explicit-assembly positive control. Retained output is `/private/tmp/p68-operational-help-final-20261028.json` and `.stderr`. The stderr retains ordinary Java warnings and a runtime-started event; no error is concealed. A literal before/after config diff confirms exactly one added top-level descriptor property and no other change. Normal selected installation/update remains unchanged.

The inactive profile mapping and factual README are retained in `target/p68-operational-host-20261028/`. Neither active profile key was installed. The tracked delta for this task is this journal only; the host config and target artifacts are locally ignored. No SBT/product tests or publishLocal are required for this configuration-only repair, and no publication is performed. `git diff --check` passed for the tracked reporting scope. Broader tests are not evidence needed for a reversible host property selection.

P68-D2 remains unstarted until the normal operational release provides its real public operations and the caller can supply a genuine current execution descriptor. This work does not authorize bringing feature-development bytes into operation. Existing operational features retain their own contracts; they are not a fallback that proves P68-D2 acceptance.

Returned this factual result to the already authorized requester chat `sm-workflow-dev開発` (`01a1555d-b4a7-7be3-8232-5396c2f5e293`) through the existing app message tool; the tool confirmed successful delivery. This was the original limited cross-chat authorization's status/result return, not Task dispatch or a request to change issued work. The message explicitly preserves all existing work and prohibits treating setup/transport as native acceptance.

## Agent usage

| Agent | Model | Effort | Use | Outcome |
| --- | --- | --- | --- | --- |
| parent `/root` | gpt-6.1-sol (user-attested; initially unknown) | high (user-attested) | DIRECT once; diagnosis, command verification and bookkeeping | current operational artifacts only after user clarification |
| `/root/p68_host_configuration01` | gpt-6.1-sol | medium | registered cncf_fix_worker_luna START once | stopped before edits; old development-runtime proposal withdrawn |
| `/root/p68_operational_host_fix02` | gpt-6.1-sol | medium | registered cncf_fix_worker_luna START once | exact one-key current-host repair and two inactive setup artifacts; parent confirmed ordinary installed Help success |

No RESUME or SBT/runtime/commit runner was used. Installed CLI checks were parent-owned with scoped escalation. No AI execution completion or native acceptance is inferred from configuration or tool transport.
