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
provider is configured or that a call succeeded. No live qualification has
been established by these source changes.

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

A verified single oversized Scala or Java source can be recorded as a
nonblocking `Hygiene` entry in the shared Journal. The repository, path and
candidate blob identify the observation. The original entry retains the
source size, threshold and validation reference. Generated locations and
recognized generator headers are excluded. Package-wide excess alone produces
no source-size entry.
