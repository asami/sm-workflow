# Runtime/caller terminology adoption — development boundary

Date: 2026-10-23
Status: connected implementation, isolated CLI and normal format/runtime cutover verified; remaining purpose-specific wording pending

Current results are in “Normal-operation adoption” below.
Earlier sections retain the original adoption inventory.

## Authority and retained work

Read the actual Human request in architecture thread
`01a0fa78-944b-7ed2-b04e-9deafbeca751`, turn
`01a16484-8301-7fe1-9ed5-cd88feb868fe`, item
`01a16484-8315-79c1-9a1e-674191f619ba`. It requests delivery and adoption of the
terminology handoff while retaining work, Session, history, repair counts and
validation. The same turn's Human item
`01a16484-ecf1-7a61-91e5-727b7879198c` requests an English handoff.

Adopt the [handoff](2026-10-23-network-host-terminology-handoff.md),
[specification](../../../spec/runtime-caller-terminology.md) and
[N-01–04 plan](../../../notes/runtime-caller-terminology-adoption-plan.md).
Reserve host for a network hostname/address. External Codex `hostId` remains
unchanged. Apply the responsibility-specific mapping; no global replacement,
compatibility aliases, forwarding wrappers, dual readers or extra approval gate.

Continue the existing caller-information adoption. Session
`96c294d73be3d48067f3721d8a2abf33`, Goal, base, epoch, accepted Steps/results,
repair counts, old failures and evidence references remain original. This adoption
performs no Start, Submit, recovery or Advance and creates no replacement Goal.
No Phase 6.4/6.5 acceptance is reopened.

The separately requested runtime refresh has already completed: selected
installation `91db9846-acb2-4551-951a-bf16ddeeb8f7`, current local CNCF JAR,
unchanged published application CAR, and two passing isolated installed CLI
examples. Those results establish the original interface's runtime/limit behavior,
not validation of renamed interfaces. Earlier failed provider qualifications also
remain failed in their original scope. See the
[caller-information adoption status](2026-10-23-codex-caller-information-adoption-status.md).

## N-01: observed declarations and connected consumers

Paths below are relative to the verified sm-workflow repository unless otherwise
specified. They are current locations, not supported compatibility names.

| Boundary | Observed implementation / consumers | Adopted target / connected work |
| --- | --- | --- |
| Caller execution | `src/main/scala/org/simplemodeling/textus/smworkflow/host/NativeWorkflowHostExecution.scala`, `NativeHostExecutionInput.scala`; service assembly and CML prepare/record DTOs | `caller.NativeWorkflowCallerExecution`, `NativeCallerExecutionInput`, `WorkflowCallerExecution*`; update CML and normal generated consumers together. |
| Passive preparation | `admission/NativeHostPreparationServiceAssembly.scala`, CML `NativeHostPreparationService`; `host/NativeHost*Preparation` and input helpers | `NativeWorkflowPreparationService` and purpose-specific preparation helpers; split caller execution from `preparation`, without a forwarding package. |
| Runtime composition | `admission/ThreeWorkflowLauncherAssembly.scala` nested `Host`/`HostDecoder`, `ThreeWorkflowHostParameters.scala` | `RuntimeComposition`/`RuntimeCompositionDecoder`, `ThreeWorkflowRuntimeParameters`; definition key/schema change with their selected inputs. |
| Input records | `ManagedHostRecordRead`, `GoalPhaseHostRecordReadPolicy`, `RepositorySyncHostSelectionReadPolicy`, `SplitPhaseHostSelectionReadPolicy` | Specification's `InputRecord`/`InputSelection` names and `input-records.*` keys; preserve collection destinations. |
| External evidence | `admission/NativeHostEvidence.scala` defines old prefix, v7 schema and `nativeHostEvidencePayload`; reader, evidence ports and preparation scripts consume them | `NativeExternalEvidence`, `external-evidence.*`, new schema/field; selected active inputs require a bounded adoption decision before reader cutover. |
| Review/Human/authority | CML `WorkflowHostReview*`, `WorkflowHostHumanContext`, `WorkflowHostAuthorityContext`, `hostContext`; `NativeWorkflowOperationOutput` and purpose-specific context builders | `WorkflowReview*`, `WorkflowHumanDecisionContext`, `WorkflowAuthorityContext`; distinguish `reviewContext` from `humanDecisionContext`. |
| CLI runtime directory | development-workstation `scripts/sm-workflow-runtime.py`: `launch` consumes leading `--host`, resolves local configuration directory, then sets cwd and assembly descriptor | `--runtime-root`/`runtimeRoot`; this flag belongs to the installed launcher, not CNCF Git's network destination. Change launcher and all actual producers together. |
| Caller helpers | development-workstation `common/codex/scripts/native-goal-command.py` emits `--host`, preparation service names and old evidence schema; sm-workflow `scripts/` mirrors and Python fixtures consume the same connection | Rename connected command construction, result fields, service names and tests in both trees; no old-option fallback. |
| Distribution | development-workstation `scripts/sm-workflow-tools.py`, three shared `sm-*` skills, five `sm_workflow_*` agent definitions, normal launcher/updater; sm-workflow bundle sources/build | Update canonical distribution and mirrors with the changed connection. Current bundle manifest still lists withdrawn provider payload; retire that entry with its actual consumers, rather than rebrand it. |

The three shared skills currently use host for caller/control roles as well as
linking current contract filenames. Their purpose-specific text, links and
distribution need alignment at cutover. Historical quotations and external tool
fields are outside mechanical deletion scope.

One additional declaration, `SplitPhaseManagedTypedStartAssembly.Host`, holds
Human-verification evidence. Its exact purpose-specific name must be recorded
before editing; it must not acquire the caller name merely because it contains
the old word. The specification permits this narrower mapping without creating
another abstraction.

## Actually selected inputs: observed and not yet converted

Read only the current consumer's `.textus/config.json` and SQLite metadata at
`sm-workflow-dev/target/sm-workflow/host/workflow.sqlite`.

- Selected configuration contains `sm-workflow.host.definition`, three
  `sm-workflow.host.evidence.*` keys, and ten family `host-records.*` keys.
  Runtime definition JSON also requires the schema rename. Store names,
  collection destinations and directories must retain their current values.
- The database contains 22 rows in `host_evidence`, with column
  `nativeHostEvidencePayload`, and 245 continuation rows. These are inventory
  counts, not a declaration that every record is active or needs conversion.
- The actual Session's outer `cncf.workflow-json.v1` envelope has
  `current`, `definition`, `history`, `identity`, `schemaVersion`; the inspected
  decoded envelope contains no host-named keys or matching old technical names.
  That does not prove that selected business inputs, external evidence or pending
  continuation payloads need no conversion. They must be traced through the
  actual current binding before N-04.
- The selected runtime and CAR still use the old interface. Their current
  success does not authorize changing only live configuration keys first.

No persisted row, configuration, storage path, signature, historical payload or
installed launcher was converted by this inventory. In particular, the existing
`host_evidence` table name and database directory are storage destinations, not
instructions to move historical evidence. Do not rewrite archived receipts into
new-interface proof.

## Concrete unresolved boundary and next work

CI-01's minimal caller-information request/result ingress is still unimplemented.
The existing issued-work execution bridge admits selected executor observations;
its completion evidence alone does not authenticate the original interrupted
command or independent Human. A renamed bridge by itself does not close this
gap. Define the smallest actual ingress against current issued-work/continuation
contracts, then update both ends with the N-02 names and validate one CI-02 round
trip. Do not wait for every N-03 rename to begin that coherent connection.

For N-04, determine which pending/selected records the changed reader will really
consume. Keep originals and the explicit mapping in the existing evidence
location; retain IDs/business values. Apply any necessary one-time conversion
outside ordinary readers, after issued calls finish, alongside the matching
runtime/configuration cutover. No generic migration engine or new integrity
certificate is required.

`HostSourceRead`/`host_source_read` remain withdrawn. No new provider, acquisition
ProcessExec proxy or model run is adopted; `process_exec_text` remains a future
candidate. Caller-return observations are information, not Human consent or
recovery/Advance authority.

This boundary records adoption and read-only inventory, not implementation or
deployment acceptance. No new product test, review cycle, publication or successful
operation replay was requested merely for this documentation change.

## Implemented and verified connected boundary — 2026-10-23

The earlier inventory and implementation-pending text above retain their original
historical scope. The purpose mapping is now applied to CML/generated DTO
consumers, runtime composition/configuration, input-records, external evidence,
review/Human contexts, caller/preparation packages and connected launcher/skill
sources. `--runtime-root` replaces the former directory flag without an alias.
Current native-goal-command-task examples and purpose-specific diagnostics were
also updated. Network hosts, external Codex fields and existing storage destinations
are retained.

Normal generation of 267 sources, main/test compile, focused boundary tests,
normal CAR/skillBundle and the actual isolated installed caller-information
round trip succeeded. Independent source review findings were repaired and
focused closure passed. See the
[actual results and evidence](2026-10-23-codex-caller-information-adoption-status.md#implementation-and-isolated-connected-acceptance--2026-10-23).

N-01 and N-02's source/isolated connection are established. Purpose-specific
remaining wording is N-03; coordinated live format adoption is N-04. No old
persisted record was converted, old reader added, live skill installed alone or
live runtime/configuration partially switched. The real Session row, configuration
and selected live runtime remain identical to the starting baseline. The
isolated same-issued test does not claim to validate old-format live conversion.

## Normal-operation adoption — 2026-10-23

The later explicit Human request authorizes this switch; the preceding sections
retain their original earlier scope. The selected inputs were inventoried and an
actual DB copy was converted/read before live application. Both real runtime
roots now use the 14 new keys and definition schema; the 22 selected evidence IDs
have new-format projections with every original payload retained unchanged.
No ordinary old-format reader or fallback was added.

Standard updater update `f4c3f54f-df84-4ca5-a9a7-ca5bebca96fe` selected
installation `f86523ee-abe0-4a30-9164-ac27eb06a373` after normal bootstrap
admission. Installed CLI Help succeeds at both the trial and development roots.
Shared skills, dedicated agents, the finite callers and their active operation
examples agree with the deployed API and `--runtime-root`.

The real Session remains at store revision 271 / inner revision 270,
`phase.PhaseInspection`. All original business/history rows, repair counts,
accepted results, Journal and development Git state remain unchanged. No Start,
Submit, Advance, recovery, successful-effect replay, commit or push occurred.
Full scope, evidence, original-copy locations and remaining coverage limits are
recorded in [the cutover result](2026-10-23-codex-caller-information-adoption-status.md#normal-runtime-and-persisted-format-cutover--2026-10-23).
