# GoalPhase tracked Fix issues

Since Oct. 18, 2026. PHASE-6.2 / P62-FIX-C@1.

The native GoalPhase history traversal reconstructs one execution's business
issues from its actual contiguous validation and semantic completions. The
optional `issues` Request field and `issueReport` Result field are additive in
the existing request v2 and result v1 schemas. Their absence carries no claim;
it cannot waive accounting for issued unresolved IDs. Present null or malformed
structured values reject. Public DTOs preserve the same bounded values.

## Business records

`Issue` carries identity, repository, Step, TEST/REVIEW source, optional supported
source identity, summary, actual evidence reference, status and optional latest
disposition. IDs are monotonic execution-local `issue-N`, assigned in intake
order. TEST correspondence uses exact repository, Step, check identity and kind.
Review source identity may be absent: cross-observation correspondence is then
unknown, and no guessed match is made. Closed-Step issues and transitions stay
in the execution. Current-Step work assigns only that Step; ReviewPhase covers
all Steps in the selected repository.

Statuses are OPEN, DECLINED, RESOLVED and REOPENED. All but RESOLVED remain
unresolved. Each actual status transition records its previous status and
actual evidence reference. A movement describes its single evidence boundary:
current relevant unresolved count, actual resolution transitions, actual new
issues, actual reopenings, and whether review correspondence is known. Repeating
an already-unresolved observation updates evidence without inflating transition
counts. Reconstructing the same history produces the same IDs and metrics.

All options, vectors and nested values are nonnull. Exact nonblank NUL-free UTF8
identity/scope/reference strings are at most 4096 bytes; summaries and rationales
are at most 16384 bytes. State contains at most 16384 distinct issues and 65536
transitions; reports contain at most 16384 distinct issue entries. Optional
identities follow the same text contract. No null means absence.

## Repair results

ImplementStep rejects any issue report. RepairStep requires exactly one entry
for every issued current unresolved ID, using FIXED, UNRESOLVED, DECLINED or
BLOCKED and no finding ordinal. Missing, duplicated, unknown, unsolicited or
malformed IDs reject. A repair without issued IDs cannot invent FIXED entries.

FIXED records the claim but leaves the issue unresolved. UNRESOLVED preserves
its status; DECLINED sets DECLINED. BLOCKED requires `decision-needed` and uses
the existing Human/Decision route. A completed semantic batch may honestly report
partial progress or zero FIXED claims. No report alone changes counters, scope,
the selected Plan, execution authority or acceptance.

Fresh actual selected passing receipts resolve only existing matching TEST
issues covered by those receipts. Unexecuted checks, provider errors, unknown
outcomes and unrelated identities/kinds/Steps establish no resolution. Known
selected failure intake uses the shared native typed classifier, not stdout
interpretation. A later failure reuses the same ID and reopens only a previously
RESOLVED issue. The issue evidence reference points to the actual retained
validation, with its diagnostic summary and original raw receipts still available
in the check-failure context.

## Independent review results

ReviewStep and ReviewPhase accept only RESOLVED, RECURRING and NOT_REVIEWED.
Every relevant existing REVIEW issue, including a RESOLVED one, requires explicit
coverage exactly once. TEST coverage is optional; omission cannot resolve TEST
issues. RESOLVED and NOT_REVIEWED take no ordinal. RECURRING requires a unique
one-based ordinal in the actual findings vector, binding that finding to its
supported existing issue. A prior RESOLVED issue becomes REOPENED; an unresolved
issue keeps its status and updates actual evidence. Unclaimed finding ordinals
create new REVIEW issues with unknown source correspondence.

NOT_REVIEWED preserves status. An independent result's missing finding is never
implicit resolution. Review `accept` rejects while any relevant issue remains
unresolved. The result must instead request repair or a decision. Existing
excluded acceptance proposals remain nonblocking acceptance observations or
advisories; only actual required findings enter issue intake.

The independent host target binds the actual selected issue state, and its
verdict binds the actual supplied issue report alongside the existing work,
result, worker, participant and admission evidence. Passive public preparation
strictly converts the report and normalizes only existing declaration/wire name
projections of opaque Records. A different issue context or report cannot reuse
the prepared independent evidence row.

For independent review applicability, a valid report with zero dispositions is
canonicalized to absence only after strict report decoding. Both represent no
coverage claim. Nonempty reports retain every actual entry, and the complete
selected issue state remains bound in either case. Present null, malformed or
invalid reports reject. Neither absence nor a zero-entry report resolves an
issue or waives explicit coverage of every relevant REVIEW issue; native
completion and accounting guards remain authoritative.

## Native lifecycle and executable examples

Issue state changes once at each actual validation or semantic completion in the
existing contiguous traversal. Admission validates the same Request/report
relationship before native CAS completion. Bad reports leave actual candidate
edits intact. Repeated pending displays reuse issued state. Request validation
compares selected issue context with the reconstructed predecessor. Documents,
Human decisions, renewed repair allowance, new candidates and Step commits retain
state and history; scope prevents old-Step issues becoming next-Step assignments.

`GoalPhaseFixIssuesSpec` specifies bounded model/codecs/public roundtrip, exact
repair accounting, malformed reports, all dispositions, honest partial progress,
fresh selected TEST evidence, scope isolation, deterministic reconstruction,
explicit review recurrence/coverage and the final unresolved guard.
`GoalPhaseNativeCheckRepairSpec` connects actual-shaped TEST and REVIEW intake to
the same Request/Result path. `ThreeWorkflowGoalOperationSpec` exercises admitted
independent REVIEW issues, explicit fixture claims, Human continuation, fresh
checks and required re-review coverage. `NativeGoalCheckRepairCliSpec` authors
normal public report entries from actual issued IDs and retains actual failed
and passed evidence across separate ordinary commands. Host preparation examples
exercise strict public intake and managed Reader rejection of crossed issue
context or report. Fixtures are specification inputs, not genuine provider or
Human acts.

This slice adds no convergence engine, automatic replan or authority. CNCF103,
connected multi-batch partial-progress/STALLED handling, normal-client actual
commit/next-Step proof, integrated Step independent review and final 6.3 aggregate
validation retain their separate owners. It claims neither TS01 nor full TS06,
Step acceptance or Phase completion.
