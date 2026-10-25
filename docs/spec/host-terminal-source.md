# Codex terminal information through the existing caller

The withdrawn provider remains withdrawn. The
[validation repair adoption](goal-phase-validation-repair-adoption.md) route
uses the caller's ordinary public API read and an opaque full-page Resource.
Current native selection adds no provider, process acquisition service, fabricated
issuedWorkContext or descendant-settlement observation.

Status: caller-return implementation, isolated installed round trip and normal runtime adoption verified, 2026-10-23; actual retained-Session progression remains separately owned.

The current executable ingress is [caller-information](caller-information.md).
The historical provider proposal below remains withdrawn.

Apply [runtime/caller terminology](runtime-caller-terminology.md): caller is the
existing Codex integration; RuntimeRoot is the configuration directory. `host`
is reserved for network destinations. Old provider identifiers below are
withdrawn historical names, not names to carry into the new caller-return API.

## Current responsibility boundary

sm-workflow returns a typed information request to the existing Codex caller.
That caller obtains the requested observations using its existing execution
context and available tools, then returns the actual result to the same retained
Workflow continuation. CNCF/sm-workflow must not execute `host_source_read`,
`HostSourceRead`, `process_exec_text` or a substitute ProcessExec wrapper to fetch
Codex information. No second Codex model run or runtime-owned App Server proxy
is required for this route. A read-only API does not inherently lose context;
the decision is about ownership and avoiding a second execution/collection path.

Here “caller” means the existing Codex integration that invoked sm-workflow,
not merely the machine on which a helper happens to run. The caller may use an
already available supported read API; this specification does not select a new
API connection or promise availability of otherwise unexposed records.

## Request, result and continuation

Reuse the existing issued-work/return and execution-observation contracts where
they express this request. The implementation must bind the requested information
kind, actual Session/continuation, target execution/item reference and requested
scope. Add only missing typed fields at that boundary; do not build another
workflow engine, dispatcher, approval protocol or generic “host read” subsystem.
Concrete wire names and ingress binding must be matched to the existing public
API in the [adoption plan](../notes/codex-caller-information-adoption-plan.md).
No illustrative payload or old provider DTO is declared executable by this text.

Return the original observations or attributable source references, their actual
scope, and an explicit available/partial/unavailable outcome. The caller must not
invent an exit code, termination, user answer or successful validation. Preserve
the distinction between helper termination, CLI termination, descendant effects
and business acceptance. Correlate the returned observation to the issued request
using existing identities. Repeated delivery must not replay the original command
or add another recovery/Advance; use the existing continuation semantics.

An information return supplies evidence, not Human consent. Any recovery action
that actually requires independent Human direction keeps that existing boundary;
this change neither requires a fresh answer solely for changing the transport
nor accepts an AI paraphrase as a genuine user message. Missing safety-critical
terminal evidence leaves the corresponding recovery unconfirmed. Missing optional
usage data is reported as unavailable and does not stop ordinary development.

Return processing resumes the same Session/issued continuation when its ordinary
conditions are met. Do not Start a replacement Session, reset repair counts, or
replay accepted Steps/tests. If the current public contract cannot admit the
caller observation, identify and implement that minimal ingress change; do not
bypass provenance by uploading an equivalent JSON blob to the old provider path.

## Adoption and preservation

The caller-return route supersedes the provider proposal below. Preserve existing
implementation and tests until the responsible development chat replaces the
affected path. Prior passing results remain evidence only for their original
scope. No current Session or historical receipt is rewritten by this revision.
The [developer handoff](../journal/2026/10/2026-10-23-codex-caller-information-handoff.md)
defines the execution order and remaining acceptance.

## Historical provider proposal — superseded, not an installation instruction

Everything below records the previous proposal for traceability, including its
configuration, capability, script, wire formats and proof route. Do not install,
register or develop that route as a prerequisite of the revised plan. Its assertion
that only a `host_source_read` invocation can establish source provenance is
superseded by the caller-observation boundary above; actual returned evidence
still needs attributable origin. Keeping this text is not compatibility scope.

### Previous proposal text

`native-goal-terminal-source.py` is a fixed, read-only host provider. It reads
one exact prior commandExecution and a separate genuine userMessage through
the public Codex App Server. It performs no Workflow mutation. The existing
CNCF UnitOfWork owns its one-shot process, captures and cancellation.

The provider accepts only `--codex-executable <absolute fixed path>` from its
installed process declaration and one bounded JSON request on stdin. There is
no script, shell, argv, PID, source-text, exit-code or ended-Boolean input. It
does not read private Codex files or change CODEX_HOME. Its managed-Python
shebang and installed mode 0755 are part of the payload contract.

## Fixed registration

Append this program to the host's existing `cncf.runtime-process-execution.v3`
plan selected by `textus.subsystem.process-execution.plan`, preserving its
existing programs, grants and limits. Replace the two executable paths only
with the actual installed provider and runtime-fixed Codex executable paths:

```json
{
  "capability": "host-source-sm-workflow-codex-terminal-read",
  "identity": "host-source-sm-workflow-codex-terminal-read",
  "executable": "/Users/asami/src/development-workstation/common/codex/scripts/native-goal-terminal-source.py",
  "fixedArguments": ["--codex-executable", "/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex"],
  "argumentTemplates": [[]],
  "limits": {
    "launchTimeoutMillis": 10000, "executionTimeoutMillis": 180000,
    "terminationGraceMillis": 5000, "stdinBytes": 65536,
    "stdoutBytes": 4194304, "stderrBytes": 1048576,
    "argumentCount": 1, "argumentBytes": 1, "artifactCount": 1,
    "artifactBytes": 1, "workAreaBytes": 1048576
  },
  "environment": {"HOME": "/Users/asami", "PATH": "/opt/homebrew/bin:/usr/bin:/bin", "LANG": "en_US.UTF-8"},
  "allowedArtifacts": [], "allowedInputFiles": {}, "allowsWorkingDirectory": false
}
```

Grant `host-source-sm-workflow-codex-terminal-read` to
`org.simplemodeling.textus.SmWorkflow`. This is a substantive installed provider;
registering uv, Python, an arbitrary shell or an interpreter alias is outside
this contract. CNCF `HostSourceRead` resolves the fixed target
`sm-workflow-codex-terminal` with empty caller arguments through ordinary
admission and returns the actual one-shot process result.

The optional closed `sm-workflow.host.terminal-source` configuration contains
`logicalTarget`, `canonicalHostRoot`, `canonicalTaskRoot`,
`installedSmWorkflowExecutable`, `nativeCaller`, `uvExecutable`,
`trustedParticipant`, `trustedIssuer`, and `nativeEvidenceCollection`.
Ordinary component initialization resolves these values. Missing configuration
disables this service path. It does not fall back to a caller source path.
The configured Human participant and evidence issuer retain their existing
independent native evidence authority. Installing a program or changing a
policy is separate from a runtime Human answer; obtain a current description
and a fresh exact answer after any policy change.

## Closed source request and public protocol

The trusted sm-workflow host constructs this request from resolved configuration
and the typed operation target. Every object has exactly the shown fields:

```json
{
  "schema": "sm-workflow.codex-terminal-source-read.v1",
  "executionLocator": {"threadId": "execution-thread", "turnId": "execution-turn", "itemId": "execution-item"},
  "humanLocator": {"threadId": "human-thread", "turnId": "human-turn", "itemId": "human-item"},
  "expectedInvocation": {
    "host": "/host", "task": "/host/target/sm-workflow/tasks/task",
    "output": "/host/target/sm-workflow/tasks/task/prior-output",
    "installedExecutable": "/installed/sm-workflow",
    "nativeCaller": "/installed/native-goal-command.py",
    "uvExecutable": "/opt/homebrew/bin/uv",
    "operation": "org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase",
    "sessionId": "exact-session"
  }
}
```

The provider launches only the fixed Codex executable with
`app-server --listen stdio://` using ordinary subprocess execution in its
existing process group. It exchanges `initialize`, `initialized`,
`thread/read` and `thread/items/list`. It never starts/resumes a thread or model
turn. Each locator selects its exact thread and turn; only actual nextCursor
values drive pagination. The full bounded turn-page set is checked for a
unique matching item. Crossed turns, wrong types, duplicate matches,
nonterminal or truncated execution output and RPC errors fail without proof.

Limits are: request 64 KiB; each incremental JSONL message 16 MiB; aggregate
wire 32 MiB; four pages per locator; 100 items per page; 100 notifications per
RPC; 15 seconds per RPC; 140 seconds for the dialogue; terminal text 1 MiB;
Human text 64 KiB; returned result 4 MiB; stderr 1 MiB; owned-child shutdown
eight seconds. Stderr drains concurrently. EOF, strict JSON, response IDs,
actual child exit zero and complete finite output are required. Signal,
cancellation, timeout and failure close input and boundedly terminate/reap
only this owned proxy. Missing pipe/child completion remains an unconfirmed
nonzero typed diagnostic; no escaped descendant can supply successful proof.

The legacy command must be the exact `/bin/zsh -lc` representation of one
configured `uv run --no-cache --managed-python --no-python-downloads --script`
caller, with exactly executable/host/task/action=advance/output pairs.
Tokenization uses shlex; duplicate options, shell operators, substitutions,
extra commands, foreign executables and alternate scripts refuse. Historical
text is never executed. Actual aggregatedOutput (or the public output.text
representation) must contain one matching invocation and one matching
process-terminated JSONL record with exact operation, outer and target Session,
host, response/stderr references and the actual helper-owned child PID.

The terminal record is returned unchanged with `proofScope` set to
`helper-owned-cli-terminated` and actual API `toolExitCode` added. An outer
tool exit 2, native CLI exit 1/PID 62544, and runtime-started PID 62546 remain
different facts. This proves only that the helper waited for its CLI child;
it does not settle UNKNOWN effects, descendant effects or Test success.

## Genuine runtime Human answer

The separately selected actual userMessage must contain exactly one text
content object whose text is this strict JSON object:

```json
{
  "schema": "sm-workflow.whole-phase-candidate-human.v1",
  "choice": "AuthorizeNewObservation",
  "rationale": "The reason for this exact runtime direction",
  "sessionId": "exact-session",
  "descriptionReference": {"identity": "file:///absolute/actual-description-response.json", "revision": "actual-frontierRevision"},
  "executionLocator": {"threadId": "execution-thread", "turnId": "execution-turn", "itemId": "execution-item"}
}
```

`Defer` is the only other choice. Duplicate keys, nulls in required fields,
nonfinite numbers, extra fields, AI messages, natural-language inference,
uploaded source JSON and implementation approval are not this answer. Missing
or invalid exact answer returns `outcome: decision-required` with the actual
items and `human: null`; it provisions no evidence. The sm-workflow host checks
the actual current description identity/revision and frontier/policy. It uses
the real Human item reference `codex:<threadId>:<turnId>:<itemId>` at revision
`1`, separately from the terminal execution locator and host observation time.

A complete result uses schema `sm-workflow.codex-terminal-source-result.v1`
and exactly `schema`, `outcome`, `executionLocator`, `humanLocator`,
`executionItem`, `humanItem`, `terminal`, `human`. Only the admitted
host_source_read invocation authenticates this result. No public operation
accepts an uploaded equivalent as source proof.

## Finite caller recovery

`native-goal-command.py --action recover-whole-phase-from-codex --request`
accepts one actual typed request file containing exactly `sessionId`,
`descriptionUri`, `priorOutputName`, `executionLocator`, and `humanLocator`.
The prior output name is one safe relative directory name. Existing common
executable/host/task/output options and fresh output directory rules apply.
No answer, command text, terminal record or ended attestation is caller input.

The caller invokes
`NativeHostTerminalSourceService.prepareWholePhaseCandidateRecoveryFromCodex`
once and retains/displays its actual response. It derives the existing recover
DTO from the returned `nativeHostEvidencePayload` v7 row: the exact binding
target must equal the actual description target, and binding answer.reference
must equal the evidence reference/revision. The prepared response remains
byte-for-byte unchanged. That absolute response file is passed through
`--textus.import.data.file` only to `recoverGoalPhaseWholePhaseCandidate`.

Only an authorizing answer with actual ended proof, actual accepted recovery,
and unchanged task routing permits one separate ordinary Advance with no
import argument. Defer, rejection, malformed output, stale native admission,
uncertainty and changed routing stop. There is no Start, Submit, old-effect
replay, auto-retry, polling workflow or additional state store. Preparation,
recovery and Advance retain separate raw response/stderr files.

Protocol/package/caller specifications and independent boundary review are
required. Mocked coverage is not real source qualification: connected acceptance
also needs an isolated installed CLI, a real public prior terminal source and
a fresh genuine test-owned Human answer. Until that answer exists, report
connected acceptance pending without touching a real Session.
