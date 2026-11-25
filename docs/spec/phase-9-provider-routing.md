# Phase 9 supplied review routing

The caller can explicitly prepare a supplied review package with
`goal-phase-dispatch.py review_package`. It requires an actual delegated,
independent GoalPhase review Request, its candidate, diff and check receipts,
and caller-supplied files with a completeness attestation. The package projects
the continuation identity and review material rather than the complete Request.
The caller retains the unchanged Request separately. The limit is measured on
the canonical UTF-8 JSON package and defaults to 512 KiB; a lower limit may be
selected. An unconfirmed or oversized package is declined without truncation.
The caller may instead use the ordinary capable-agent path.

`prepare` accepts a READY package through `supplied_review`. It issues a fresh
agent context with instructions to review only that package; the host's actual
launch and result continue through `launched`, `complete` and normal Submit.
These instructions do not establish a host-enforced tool restriction or prove
the caller's completeness attestation. Returned review is not Admission.

The configured component exposes the CNCF `AiRunnerSocketSet` input for a
host-composed provider. The explicit `GoalPhaseService.runGoalPhaseApiReview`
operation reads the canonical package from the caller-retained local file URI, within
the configured byte bound, and checks the current review suspension and package
against the issued work before using the configured runner. `api_prepare` requires
the caller-provided absolute package path and verifies its bytes against the
canonical package; it does not create the file. It requests no tools and disables
record-generation retries. Provider failures after invocation are uncertain;
normal Submit remains responsible for semantic admission. `api_prepare`,
`api_started`, `api_complete` and `api_uncertain` provide caller-owned attempt
state: retain each state before the next effect, and do not redispatch a
started or uncertain attempt. Socket availability is not evidence that a
provider is configured or that a call succeeded.

To enable bounded API review in ordinary dispatch, set
`"suppliedReviewApi": "bounded"` in the selected caller mapping. Pass the READY
`supplied_review` and the canonical file's absolute path as
`supplied_review_path`. The caller derives the attempt identity from the issued
Session, work Resource, Continuation and expected revision. Retain the attempt
state for that issued work and supply it on continuation. The normal capable
reviewer remains available when the mapping omits the setting or there is no
eligible retained package. Existing explicit callers may still provide
`bounded_api` with the selected `attemptIdentity` and `packagePath`.

The returned API attempt sets `boundedTask` on the existing review operation.
The runtime selects an installed member advertising
`textus-ai.bounded-task.v1`, prefers OpenAI, and sends the exact work Resource,
continuation, and attempt identity. The producer applies its usage policy and
reserves its allowance before dispatch. Completion requires the producer to
echo the binding and report the actual model and provider.

The Textus AI runtime supplies the bounded binding as a typed component port.
Advertise the capability on the configured instance after installing a binding
with a qualified usage policy and bounded invoker. To opt into OpenAI, set
`textus.ai.bounded-task.openai` to the string value `"true"`, select the
registered OpenAI connection, and configure the Textus AI allowance authority.
The built-in authority reads `textus.ai.bounded-task.openai-allocation` with
verified organization, project, model group, allocation and validity. A host
can also supply its own `OpenAiTaskAllowanceAuthority`. The authority assesses
the exact task and atomically reserves its complete usage bound before dispatch. Assessment,
reservation, HTTP and Record conversion share one monotonic deadline. The
authority must return by that deadline. HTTP receives only the time remaining,
and a response received too late retains its observed facts as uncertain.
Bind the `ai-runner` socket of `org.simplemodeling.textus.SmWorkflow` to the
selected Textus AI component instance in the assembly's `spi.bindings`.
Declare `textus-ai.bounded-task.v1` in that instance's capabilities. Ordinary
review and compiler repair invoke this SPI in the SmWorkflow execution
context. In the trusted `textus.subsystem.http-execution.plan`, grant
`org.simplemodeling.textus.SmWorkflow` POST to the exact configured origin.
An app calling the provider from another Component needs its own explicit
grant. The built-in policy reserves daily tokens first and can use verified
promotional credit when it covers the whole request. Set
`textus.ai.bounded-task.openai-daily-margin-percent` to an integer from 1 to 99
to adjust the daily headroom, initially 10%. The new threshold includes earlier
reservations for that organization, model group and UTC day. Refresh the daily
allocation at the UTC boundary and use the same reservation store for the
participating callers. Traffic outside that store can still cause overage.
Credential configuration alone supplies no allowance. The configured local
provider remains available when OpenAI has no eligible allowance.

### Provider-side admission evidence

Public OpenAI documentation examined on 2026-11-25 describes the
[complimentary-token policy](https://help.openai.com/en/articles/10306912-sharing-feedback-evaluation-and-fine-tuning-data-and-api-inputs-and-outputs-with-openai):
the daily allocation is scoped by organization and model group, resets at UTC
midnight, and a request crossing the cumulative daily limit is billed in full.
The documented [spend limits](https://developers.openai.com/api/docs/guides/spend-limits)
are monthly organization or project caps and may be exceeded while enforcement
catches up. [Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)
limit request or token rates; the
[Chat Completions API](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)
reports usage after execution. These sources do not verify a free-only
pre-dispatch admission control for the exact organization, model group and UTC
day, including concurrent callers. This is a documentation assessment, not a
live quota-boundary or concurrency test or a claim about an invoice.

Consequently, the built-in daily path continues to reserve the request-sized
bound locally before ordinary review and bounded repair dispatch, including the
configured margin and outstanding reservations. Usage reporting alone cannot
replace admission. Continue retaining the request claim, usage and outcome; a
reservation-free path would require verified pre-dispatch enforcement at the
quota boundary under concurrency. The separate verified promotional-credit
policy remains unchanged.

For a daily reservation, the built-in Textus AI ledger settles a complete,
valid provider usage observation bound to the dispatched request and response
before application acceptance or rejection. It charges the observed input and
output once and returns only the unused reservation to the original UTC-day
scope. Identical settlement is idempotent; a conflicting repeat cannot reclaim
more. Missing, malformed, mismatched or uncertain usage, uncertain dispatch,
and a response arriving after the deadline retain the full reservation. Earlier
reservations, claims and settlement history remain in the store. This applies
to the built-in ledger-backed daily path; a custom authority has its own
contract. Promotional credit remains a separate admission path and token usage
does not imply a credit refund.

The delivered provider behavior was exercised with offline fixtures, including
concurrent admission, application rejection and ledger reconstruction. A
separate process reopened the fixture database after test termination; the
production application was not relaunched in that observation. Deadline closure
was simulated directly rather than through an end-to-end caller interruption.
These observations do not establish live provider or billing behavior.
The bound response records the verified allowance admission separately from
observed token usage and provider-reported cost. A successful allowance check
does not supply an invoice amount.

After a retained `DECLINED`, `UNAVAILABLE`, `FAILED`, or typed input/cost budget refusal,
use `prepare` with `retained_api_attempt` and the original issued review.
This prepares the existing independent agent assignment and records the
handoff. A reported provider failure retains observed usage and request identity
when available. A completed result goes to ordinary review Admission. An
uncertain attempt stays with its original invocation for recovery.

The [Phase 24 handoff](../phase/phase-24.md#local-handoff--2026-11-22) records
one successful OpenAI transport invocation through the transport used by this
operation, in an isolated Textus AI composition. The synthetic compiler package
returned HTTP 200 and a typed `decision-needed` review from `gpt-5.6-sol`.
OpenAI reported 590 input and 260 output tokens. Phase 25 can reuse that
connection evidence and the package contract. Ordinary review admission and
compiler-repair application still require their own same-Session evidence.
The retained response contains token usage, with the remaining included
allowance and billing category unobserved. Use the
[provider access and usage policy](../notes/phase-24-provider-prerequisites.md#pre-4-access-and-no-paid-overage-gate)
when enabling ordinary routing.

The Phase 9 paired review fixture used the existing `gemma` provider with
Ollama `gpt-oss:20b`. For a bounded Task on a native Ollama daemon, enable
`textus.ai.bounded-task.local-ollama` with the string value `"true"` in the
trusted host configuration. Set `textus.ai.gemma.endpoint` to the daemon's
literal loopback origin and `textus.ai.gemma.model` to the installed model
name. Use the same assembly binding and grant POST to that loopback origin
for the calling Component. The bounded route uses the same Task and Record contract
and preserves the model and actual token counts reported by Ollama. The
Phase 9 retained review covers the earlier record-generation route.

## Editor takeover from native validation

The ordered Goal Phase connects a completed delegated editor's execution
observation to the next authenticated terminal Step validation. Two distinct
completed assignments under the same admitted editor profile can establish
recurrence when the same candidate fails the same required Compile, TestOnly or
Test check. This uses the selected check and adds no test runs. The next
normally authorized RepairStep starts at the next role in the
captured editor dispatch chain. If the chain has no next role, existing routing
and repair limits apply. A caller can dispatch only an available editor for the
issued repair capability. The supplied review API still accepts review work only.

The issued operating profile records why the role changed and points to the
prior attempt and both validation records. Later repairs continue from that
role within the same Step. The runtime restores this selection from contiguous
native history, issues each RepairStep through its ordinary Continuation, and
admits completion against the captured role chain. The selected Plan, candidate,
validation and repair accounting remain with their current native owners.

## Selected CAR lint gate

The fixed Goal Phase validation policy can declare a check with
`kind: "car-lint"`. Select it once at the end of the primary Plan check list,
after Compile and Test or TestOnly. A Step with an assessment policy selects
`carLint: "required"` to make the same check mandatory. A failed or incomplete
earlier check stops validation before the CAR lint process is invoked.

The CNCF host process plan binds the check identity to the selected repository
workspace, purpose `lint`, and the fixed Cozy argument vector
`["car", "lint", ".", "--format", "json"]`. The runtime uses typed validation
check admission and saves intent before dispatch. A complete report with no
FAIL findings passes. A complete failing report retains each finding's category,
rule, repository-relative path, location and message. Malformed and incomplete
output cannot establish success or a repairable rule failure.

The first repairable CAR lint failure uses ordinary Step repair. Two distinct
validated repairs with the same complete failing rule and path set can select
the next role in the already admitted dispatch chain even when the source tree
or line positions changed. The existing Step allowance and history cover both
editor assignments.

## Explicit provider decline

A delegated Step editor can return `declined` before making source edits, with a
concrete summary and empty findings. Normal Submit records that returned
assignment. Advance may issue another continuation of the same Step and
generation using only the remaining roles in the captured chain. It verifies
that the Git candidate has stayed the same and retains the original checks and
repair counts. A changed candidate requires ordinary review of the pending work
instead of automatic transfer. Once the chain is exhausted, the existing
Decision route records the unresolved work.

A returned `blocked` identifies an obstacle to progress and goes to Decision.
Unknown execution remains in the transport's uncertainty handling. Old
continuations and the departed executor cannot complete the replacement.

## Compile-error input preparation

Use `GoalPhaseService.resolveGoalPhaseDiagnosticInformation` against the
current issued `RepairStep`. Select one `compile-package` for the failed check
and one `candidate-source` for each required repository-relative file in the
same request. The package selection's reason identifies the source of the
complete file-set judgment. Use the capable editor when completeness cannot be
established. The runtime checks that the request includes the recognized
compiler locations and reads whole regular files from the validated Git tree
through the admitted Tree and Blob operations.

A successful selection retains the compact UTF-8 JSON package with the exact
continuation, work contract, validation, candidate, both captured streams and
whole source files. Read the package from the corresponding item in
`EvidenceService.readEvidence`. The initial response carries its reference
and eligibility. `sm-workflow.goal-phase.review-package-max-bytes` bounds the
actual serialized review and compile package bytes, with a default and
ceiling of 524,288. Incomplete or oversized material returns `agent-required`
and leaves the current repair assignment and validation history available.
Preparation grants no provider invocation, mutation or acceptance authority.

A bounded compiler replacement uses one owned Scala document and the actual
diagnostic from the retained check. Its UTF-16 range must identify nonempty
source that exactly matches the expected text in that candidate. The shared
admission checks the issued document URI, revision and version, and admits one
non-identical replacement of at most 4,096 UTF-16 units. The source span and its
distance from the diagnostic are bounded by the same limit. Reuse this admission
for a provider proposal with the runtime's diagnostic and owned document. The
result is an edit proposal. The workflow applies it under its own file authority
and validates the resulting candidate before acceptance.

The bounded compiler task uses one complete diagnostic from a failed Compile
or TestCompile check. Its source comes from the exact staged Git blob after
the managed worktree file has been checked against that blob. The input carries
the candidate tree, validation reference, repository-relative path, diagnostic
and source. When the Scala 3 location, echoed source line and caret match the
staged source, the runtime selects that exact span and asks for replacement
text only. For other diagnostics, a compiler reply supplies the binding
values, one range and the expected and replacement text. Coordinates are
zero-based UTF-16 offsets within the selected lines. The runtime constructs the diagnostic and file
identity from its own records. The shared bounded runner transport applies the
same task binding and completion checks as for reviews.

Ordinary Advance considers this task after the existing deterministic repair.
It selects only an installed bounded runner and saves a native attempt intent
before calling it. A known pre-dispatch decline, budget refusal or bound
proposal that fails patch admission leaves the normal repair assignment
available. For a rejected proposal, the runtime preserves the producer and
usage evidence and records the patch admission refusal. A missing or unbound
response leaves the attempt uncertain, with its
recorded effects retained across Advance. Before applying a retained proposal,
Advance checks the current candidate and exact source again, then uses the
existing file update, candidate capture, validation and rollback route.

A verified single oversized Scala or Java source can be recorded as a
nonblocking `Hygiene` entry in the shared Journal. The repository, path and
candidate blob identify the observation. The original entry retains the
source size, threshold and validation reference. Generated locations and
recognized generator headers are excluded. Package-wide excess alone produces
no source-size entry.

## Phase 25 qualification and operation

See the [operational qualification](../notes/phase-25-operational-qualification.md)
for the real isolated OpenAI review and compiler-repair Admissions, a separate
Ollama observation, retained uncertainty and the release limitations. These
examples do not establish routine production behavior or settled billing.
