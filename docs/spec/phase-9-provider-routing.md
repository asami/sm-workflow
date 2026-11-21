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
