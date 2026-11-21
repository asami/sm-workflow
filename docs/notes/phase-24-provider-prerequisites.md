# Phase 24: provider connection and prerequisite inventory

This is a source and retained-evidence assessment for P24-PRE, not permission to
invoke a provider or a claim of Phase 24 acceptance. The adopted sequence remains
current stabilization, **remaining Phase 7**, these prerequisites, then Phase 24
consumer delivery. The Phase 7 [plan](../phase/phase-7.md) and its
[dependency delivery](../phase/phase-7-dependency-delivery-checklist.md) and
[artifact overlay](../phase/phase-7-artifact-overlay-checklist.md) ledgers retain
their existing identities and unresolved conditions. This inventory does not
restart or close Phase 7. Reconcile its actual completion before treating it as a
satisfied predecessor. An attributable observation from the owning parent says
`sm-workflow-dev開発` (chat `01a1555d-b4a7-7be3-8232-5396c2f5e293`, turn
`01a1ffaa-e6d2-7a23-84fc-75c19632d85d`) is resuming Human2903 for CNCF/Cozy
real-workspace verification; no formal completion was observed. This report is
not a Phase 7 completion receipt.

## PRE-1: connection inventory

The following boundaries describe the existing supplied-review route and the
evidence needed to use it for this Phase. The detailed dated source inventory and
later R1/R2 observations are in [Phase 9 integration](phase-9-provider-integration.md);
its [routing specification](../spec/phase-9-provider-routing.md) defines the
package and attempt-state contracts.

| Boundary | Existing capability and evidence | Remaining Phase 24 gap / owner |
| --- | --- | --- |
| Caller and package | `scripts/goal-phase-dispatch.py` prepares a supplied review from an issued independent review, candidate, diff, check receipts and caller-attested files. It declines an incomplete or oversized package. | sm-workflow-dev開発: qualify reproducible practical context assembly and its provenance, estimates and overhead for Q1; a completeness attestation is not independently proven by the package. |
| Runtime dispatch and API | `GoalPhaseService.runGoalPhaseApiReview` checks the issued review and package before using a host-composed CNCF `AiRunnerSocketSet`; `GoalPhaseApiReview.scala` maps the typed review and requests no tools. Phase 9's paired fixture recorded distinct API and agent continuations, equivalent material, and ordinary Admission of both. Its API observation was `gemma` / `gpt-oss:20b`, not OpenAI. | sm-workflow-dev開発: establish the configured installed route and a real bounded OpenAI review only after PRE-4. Source, socket availability and a different provider's fixture do not prove OpenAI availability. |
| textus-ai adapter | The Phase 9 source inventory identifies `TextusAiRunner`, `AiRuntimeBinding` and `OpenAiGenerateService`. The local textus-ai-core `OpenAiRuntimeProvider.scala` contains request/response handling and optional reported token metadata; `ComponentFactory.scala` declares limits and fallback configuration. | textus-ai owner: qualify the exact delivered artifact and configured request/result, limits, fallback and usage/cost attribution on the selected connection. Optional metadata and declared configuration are not proof of enforcement or observed cost. |
| Typed result and Admission | `GoalPhaseWorkResult` returns through `GoalPhaseService.submitGoalPhaseWorkResult`; existing Continuation and native Admission retain acceptance authority. Phase 9 records paired admissions, not general provider qualification. | sm-workflow-dev開発: demonstrate the selected OpenAI result and actual provider/model/usage evidence through ordinary Admission. Returned inference alone is not acceptance or editing authority. |
| Agent continuation and uncertainty | `goal-phase-dispatch.py` prepares a host-dispatched agent assignment; `launched` and `complete` record actual host observations. API attempt states retain started and uncertain execution without blind redispatch. Phase 9's isolated R2 fixture observed editor takeover and stale-submit rejection. | sm-workflow-dev開発: qualify Phase 24's bounded patch and applicable decline, budget-stop and uncertain-result handoff. The review API itself is not an editor; Phase 9's observed takeover does not prove every failure mode. |

## PRE-2 and PRE-3: producer readiness

CNCF's generic UoW provider dispatch and continuation interfaces exist in the
Phase 9 source inventory. The paired fixture provides connected evidence for its
specific review scenario; it neither closes all of CNCF Phase 80 nor proves the
selected deployed binary or all lifecycle cases. CNCF開発 owns any demonstrated
missing generic dispatch, independence or one-shot lifecycle capability in the
canonical CNCF checkout. First map the precise selected operation and result;
do not start producer work merely because P80-A/B/C are planned.

The textus-ai owner must establish the resolved artifact identity and supported
OpenAI configuration, limits, observable fallback, request/result mapping and
usage/cost basis for the bounded route. A matching SNAPSHOT coordinate or local
source symbol does not establish the installed binary or configuration. Record
any concrete missing producer contract with its owner and deliver it through the
normal producer validation, review and delivery route. No such producer change
is authorized or claimed by this inventory.

## Q1 offline assembly

`assemble_review_package` in `scripts/goal-phase-dispatch.py` accepts an issued
independent review, the admitted repository root, explicit included paths and
excluded paths with reasons, and the caller's completeness judgment. It reads
regular UTF-8 blobs from the issued candidate tree, checking the issued head
and tree as separate Git objects: a staged candidate tree may differ from the
head commit's tree. It sorts paths, bounds the inventory and package, and declines
on missing or unsuitable content. Git lazy fetch and replacement objects are
disabled. The result contains the existing v1 `suppliedReview` packet plus local
blob identities, SHA-256 hashes, sizes and exclusions. It does not inspect the
live worktree or prove that the selected files are sufficient. Its byte-based
token estimate is a heuristic, not a model tokenizer or cost guarantee. The
offline implementation and specifications still require runtime-owned validation;
construction overhead and practical connected review remain unmeasured.

## PRE-4: access and no-paid-overage gate

The operator must confirm the selected organization/project has eligible API
access and an enabled credit benefit, with credentials managed outside chat.
The operator and runtime configuration owner must also establish an enforceable
no-paid-overage boundary for the selected route, including concurrent and
in-flight requests where relevant. Capture the mechanism and its verified scope;
a per-call estimate, configured cost cap, account balance assumption or unknown
credit status alone does not establish that boundary. OpenAI’s [project management guidance](https://help.openai.com/en/articles/9186755-managing-projects-in-the-api-platform)
states that tracked hard limits take effect with delay and recorded spend can
slightly exceed the limit. The owning parent also reports that OpenAI’s
“Sharing feedback, evaluation and fine-tuning data, and API inputs and outputs
with OpenAI” guidance says a request exceeding the daily quota for the
applicable complimentary data-sharing token benefit is billed in full at
standard rates. The benefit type itself is unverified. These observations make
a project hard limit or assumed daily quota insufficient evidence of this
Phase’s no-overage requirement. Actual access, eligible credit and enforcement
remain **unverified** in this inventory.

Until both live access and the cost guard are confirmed, do not invoke the
provider. If the guard cannot be established, leave the API route disabled and
record the exact setup gap. Unknown request outcome or usage stays unknown;
retain the attempt and use normal recovery rather than blind replay. No provider
call, account inspection, build or test was made for this inventory.
