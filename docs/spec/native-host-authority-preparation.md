# Native host closing authority preparation

The actual Goal Step or Phase closing `Wait` may carry the optional generated
`authorityContext: WorkflowHostAuthorityContext`. Its required fields are
`purpose: String`, `componentInstance: String` and `scope: Record`. This is the
selected target of the already resolved native Closing input and actual owning
core. It exposes a target; it authenticates no origin and grants no permission.
Ordinary Wait, Request, Human, Terminal and forwarded repository child outputs
carry no closing authority context. The fifteen normal Operations and their
Start, Advance and Submit parameters remain unchanged.

Accepted review supplies no Step commit, repository child Start or remote effect
permission. The genuinely responsible operator must independently decide whether
to authorize the selected target and supply the actual authorization reference,
participant, trusted issuer and historical event time. Test fixture rows and
command-line flags are not independently issued operator authorization.

## Public passive preparation

`org.simplemodeling.textus.smworkflow.host.NativeHostAuthorityPreparation.goalC`
accepts the generated context, `WorkflowArtifactReference`, participant, issuer
and `Instant` explicitly. It returns `Prepared(recordIdentity, record)` through
`Consequence`. Only `goal-phase-step-closure` and
`goal-phase-repository-closure` purposes are supported.

The Step scope retains instance/source revision, Plan reference, Step,
generation, native parent/candidate and accepted review evidence. The Phase
scope retains instance/source revision, selected Plan identity/revision/phase/
repository, original base, ordered completed Step commit chain, candidate,
review and child invocation. The shared native input construction supplies the
same scope to the existing authority port and the actual missing-authority Wait.
There is no extra Workflow or Git read, candidate validator, full Plan/body
certificate or new Reader admission.

Preparation derives the existing lookup from the supplied scope's String
`instance` and `sourceRevision`. The existing selected-field v7 codec consumes
the binding `{componentInstance, scope}` and explicit metadata and returns the
existing record identity with `nativeHostEvidencePayload`. Unused annotations
are discarded. Consumed String, integer generation, snapshot and ordered-chain
types remain required by their existing consumers. No clock, configuration,
source consistency certificate, storage write or Workflow call occurs.

Normal Command/json/data formatting uses snake_case for opaque Record fields.
After parsing the scope object, preparation projects only these consumed
top-level pairs for the selected purpose, before lookup and v7 encoding:

| Purpose | Declaration spelling | Normal wire spelling |
| --- | --- | --- |
| Step | `sourceRevision` | `source_revision` |
| Step | `planRevision` | `plan_revision` |
| Step | `reviewEvidence` | `review_evidence` |
| Phase | `sourceRevision` | `source_revision` |
| Phase | `childInvocation` | `child_invocation` |

A declaration-only field is retained. A wire-only field becomes the declaration
field and its wire key is removed. Equal dual spellings are accepted once;
contradictory dual spellings fail with `Consequence.stateConflict`. Missing or
wrongly typed consumed values still fail their existing String lookup or strict
selected-field v7 checks. The projection requires an object and does not rename
nested snapshots, Plan fields or completed commit fields. No generic recursive
conversion, generated outer reader change or new schema is introduced. The v7
binding, Reader admission, trust and authority meaning remain unchanged.

## Normal typed preparation Operation

Use `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareAuthority` with one named `input` of generated type `WorkflowAuthorityPreparationInput`. Select actual host values and absolute local file: URI references; the following values illustrate the concrete schema:

```sh
"$installed_executable" --host "$host" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareAuthority \
  --input '{"request":"file:///absolute/host/full-goal-wait.json","reference":"file:///absolute/host/authority-reference.json","participant":"actual-operator","issuer":"configured-host","occurredAt":"2026-10-14T00:00:00Z","collection":"entity:smworkflow.cli.host_evidence"}' --json
```

Save the full normal Goal Advance JSON from its actual closing Wait. The generated reader requires Wait and exactly one supported authorityContext. Request and reference are read in that order; unchanged NativeHostAuthorityPreparation.goalC retains actual Step/Phase scope aliases, owner, historical time and native row identity. Ordinary Waits and other assigned kinds supply no closing authority.

All fields above are required except participant is optional in the evidence DTO and governed by its selected kind. Metadata is exact, nonempty, untrimmed and control-free; collection addresses use literal `entity:` and three labels matching `[A-Za-z][A-Za-z0-9_]*`. Invalid metadata fails before acquisition where the existing read order guarantees it. Missing or invalid typed data and unavailable resources return ordinary Consequence/command errors without successful importer output. Normal CLI Help describes the generated service.

The passive helper `NativeHostAuthorityInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists. See [normal preparation Operations](normal-host-preparation-operations.md).

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

## Finite retained-task caller

`scripts/native-goal-authority.py` calls that same normal installed Operation for one selected
retained Goal task. It uses managed Python 3.12 or newer without dependencies and
loads the fixed sibling `native-goal-command.py` for the existing path, routing,
installed executable, strict JSON and bounded subprocess utilities. Run it with all ten
explicit named pairs:

```sh
uv run --no-cache --managed-python --no-python-downloads --script \
  "$shared_root/scripts/native-goal-authority.py" \
  --executable "$installed_executable" \
  --host "$host" \
  --task "$task" \
  --output "$empty_output_directory" \
  --request "$closing_wait_file" \
  --reference "$authorization_reference_file" \
  --participant "$actual_operator_participant" \
  --issuer "$configured_trusted_issuer" \
  --occurred-at "$historical_iso_instant" \
  --collection entity:smworkflow.cli.host_evidence
```

`executable`, `host`, `task`, `output`, `request` and `reference` must be absolute
normalized paths. The task is an existing direct child of
`$host/target/sm-workflow/tasks`; output is an existing empty directory beneath
that task. The caller pins its directories through symlink-free ancestors and
requires an executable installed CLI and the same existing host configuration as
the command caller. No source checkout, generated classpath or fallback is selected. It does not create prerequisites or select a new task.

The task's retained `workflow-session.json` must contain exactly `family` set to
`GoalPhase` and the actual `sessionId` returned by its real Start. The selected
request must be kind `Wait` and carry that same actual ID via `sessionId` or
`session_id`; equal dual spellings agree and contradictory spellings fail before
invocation. The caller rereads the selected task's routing for each invocation.
It forwards both existing file paths and the explicit participant, issuer, time
and collection explicitly to the normal prepareAuthority Operation, using opaque Path.as_uri() file Strings. It does not project the
opaque context or validate reference, authority scope, issuer or time semantics;
the generated Scala readers and existing native authority consumers own those.

Unknown, duplicate, missing, blank, control-containing and action-inapplicable
flags fail. The collection uses the strict dotted grammar above. No action,
proposal, result or approval flag exists. Exactly one inherited-environment
subprocess runs with the host as its working directory. The installed runtime owns
execution deadlines; the caller introduces no separate execution timeout.
The caller exclusively creates mode-0600 `response.json` and `stderr.log` in the
selected output directory, using the existing 8 MiB response and 1 MiB stderr
bounds. It prints the actual response once only after zero process exit, strict
JSON object parsing without duplicate keys or nonfinite numbers, and an array
`datastore`. A failure returns two, prints a bounded printable diagnostic and
retains available streams without retry.

The caller writes only those two output files. It preserves routing, host
configuration, native storage and Git state. Its result remains passive standard
StartupImport input: it grants nothing, performs no trusted provisioning or
import, and calls no Start, Submit or Advance. The genuinely responsible
operator's independent authorization and later explicit provisioning remain
required.

## Trusted provisioning and use

The host saves successful stdout to its trusted configuration-selected
`textus.import.data.file` input. Normal CNCF StartupImport performs existing
mutable save/create provisioning on the next ordinary startup. The returned
input is no committed receipt; no immutable or atomic import promise is added.
The tool's issuer option and encoded target do not authenticate operator origin.

The existing configured Reader requires current trusted issuer, managed
record-read permission, matching purpose/lookup/owner/selected scope and a
nonfuture occurrence. Step and Phase remain distinct. Reference and participant
must be explicit valid metadata and are preserved; the current Step/Phase
Reader does not correlate those metadata fields to the target. No new
correlation gate is introduced. Native fixed process grants, accepted review,
CAS, retained intent non-replay and actual commit/child behavior remain at their
existing owners. A context or prepared row alone supplies no review, Human,
Split Start or Git effect permission.

Normal Submit remains acknowledgement only; the caller separately Advances.
After trusted authority provisioning, normal explicit Advance may observe the
actual reviewed Step commit or start the installed repository child under its
separate prerequisites. Only the actual child's Terminal closes the outer
Phase. Skills and command/client business procedures remain unchanged. Real
task work uses command; the existing light client Start is connectivity evidence.

## Rules and executable examples

R1: Only the actual owning core and resolved native Closing/PhaseClosing Input
supplies optional closing Wait context; other outcomes carry none.

R2: Selected Step/Phase scopes use the unchanged current v7 codec; no full input
certificate, new Reader or native validation is added. Only the consumed
purpose-specific top-level alias pairs above are projected; equal dual names
agree and contradictory names fail.

R3: A genuinely responsible host supplies actual authorization reference,
participant, issuer and historical time explicitly. Encoding, UI flags and
accepted review cannot create authority.

R4: Preparation and the tool are passive and finite. They produce standard
configured bootstrap input and perform no storage or Workflow write/progression.
The retained-task caller invokes Main once, preserves the actual selected route
and saves only its response and stderr streams.

R5: Current trusted issuer, managed read permission, purpose/lookup/owner/selected
target/nonfuture occurrence and native process grants control actual use. Step
and Phase permissions remain distinct. Reference/participant metadata is
preserved without a new Step/Phase target admission.

R6: Consumed field types and CLI values are required; unused annotations are
inert. Failures emit no successful JSON. Existing native CAS, intent non-replay
and commit/child behavior remain unchanged.

`NativeHostAuthorityPreparationSpec`, `NativeHostAuthorityInputSpec`,
`scripts/tests/test_native_goal_authority.py` and the two managed two-Step Goal
episodes in `ThreeWorkflowGoalOperationSpec` bind these
examples with visible canonical metadata and adjacent Given/When/Then. This
table specifies behavior obligations and makes no validation or acceptance claim.

| Example | Rules | Observable contract |
| --- | --- | --- |
| E1 | R1,R2,R3 | Native Step Input/core projection, strict generated round-trip and passive v7 target agree with unchanged native binding and explicit metadata |
| E2 | R1,R2,R3 | Phase projection retains ordered chain/base/candidate/review/child invocation and its distinct purpose |
| E3 | R2,R3,R6 | Missing metadata and mistyped consumed scope fail; other purposes fail; unused annotations are inert |
| E4 | R4,R5,R6 | Preparation writes nothing; existing managed Create/Reader require matching actual target and live permission; metadata correlation is unchanged |
| E5 | R1,R4,R5 | Actual missing Step authority Wait remains Active with no commit; public preparation plus explicit provisioning leaves revision unchanged; next Advance observes the reviewed-tree commit |
| E6 | R1,R4,R5 | Actual missing Phase authority Wait remains open; matching public input and separate child prerequisites permit installed child execution; only its Terminal closes the outer ordered chain |
| E7 | R1,R2,R3,R6 | Actual Command/json/data formatter, RecordDecoder and strict generated Goal reader preserve canonical Step/Phase authority binding, lookup and explicit metadata |
| E8 | R2,R5,R6 | Every consumed pair accepts canonical-only, wire-only and equal dual names; contradictory, absent or wrongly typed values fail under existing strict v7 meaning |
| T1 | R1,R2,R3,R4 | Full Step/Phase Wait intake exports the same standard row as public preparation |
| T2 | R3,R4,R6 | Invalid flags/files/types/time/address/reference/participant/purpose or nonclosing output fail without successful JSON |
| T3 | R2,R3,R4,R6 | Exact dotted address and selected current v7 scope survive; no implicit identity, clock, permission or side effect |
| T4 | R1,R2,R3,R4,R6 | Actual compiled finite launcher processes read saved normal formatter Step/Phase Wait output and export parseable rows; invalid time exits two with no success JSON; each owns only its descendants/streams and a 30-second deadline | Normal CncfMain command cases in NativeHostPreparationOperationSpec P4 preserve selected payloads and refuse invalid metadata |
| T5 | R1,R2,R3,R4,R6 | Main's full normal formatter intake produces canonical native Step/Phase binding and lookup with explicit metadata |
| A1 | R1,R2,R3,R4,R6 | Matching camel/snake/equal-dual selected Wait calls the exact typed prepareAuthority Operation once; response and explicit metadata survive; routing and configuration remain unchanged |
| A2 | R1,R4,R6 | Crossed ID, non-Wait and contradictory consumed request aliases fail before invocation |
| A3 | R3,R4,R6 | Missing, duplicate, blank, control, unknown and action-inapplicable flags fail without defaults or writes |
| A4 | R3,R4,R6 | Invalid collection or relative, unnormalized or absent explicit file fails before invocation |
| A5 | R4,R6 | Missing, occupied or symlinked output and foreign/invalid selected task paths fail before invocation or stream creation |
| A6 | R1,R4,R6 | Absent or malformed exact routing and missing prepared launcher/host prerequisites fail without replacement |
| A7 | R4,R6 | Nonzero, malformed, duplicate-key, nonfinite, oversized and timed-out responses retain available streams without success JSON or retry |
| A8 | R1,R4,R6 | Sequential independently selected tasks reread and preserve their own retained IDs |

Direct execution of the managed `test_native_goal_authority.py` discovers
`test_native_goal_*.py` under its own `scripts/tests` directory, including the
complete unchanged command caller suite and the new authority caller suite.
Those Python examples mock the native-call transport boundary only; Scala retains typed native authority
admission. Discovery and fixture rows make no actual validation or task claim.

Distinct Split Start consent preparation and RepositoryEffect permission
preparation/provisioning remain mandatory following D2 work. Genuine independently
attributable installed command tasks for all three families, typed modes,
providers, canonical IDs, Submit/Advance, Human/Wait/Terminal/error behavior and
the bounded Goal remain required. These specifications and fixture processes
complete none of those actual host/task obligations or full Step/Phase closure.

See [normal host configuration](normal-three-workflow-host.md),
[native installation](native-client-installation.md),
[authority applicability](native-host-authority-applicability.md),
[review preparation](native-host-review-preparation.md) and
[Human preparation](native-host-human-preparation.md) for their distinct owners.

The normal public route and runtime acquisition cases are mapped to `NativeHostPreparationOperationSpec` P1–P4 in [normal preparation Operations](normal-host-preparation-operations.md). Retained E/T IDs in the InputSpec continue to cover payload, alias, metadata and reader failures; former subprocess T4 (or evidence T6) is owned by P4.
