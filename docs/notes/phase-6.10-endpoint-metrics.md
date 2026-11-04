# Phase 6.10 endpoint metric implementation

Status: authored implementation; native qualification, independent review and actual
Phase acceptance are separate. No provider usage or model qualification is claimed.

The native fresh GoalPhase Start takes one managed current-workspace byte snapshot
through CNCF FileReadSnapshot in its existing UoW. Its optional `sourceMetrics`
payload retains root/workspace, reusable literal includes/excludes, source suffixes,
physical LF counting rule, effective finite producer limits, file/byte/line totals,
observed time and the existing Git source reference. It does not retain a frozen
filename list or introduce a hash, snapshot store, lock, shell command or usage fetch.

The source roots reuse the native candidate inventory (with a documented source
suffix selection); generated output, caches, vendor and evidence prefixes are pruned.
Blank/comment lines count, as does a final unterminated line. New files inside the
same source rule are included; deleted files disappear and renames count once.

Local completion captures the final accepted committed source before selecting or
starting best-effort RepositorySync. Strict repository closure captures before its
first child intent/Start. Each endpoint summary is retained in the existing native
closure payload and projected on result recollection without another source read.
An old strict closure without a captured endpoint is unavailable rather than a later
working-tree measurement. A Session with no original measured Start keeps start,
net and ratio unavailable; adoption-time counting is never substituted.

`GoalPhaseCompletionResult.metrics` is an optional public Record. The normal close
message displays raw start/end/signed net and separate Codex/local observed usage
and ratios. Native history reconstructs issued operating-profile captures for the
summary; actual model attribution comes only from existing observed usage resources.
Selection metadata never establishes an actually applied model or effort.

Native completion with no attributable usage shows `unavailable`, never zero.
`NativeCallerPhaseMetrics` passively accepts already-retained CNCF execution-harness
Sessions and their ordinary Resources; `NativeWorkflowCallerExecution.Prepared`
exposes this optional completion projection. It validates the exact Phase instance,
retains failed/repair attempts, original execution IDs, observed model, interval and
explicit host destination/inclusion metadata. It does not obtain usage. A final
invocation's Resources are partial unless the host independently proves complete
Phase coverage. No real Phase-wide Resources were established by this implementation.

Retransmissions deduplicate, cumulative revisions select a unique latest value,
contradictory observations are omitted, explicit inclusive parents exclude covered
children, and unknown inclusion is partial. Codex and local remain separate.
Cached input/reasoning output are inclusive breakdowns and are not added again.
Missing input/output remains missing; zero/negative or incomparable net lines yield
N/A. These exploratory values never change acceptance, repair, model selection or
completion. Managed-read denial/overflow/interruption is a retained unavailable
endpoint and does not request Human, repeat validation or repeat a close/commit.

`Phase610PhaseMetricsSpec` contains real managed snapshot/add-delete-rename cases,
exclusion/denial/byte-cap cases, physical counting and ratio fixtures, existing
caller-harness resource roundtrip, native Start codec compatibility, and actual
native completion/reopen after later unrelated source changes. Authorship of these
specifications is not an executed result or live usage evidence.
