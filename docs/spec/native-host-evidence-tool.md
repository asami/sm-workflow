# Native host evidence tool

`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareEvidence` is a normal typed passive
adapter from an actual saved normal Request/Human output, an independently
supplied review result or Human answer, and explicit metadata to one standard
CNCF StartupImport row. It calls [review preparation](native-host-review-preparation.md)
or [Human preparation](native-host-human-preparation.md). It does not interact
with a Human, perform review, install trust or call Workflow.

## Normal typed preparation Operation

Use `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareEvidence` with one named `input` of generated type `WorkflowEvidencePreparationInput`. Select actual host values and absolute local file: URI references; the following values illustrate the concrete schema:

```sh
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareEvidence \
  --input '{"family":"GoalPhase","kind":"human","request":"file:///absolute/host/full-human-output.json","result":"file:///absolute/host/answer.json","reference":"file:///absolute/host/answer-reference.json","executionEvidence":"file:///absolute/host/execution.json","issuer":"configured-host","occurredAt":"2026-10-14T00:00:00Z","collection":"entity:smworkflow.cli.host_evidence","participant":"actual-submitting-participant"}' --json
```

Read request, result, artifact reference and execution evidence in that order. Families are exactly GoalPhase, RepositorySync and SplitPhase. kind is review or human; Split review is refused. participant is required for Human and forbidden for review. Use actual assigned Request/Human outputs, preserving Goal Step/Phase review, direct/forwarded Repository completion and Split Human source/type distinctions. No reviewer or operator provenance is certified.

All fields above are required except participant is optional in the evidence DTO and governed by its selected kind. Metadata is exact, nonempty, untrimmed and control-free; collection addresses use literal `entity:` and three labels matching `[A-Za-z][A-Za-z0-9_]*`. Invalid metadata fails before acquisition where the existing read order guarantees it. Missing or invalid typed data and unavailable resources return ordinary Consequence/command errors without successful importer output. Normal CLI Help describes the generated service.

The passive helper `NativeHostEvidenceInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists. See [normal preparation Operations](normal-host-preparation-operations.md).

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

## Named Goal review action

The finite [native Goal caller](../../scripts/native-goal-command.py) exposes
`--action prepare-review-input` alongside its existing actions. Supply its
explicit `--primary`, `--host`, `--task` and empty task-owned `--output` directory,
plus all seven named pairs: `--request`, `--result`, `--reference`,
`--execution-evidence`, `--issuer`, `--occurred-at` and `--collection`.
All four source-file paths must be absolute, normalized and available; collection
uses the same canonical `entity:<major>.<minor>.<name>` grammar. There are no
identity, time or result defaults. The caller fixes `--family GoalPhase` and
`--kind review`; it exposes no participant, family, kind or child-route override.

The caller rereads the selected task's `workflow-session.json` and actual issued
Request. Its shared Submit-route check consumes only session ID, Submit operation,
Submit session ID and result type, accepts matching declaration/wire aliases and
refuses contradictions. The route must remain the same Goal session with
`submitGoalPhaseWorkResult` and `GoalPhaseWorkResult`. The actual request file is
forwarded unchanged. The existing typed Operation/helper owns admission of the
assigned review context, target, completion, result, reference, worker and time.

Before invoking this action, a fresh distinct read-only reviewer must actually
review the assigned candidate and requested diff/checks. The current owning host
retains that reviewer's genuine final `GoalPhaseWorkResult`, its
`WorkflowArtifactReference`, and `WorkflowExecutionEvidence` containing the
actual returned worker identity and explicit references. Include only observed
executor facts; configured model/profile values do not attest actual execution.
Supply the actual issuer, observed completion time and admitted host collection.
Accept, repair and decision-needed remain genuine reviewer outcomes; caller
conversion cannot choose the verdict or establish independence.

The action performs one passive invocation with a 60 second timeout, retains
`response.json` and `stderr.log` exclusively under the selected output, requires
a datastore-array response and displays whole actual stdout. It leaves task
routing and host configuration unchanged. After actual successful preparation,
the owning host separately selects that response through only its admitted
`textus.import.data.file` key. The next ordinary command startup imports it;
the separately explicit Submit forwards the same genuine result/reference/
execution through unchanged declared Operation parameters. Reader and managed
completion own acceptance. Preparation performs no import, Submit, Advance,
Human interaction or terminal/Step/Phase/Git transition.

Apply the [adopted security applicability rule](../phase/phase-6-security-review-profile.md)
within the existing acceptance boundary: an additional check requires an adopted
concrete security contract, a change affecting it, and an evidence gap. For this
connection the affected boundary is the genuine reviewer-to-issued-target/native
admission and selected host import ownership. Reuse applicable existing
codec/Reader/StartupImport evidence; caller specifications cover the changed
finite forwarding behavior. PLANNED producer documents supply no delivered
guarantee or universal security PASS. The command route, lightweight client
qualification and PHASE-6.3 aggregate validation ownership remain separate.
Task-level review/Submit evidence does not close original Step/Phase review
obligations.

## Rules

R1: Strict generated family readers consume the actual normal Advance output.
The declared kind must be Request review or Human. Goal selects exactly one own
or forwarded Repository context; Repository selects its nested context; Split
selects exactly one Scope or Reasoning Human context. Nonassigned, Wait, Terminal,
ambiguous, absent and crossed result-type/source inputs fail. Normal command JSON
uses the real response formatter, including its default snake naming inside
opaque review target and completion-handle Records. Those selected Records pass
to review preparation after the strict family reader; the original own or child
owner and completion remain selected.

R2: Typed generated readers consume the actual result, reference and execution
files. The already-qualified review preparer or Human preparer consumes the
selected context through existing v7 bindings. No outer canonical-ID substitution,
guessed completion, answer selection or duplicated consistency certificate is
introduced. Review preparation maps only its declared consumed handle/Goal
target/Repository target/check-exit name pairs described in
[review R2/R6](native-host-review-preparation.md). Declaration-only, snake-only
and matching double spellings yield the same canonical v7 payload and purpose;
contradictory aliases and missing, null or wrongly typed consumed values fail.
Human preparation resolves only the finite declared question paths in
[Human R7](native-host-human-preparation.md) using framework camel, snake and
kebab aliases. The first present key wins before value decoding, so canonical
input overrides a differing alias and malformed canonical input cannot fall
back. Native scopes still project the same strict canonical v7 payload; strings
and inert annotations retain their values. Split's assigned Scope/Reasoning
gate resolves only inputType by that same priority and still requires the
actual native type and exactly one context. Private storage schema, current
Reader policy and native admission remain unchanged. The host still owns genuine review, explicit
metadata, configured collection, provisioning and subsequent Submit/Advance.

R3: The concrete WorkflowEvidencePreparationInput fields are family, kind, request, result, reference, executionEvidence, issuer, occurredAt, collection and optional participant, required only for Human and forbidden for review. File fields are explicit absolute local file: URI Strings. Bad metadata, worker/reference, typed JSON or resource access returns an ordinary error without successful importer output.

R4: NativeHostEvidenceInput.prepareC(options, readText) is a passive finite Map/String-reader adapter returning a structured Record. The actual normal ProcedureActionCall uses current-context CNCF readText only during execute. Runtime resource policy owns acquisition/release and caps. No input grants access, establishes provenance, writes storage or calls Workflow.

R5: Trusted host input/provisioning, current Reader policy and managed read
permission remain separate. Human, review, Split Start, Step/Phase closure and
Git authority are distinct. Tool conversion changes no skill definition,
procedure, normal command/client placement or operation parameters.

R6: Tool/process specifications use only fixture questions, answers and review
responses. They prove conversion behavior, not genuine Human consent, independent
review, installed task completion or Phase acceptance. Distinct Split Start and
native Step/Phase/Git context provisioning and all three genuine installed tasks
remain mandatory later D2 work.

## Executable examples

| Example | Observable obligation |
| --- | --- |
| T1 | Own Goal ReviewStep and ReviewPhase export the same selected row as public review preparation |
| T2 | Own Repository and Goal-forwarded child review retain actual child context and result type |
| T3 | Own Goal/Repository and Goal-forwarded Human require explicit participant and actual supplied answer |
| T4 | Managed Direct consumers convert actual complete Scope/Reasoning Human outputs through the real normal formatter for all declared choices, retaining selected native bindings, Submit and convergence; Split review, absent and canonical ambiguous inputs fail |
| T5 | Invalid finite field/file/typed metadata, nonassigned output and crossed result-type cases produce no successful seed |
| T6 | Normal CncfMain prepareEvidence command in NativeHostPreparationOperationSpec P4 exports direct importer JSON and refuses invalid metadata |
| T7 | Own Goal ReviewStep/ReviewPhase, direct Repository and Goal-forwarded Repository request files use the real normal command formatter, strict family reader and passive Main, retaining the canonical prepared row, selected native payload, child completion and configured collection |
| T8 | Own Goal, direct Repository and Goal-forwarded Repository Human use the real normal formatter and retain canonical rows; invalid type/owner, ambiguous or missing context and missing participant fail |

`NativeHostEvidenceInputSpec` and the applicable managed Direct examples carry
canonical metadata, adjacent Given/When/Then and should matchers. The process
specification owns explicit fixture files, a maximum 30 second process deadline,
and cleanup only of its own child processes. This document records required
behavior, without claiming validation or workflow acceptance.

The [Python caller specification](../../scripts/tests/test_native_goal_command.py)
retains E1–E7 and adds these mechanical cases with adjacent Given/When/Then:

| Example | Observable caller obligation |
| --- | --- |
| E8 | Snake-wire ReviewStep and seven caller options map to one typed normal Operation input with fixed family/kind; whole stdout and route are preserved |
| E9 | Matching declaration/wire aliases are accepted; conflicting consumed aliases, crossed routes/types and non-Request output stop before invocation |
| E10 | Unknown, duplicate, missing and inapplicable options fail without defaults or native calls |
| E11 | Malformed canonical collection and relative, unnormalized or absent source files fail before output writes |
| E12 | Nonzero, timeout, malformed and non-datastore output retain actual logs and routing, return two and stop after one passive call |
| E13 | Absent, invalid or contradictory exact task routing fails before invocation or writes |
| E14 | Supplied files and metadata remain intact; no worker, model, time, reference, result, state or configuration is invented |

Temporary fixtures and mocked subprocess streams establish caller behavior only.
They are never genuine independent review, task completion, provenance, storage
provisioning or Step/Phase acceptance evidence. Validation and actual native
execution are separately owned.

The normal public route and runtime acquisition cases are mapped to `NativeHostPreparationOperationSpec` P1–P5 in [normal preparation Operations](normal-host-preparation-operations.md). Retained E/T IDs in the InputSpec continue to cover payload, alias, metadata and reader failures; former subprocess T4 (or evidence T6) is owned by P4. P5 explicitly provisions actual raw preparation stdout through separate StartupImport. Passive supplied questions never certify genuine Human consent.
