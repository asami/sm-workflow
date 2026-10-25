# Output-limit termination and native validation admission

Date: October 24, 2026
Status: read-only diagnosis confirmed; new result/persistence and continuation decisions not yet admitted
Owner: sm-workflow producer restoration; real Session operations remain with sm-workflow-dev development

Follow-up: the concrete recommended contract, exact authored-file inventory,
byte/UTF-8/compatibility decisions, finite host profile and acceptance matrix are
in `2026-10-24-output-limit-validation-repair-contract-proposal-v1.md`. It is a
design proposal, not implementation approval or a live recovery direction.

## Preserved delivery

The completed timeout-three-choice repair remains accepted as focused evidence.
See `2026-10-24-timeout-recovery-choices-handoff.md`: four affected suites,
34 passing tests, two isolated installed-command scenarios, independent review,
normal publishLocal and activated installation
`382e8019-45b6-4572-884c-f7eb67b02e7e`. No successful effect is repeated here.
The separately reported generated-ABI failure remains unresolved and is not
relabeled as passing.

## Actual incident and current public frontier

The consumer supplied actual 091 diagnostics at:

`/Users/asami/src/dev2026/sm-workflow-dev/target/sm-workflow/runtime-phase-6.5-20261023/target/sm-workflow/tasks/phase-6.5-01a1555d-b4a7-7be3-8232-5396c2f5e293/inputs/p65-091-stderr-output-limit-runtime-repair-handoff.md`

The adjacent original `091-advance-confirmed-timeout-longer-deadline/stderr.log`
reports the native SBT terminal variant `OutputLimitExceeded(Stderr)` after
2651087 milliseconds, with observed exit 143. The normal program had a
5400000-millisecond execution deadline, 2097152 stdout bytes and 131072 stderr
bytes. This is an output-limit interruption, not a timeout or a completed failed
Test. Partial diagnostic FAILED lines do not constitute a whole-Test aggregate.
The consumer reports actual termination of its owned runtime/SBT/transport;
separate managed-descendant settlement telemetry was not supplied.

Read-only consumer command 092 returned the actual current recovery target:

- Session `e89e8ba92d074262e4c8a10ef11d1183`.
- Plan `phase-6.5-three-workflow-optimization`, revision `2`.
- Candidate HEAD `20183694b0ef3a3b33da5a8470475db159a452f0`, tree
  `aca30272ae087bc0eee04542ea584bdf237efd13`.
- Source revision `2161`, frontier and intent revision `2162`.
- Step `phase:6.5`, check `repository-full`, ordinal `8`, attempt `0`.
- Outcome `decision-required`, choices `AuthorizeNewAttempt` and `Defer`.
- No `knownFailedPhase` or `terminatedTimeoutPhase` proof.

The original 092 response remains at the same task root under
`092-describe-validation-after-stderr-output-limit/response.json`. Neither that
read-only result nor this diagnostic note supplies Human authority. Prior 083
and 090 directions have already been consumed and grant no additional trial.

## Confirmed causal boundary

1. CNCF `LocalProcessExecutionDriver._read_capture` retains at most the selected
   limit, reports the retained byte count and sets `truncated` when additional
   bytes arrive. It initiates managed termination and returns the typed
   output-limit variant when termination is confirmed. The byte count describes
   retained bytes, not an invented total size of the original stream.
2. `SbtExecutionResult.outcome` maps this variant to typed `Incomplete`, rather
   than inferring a completed Test failure from exit 143.
3. `RepositorySyncNativeMergeValidation._receipt_c`, also used by GoalPhase,
   rejects every truncated capture before native result persistence. Its exact
   rejection text matches original 091 stderr. Consequently the previously
   saved intent remains without an admitted result; public 092 truthfully
   exposes UNKNOWN.
4. The existing receipt stores identity, termination text, optional exit code,
   stdout/stderr text and optional SBT facts. It has no capture-truncation or
   captured-byte-count fields. Merely removing the truncation guard would erase
   material evidence. Output-limit truncation may also split a UTF-8 code point;
   lossy decoding must not manufacture complete strict text.
5. `docs/spec/normal-typed-sbt-validation.md` explicitly requires untruncated
   captures and says receipt fields/schema do not change. Correcting this case
   therefore needs a deliberate result/persistence contract change, not an
   unreviewed relaxed predicate. No CNCF source defect is established by the
   supplied evidence.

## Minimum decisions before repair

New-result admission: preserve a truthful bounded typed output-limit result,
including affected stream, retained capture/count/truncation and incomplete
outcome. Keep malformed successful captures rejected. Preserve CNCF ownership
of finite configured limits and result interpretation; do not add a second
application byte ceiling or unlimited capture. Specify strict-text handling at
a truncated multibyte boundary and compatibility for existing stored receipts
before freezing implementation.

Current UNKNOWN continuation is a separate decision. A future admission repair
cannot backfill the missing 091 result from diagnostic logs, reinterpret it as
the old 086 timeout, or make its current two-choice target a LimitedRepair target.
Existing recovery can authorize only a new same-candidate trial or defer. If the
desired next step is corrective work before another whole Test, an explicit
bounded public continuation for this exact UNKNOWN frontier is needed, retaining
the old intent/unknown outcome and requiring independently bound Human consent
to a concrete corrective proposal. Its safety preconditions and treatment of
unconfirmed effects must be settled before implementation; no generic unknown
effect bypass or automatic retry is proposed here.

The small stderr budget is an actual configured limit, not proof that the
framework may silently expand it. Any finite budget adjustment belongs to normal
host configuration and cannot itself repair the admission mismatch. Existing
Git default 1 MiB / explicit finite maximum 16 MiB rules are unaffected and do
not automatically define an SBT capture budget.

## Preservation, validation and stop

No source/configuration/schema edit, SBT invocation, process operation, runtime
publication, Start, Submit, Advance, recover, adoption, commit or push was made
for this new incident. All actual Session history, accepted Steps/results, base,
repair counters, old timeout/unknowns, consumed directions and unrelated dirty
work remain unchanged. No new Goal was created. The actual Session remains with
its owner.

After a concrete scope is authorized, use focused receipt codec/admission and
continuation regression tests, independent review, and an isolated normal
installed-command output-limit scenario before necessary publishLocal/runtime
qualification. Reuse unchanged timeout evidence; do not repeat the actual 091
or successful consumer work as a diagnostic probe.

Current incident blocker: new result/persistence and UNKNOWN-continuation
decisions. `cncf-diagnose-fix` requires stopping before repair when diagnosis
reaches a new architecture/public contract or persistence boundary. This note
is the Development Candidate, owned by sm-workflow producer restoration; resume
after the minimum contract is admitted. Prohibited workarounds are fabricated
receipts, DB rewrites, exit-143-to-failed conversion, truncation-as-complete,
old-direction reuse and blind retry. No additional Hygiene finding is asserted.

## Agent Usage Summary

This new diagnostic work was parent-only. Exact parent model/effort is not
available and is not inferred from earlier chat settings.

| Agent | Exact model | Effort | Runtime / role | START | RESUME | DIRECT | Outcome |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| `/root` | unknown | unknown | parent runtime / diagnosis | 0 | 0 | 1 | Actual terminal event, public target, driver semantics and admission predicate inspected; no repair execution |
