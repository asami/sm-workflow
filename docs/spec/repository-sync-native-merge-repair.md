# Native pending-merge repair text contract

This contract defines the typed business layer and installed native repair
connection for the bounded Phase 6 driver. The original execution retains an
issued repair input that remains unqualified. Source implementation and
specification-owned examples do not deliver that actual repair or complete that execution.
Its already issued input cannot be amended or blindly materialized after a
source correction. Later actual work follows the existing ordinary NEW execution
rule in [native Goal command task](native-goal-command-task.md), with its own
actual ID/task and independently admitted prerequisites. The original execution,
issued input and history remain as observed; the full Phase obligations remain open.

## Rules

**R1 — Selected repair meaning and explicit text policy.** The native repair
owner obtains the genuine retained review with its repair findings. The
request carries that review, meaningful nonempty findings and actual affected
file texts. Finding identities use `[A-Za-z0-9][A-Za-z0-9_.-]{0,127}`, are unique,
and the collection is bounded to 128 findings. Each finding has nonempty distinct
ordinary relative affected paths in both the review's changed paths and the
explicit `RepositorySyncNativeConflictInput.Policy.semanticPaths`. The selected
policy's existing validation and finite file-count/text limits apply. There is
no permissive policy fallback. This primitive consumes affected business facts;
it does not recertify complete review state, history, OIDs, policy markers or
copied-body equality, and cannot authenticate the review's provenance.

**R2 — Exact result meanings.** `repaired` supplies complete ordered replacement
texts and changes at least one assigned file's actual text. Other assigned files
may remain unchanged. `decision-needed` supplies meaningful rationale and no
replacements. Every other decision fails. Admission does not translate
decision-needed into Human consent, BLOCKED, DECLINED, acceptance or execution.
An empty replacement text denotes an empty ordinary file, not file deletion.

**R3 — Complete ordered affected-file coverage.** Request files cover exactly
the distinct sorted union of all finding affected paths, without omitted,
duplicate, reordered or foreign files, and do not exceed `maximumConflicts`.
Repaired replacements cover exactly the request file paths in the same order.
Paths satisfy the existing ordinary relative-path rule; absolute paths, empty
segments, dot/dot-dot segments, `.git` segments and NUL are refused.

**R4 — Finite ordinary text and required values.** Every consumed object and
collection is present. File and replacement texts are nonnull, contain no NUL
and fit `maximumBlobBytes` measured in UTF8 bytes; empty file text is valid.
Finding and result rationale is nonnull, nonblank, contains no NUL and fits
16384 UTF8 bytes. Multibyte characters consume their UTF8 byte length. Actual File capture returns typed complete text, preserving exact Unicode,
newlines and literal path characters without a process JSON/stdout envelope.
CNCF applies the current runtime byte cap during acquisition; the consumer then
applies this narrower semantic text budget. Normal preparation and workflow
Operations return structured business Values; ordinary current-context reads
use the typed File/resource contracts. All nine original product mains and
their executable launch routes are absent from source and the current packaged
JAR; no NativeHost JVM stdout executor remains.

**R5 — Pure typed intake and admission.** The CML business values are
`RepositorySyncNativeMergeRepairFile`, `RepositorySyncNativeMergeRepairRequest`
and `RepositorySyncNativeMergeRepairResult`. Normal generated Record readers
consume named business fields, including ordinary camel/snake formatter output.
The semantic codecs use type identities `RepositorySyncNativeMergeRepairRequest`
and `RepositorySyncNativeMergeRepairResult`, with schemas
`sm-workflow.repository-native-merge-repair-request.v1` and
`sm-workflow.repository-native-merge-repair-result.v1`. Current typed decoding
retains consumed fields, ignores inert root/nested annotations and refuses
missing or mistyped consumed fields and unsupported schemas. There is no exact
key-set, whole-body canonicality, reencoding certificate, management hash or
legacy adapter. Semantic failures return `Consequence.stateConflict`.

The pure decoder/admission grants no execution, file permission, independent
reviewer evidence, validation, convergence or native task issuance. E1–E7
specification-owned normal DTO and semantic fixtures exercise only those intake
and admission behaviors. The installed connection below owns native issuance
and effects through the existing managed APIs and explicit host policies.

**R6 — Installed native ownership and changed candidate.** In the existing
`repository-sync-business-operation-v12` definition, the normal required
`MergeRepairService.repairNativePendingMerge` and provided
`RepositorySyncService.submitNativeMergeRepair` form the repair pair. Installation
requires the existing nonempty explicit conflict text policy, validation policy
and native independent review port. There is no permissive policy fallback or
new host configuration. Only the actual current paired review completion with
decision `repair` and native `ReviewRepairRequired` can issue work. The owner
validates the exact sorted finding union against changed paths and policy before
capturing actual complete texts through `RepositorySyncFileEffect.captureC` in
the current owning UoW with the selected semantic policy.

The assigned Request retains the genuine review, findings and texts. Its
continuation is `<instance>:native-repair:<actual review completion revision>`;
pending Advance consumes that saved issue. Normal typed Submit admits the
proposal against the actual assigned input and closes by managed current-revision
CAS to `RepairProposalAdmitted` or `RepairDecisionRequired`. Submit executes no
file, Git or check action and does not Advance. `decision-needed` remains a
nonterminal Wait requiring separate actual Human handling; it is no consent or
acceptance. Repair uses implementation mode; the later full review remains
independent.

Only later explicit Advance records the materialization intent, applies the
actual proposal through typed `RepositorySyncFileEffect.observeC/applyC` and
stages assigned paths through the existing typed Git policy ordinals. File and
Git share the trusted `sm-workflow-repository` workspace binding. The application
retains exact assigned paths, semantic text budgets and expected-current values;
CNCF owns current Component authority, root/grant/cap, complete acquisition and
per-file installation. No File `.git` directory/linked-worktree metadata lookup,
backlink discovery or metadata lock remains. Reads need no writable file.

Apply preflights all assigned texts before writes, refuses conflicts and refuses
replay of changed replacements already applied. Unchanged assigned files remain
in ordered result coverage and need no write. Each remaining changed file uses
one `UpdateExisting(expectedText)` and consumes actual Updated. Missing/Changed
stops without creating files; an error after attempted write is transaction-
indeterminate. Partial application or an uncertain stage intent stops without
retry, replay or rollback; per-file replacement does not promise arbitrary-editor
CAS or multi-file/Git/storage atomicity. Native intent/result keys, acknowledge
ordering, candidate selection and remote scope remain unchanged.

Candidate receipts use namespace `repair-<actual review completion revision>`.
The last actual repaired pending-candidate transition in current native history
selects its namespaced pending receipt; missing or malformed selected data fails
without original-candidate fallback. Original keys and schema v3 are unchanged.
Fresh namespace-specific check intents/results and `merge-validated` carry the
new candidate's source revision. Retained results are consumed first; a failed
or indeterminate result or intent without result stops without check replay.
Fresh complete parent diffs/index/receipts feed a new independent review under
`<instance>:native-review:<actual new validated revision>`. Original issued work,
results, checks and history remain readable. Historical completion comes from
its actual committed revision, without reappend or regenerated certificates.
Only that current candidate's genuine paired acceptance can proceed toward
separately granted current commit/push/final convergence. Review acceptance grants
none of those effects. There is no reset, recovery budget or scanner.

The original whole Phase objective and its actual task/mode/provider completion,
protected Step acceptance, full Phase review and local release remain open.
[Phase 6](../phase/phase-6.md) owns the driver and applicable Slice boundary;
6.1 owns Step evidence and scoped quality, 6.2 owns FIX/recording/dependencies,
and 6.3 owns cross-Step/Final assessment and the sole aggregate full-suite
validation. The adopted operational route uses existing commands, with one
lightweight normal FQN client Start connection qualification. Existing skill
procedures and Operation/business parameter semantics are retained.

## Executable examples

`RepositorySyncNativeMergeRepairSpec` exercises observable decoder/admission
behavior. Each group has adjacent Given/When/Then and should assertions.

| Example | Rules | Observable behavior |
| --- | --- | --- |
| E1 | R1,R2,R3,R5 | Generated normal RecordDecoder/createC preserves nested review, receipts, findings, files and replacements from camel/snake business JSON. |
| E2 | R1,R2,R3,R4,R5 | Complete in-policy changed proposals, including empty file texts, succeed without recertifying review state/history. |
| E3 | R2,R5 | Decision-needed with rationale and no replacement succeeds; proposals and unknown decisions fail. |
| E4 | R1,R3,R4 | Foreign/out-of-policy/traversal paths, incomplete findings, duplicate IDs and incomplete/reordered/foreign file coverage fail. |
| E5 | R2,R3 | Incomplete/reordered/duplicate/foreign replacements and all-no-op repair fail; one changed file with another unchanged succeeds. |
| E6 | R1,R4 | Missing consumed values, blank/NUL/oversized rationale and null/NUL/oversized texts fail; multibyte byte limits are enforced. |
| E7 | R1,R2,R3,R4,R5 | Current codecs preserve actual semantic fields with inert annotations and refuse missing/mistyped fields or unsupported schemas. |

The installed connection has separately authored executable examples:

| Example | Rules | Observable behavior |
| --- | --- | --- |
| C1 | R1,R2,R3,R5,R6 | Normal production Operations, SQLite and real owned Git issue from an actual paired repair review, Submit without effects, then apply/stage/check a new candidate and issue its full independent review; original records remain separately readable. |
| C2 | R1,R2,R3,R4,R6 | Unissued/crossed/duplicate/foreign/incomplete/no-op results, absent or disallowed capture and changed expected text fail; decision-needed stays nonterminal. |
| C3 | R1,R6 | Old or missing independent evidence cannot accept the new review; fresh acceptance still requires separate current convergence grants. |
| C4 | R6 | Actual ordinary SBT failure (exit 1), managed timeout (indeterminate/no exit) and NUL-refused receipt with retained intent prevent new review and check replay. |
| F1 | R3,R4,R5,R6 | Actual typed capture/observe/apply preserves literal paths, Unicode, assigned unchanged-file coverage, complete preflight and no replay under current coarse workspace authority. |
| F2 | R4,R6 | Typed complete capture distinguishes absent from empty and preserves exact non-ASCII text; semantic NUL/finite budgets remain enforced after CNCF bounded acquisition. |
| P1 | R5,R6 | The finite normal-command caller preserves opaque Unicode rationale/replacements and a Unicode acknowledgement only on the actual assigned child route, refuses crossed routes and leaves outer routing unchanged; its subprocess is mocked. |

`RepositorySyncNativeMergeRepairContinuationSpec` owns C1–C4,
`RepositorySyncFileEffectSpec` owns the replacement typed F1–F2 behaviors, and
`scripts/tests/test_native_repository_repair_command.py` owns P1. C1–C4 evidence
and inputs are explicitly specification-owned; P1 mocks only the subprocess and
supplies no native runtime proof. C1–C4 now use a real tracked SBT 1.9.7 /
Scala 2.12.18 ProductCheck prepared before the original initial commit. The
resolved shared text passes the original validation. Connected repaired text
passes a new check; closed failed/indeterminate/intent-only modes select real
assertion failure, a 180000 ms sleep under a 90000 ms managed timeout, or actual
NUL output rejected by the existing receipt boundary. Negative branches record
the external PID with exclusive creation; later Advance preserves that marker,
the SBT invocation count and the saved result or intent without replay.
The generic repair JVM probe is removed. Actual193 qualified this common-fixture
transition with 139 passing checks across all 18 concrete shared-fixture
consumers (9 direct, 8 via `NativeHostPreparationManagedFixture`, 1 via
`NativeHostPreparationOperationFixture`) plus 3 generated ABI/completion/retention
guards: 21 selected suites. The scoped188B normal SBT and completion-read
results retain their historical scope alongside that current qualification.
Historical linked-metadata and ASCII-stdout
JVM mechanisms are removed with the File process route; they supply no evidence
for the replacement. The two public typed File conflict scenarios retain actual
capture, Submit without effects, separate Advance apply/stage, pending merge and
physical reopen, while a deliberately missing required typed SBT capability stays
denied/Wait. They establish no full conflict/repair/Goal acceptance and do not
repair the original malformed captured issued task input. The selected installed
host's residual generic definitions are replaced by typed Git V3 configuration,
but that correction does not rewrite the saved issue or bypass typed File
UpdateExisting's exact expected-text refusal. Its empty SBT declarations leave
required validation outstanding. Parent-selected validation and independent
Step review remain required. The original retained issued input, independent
review and effect authority still govern the actual task; fixture modes do not
deliver that repair or establish AI/operator acceptance or Phase closure.
