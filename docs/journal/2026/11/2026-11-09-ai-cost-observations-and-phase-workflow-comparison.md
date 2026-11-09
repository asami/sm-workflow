# AI cost observations and Phase workflow comparison

Date: 2026-11-09 (Asia/Tokyo)
Status: investigation recorded; estimates and improvement candidates, not implementation acceptance
Scope: locally retained Codex usage, parent/child work and historical Phase execution

The user requested an estimate of Luna's contribution, opportunities beyond a
local-LLM replacement, a closer development/management breakdown, and a comparison
of `cncf-goal-phase` with `sm-goal-phase`. This entry preserves the results and
their limits together. It does not select a new model, change workflow policy,
or establish a Phase acceptance condition.

## Measurement basis and coverage

The recent-usage window is **2026-11-02 06:14:40 to 2026-11-09 06:14:40 JST**.
The Phase comparison uses the separate historical intervals below.

Costs are **Standard purchased-credit equivalents** at a common rate, using the
[official Codex token-rate table](https://learn.chatgpt.com/docs/pricing#token-rates)
consulted on 2026-11-09. They are not invoices, subscription included-allowance
consumption, or a prediction that a fixed subscription price will decrease.
Actual speed modes and historical transaction prices were not established.

| Model | Input credits / 1M tokens | Cached input credits / 1M tokens | Output credits / 1M tokens |
| --- | ---: | ---: | ---: |
| gpt-6.1-sol | 50 | 2.5 | 250 |
| gpt-6-luna | 2.5 | 0.25 | 12.5 |
| gpt-6.1-terra | 25 | 1.25 | 125 |
| gpt-6-astra | 250 | 25 | 1,250 |

Cost = `(input - cached input) * input rate + cached input * cached rate + output * output rate`.
Reasoning output is already part of output and is not added again. Cache hits
are discounted, not free. Models are identified from actual turn records, not
from agent role names: a role containing `luna` or `terra` can run Sol.

Codex state/history SQLite was read without mutation. Only this Mac's retained,
priced usage is covered. Unpriced `codex-auto-review`, other machines, missing
records and service-side-only work are excluded. No encrypted content was
decrypted. No Goal, Session, accepted result, history, repair count or successful
check was changed by the investigation.

Two measurement passes must remain distinct:

- Initial model/role inventory: positive cumulative usage differences, with
  repeated/zero notifications removed and the first lifetime cumulative value
  bounded by the last-usage record. Known-model total: **31,414.57 credits**.
- Detailed work classification: native per-response `token_usage_record`,
  matched by owning thread and deduplicated by response identity. Known-model
  total: **31,950.58 credits**, about **1.7% higher**. This is the basis for the
  development/management table and the historical Phase comparison method.

The initial inventory's role shares were not recomputed on the second total.
Do not combine their exact denominators. Raw event counts, model responses,
child-agent counts and Human messages are different units.

## Luna and local-LLM replacement

Initial seven-day inventory:

| Actual model | Input + output token share | Credit share |
| --- | ---: | ---: |
| gpt-6.1-sol | 83.82% | 91.19% |
| gpt-6-astra | 0.64% | 6.66% |
| gpt-6-luna | 14.77% | 1.48% |
| gpt-6.1-terra | 0.77% | 0.66% |

Luna represented **248 of 566 child agents (43.8%)**, 24.6% of usage events,
but only about **466 credits (1.48% of known-model cost)**. Resuming the same
child was not counted as creating another agent.

| Luna role | Credits / seven days | Share of Luna cost |
| --- | ---: | ---: |
| sm_workflow_chat_waiter | 307.9 | 66.1% |
| cncf_command_runner | 68.7 | 14.7% |
| cncf_commit_runner | 63.2 | 13.6% |
| default | 19.3 | 4.1% |
| cozy_command_runner | 6.3 | 1.4% |
| cncf_runtime_runner | 0.6 | 0.1% |

Replacing all Luna at unchanged quality would remove at most about **1.48%**
of the observed external-model cost before electricity, latency, maintenance,
or extra upper-model repair. Replacing only the chat waiter corresponds to
about **0.98% of the whole**. These are conditional gross savings, not forecasts.

The user selected local-LLM waiting as a **later, low-priority improvement**,
after other improvements. The existing development-workstation journal records
`DEV-LOCAL-CHAT-WAITER-001` for this candidate. No local model, trial or routing
change was selected here. Established Luna non-coding waiting remains in use;
this investigation does not reopen the separate coding-model decision in the
[work-classification journal](2026-11-06-work-classification-precision.md).

An event subscription or deterministic waiting loop may avoid inference for
pure waiting, but a notification alone is not equivalent to the present waiter.
Any replacement must preserve **active Goal continuation**, the same Session,
request/result correlation, cursor handling, completion/error detection,
stop/defer behavior, history and repair counts. Automatic restart of the parent
Goal through the desktop integration has not been established for an LLM-free
replacement. That requirement is why the Luna monitor currently exists.

## Development versus management

The detailed pass classified **29,079 parent responses** and 566 child agents.
Development includes architecture, code investigation, implementation, repair,
specifications, tests and semantic review, including development of sm-workflow
itself. Management includes invoking an existing workflow, control/result
handoffs, waiting, state inspection and progress/authority coordination.
Overlapping or insufficiently visible work remains mixed/unknown.

| Population | Development-primary | Management-primary | Mixed / unknown |
| --- | ---: | ---: | ---: |
| Parent AI | 11.0% | 57.7% | 31.3% |
| Child AI | 92.5% | 7.4% | 0.1% |
| Whole observed cost | 27.2% | 47.7% | 25.1% |

The parent contributes about 80% of cost; **parent cost is not synonymous with
management cost**. Child classification uses registered roles and assignment
names and does not constitute a fresh review of every child's implementation.
Parent classification uses observable operations and available short summaries,
not an attribution of internal reasoning or individual context tokens.

A qualitative audit of 36 high-cost/weighted samples changed one state-plus-code
investigation from management to mixed. This is not an independent validation
or statistical accuracy estimate. Assigning every mixed item to one side gives
a sensitivity range of development **27.2–52.3%** and management **47.7–72.8%**.
Splitting mixed in half gives about **39.8% / 60.2%**, but the half allocation is
an assumption, not a measured ratio. Management is not all waste or removable.

### Parent waiting and context

| Narrow parent operation | Responses | Share of whole observed cost |
| --- | ---: | ---: |
| wait_agent only | 5,755 | 11.7% |
| Simple write_stdin process wait only | 1,871 | 4.7% |
| functions.wait only | 536 | 1.1% |

These responses account for about **17.5%** of whole cost. This is inference
and context cost when issuing the calls, not billing for elapsed waiting time,
and is not an immediately removable 17.5% budget. Interpretation, continuation
and authority decisions around the waits may still require the parent.

In the initial inventory, 98.8% of Sol input tokens were cache hits, yet Sol
cached-input charges alone were about **60.9% of whole priced cost**. Large
shared context remains costly when sent repeatedly. As arithmetic sensitivity
examples only, 10% fewer parent responses at unchanged cost mix would correspond
to about 8% of whole cost, and 20% less Sol cached input to about 12%. Neither
is a forecast; the effects overlap and must not be added.

## Historical Phase workflow comparison

The main comparison uses **the same sm-workflow repository**, with explicit
user skill invocations and locally completed Phases. Each period includes
visible parent/child work, waiting and repair from invocation through the
completion boundary. It is not a same-task experiment.

| Metric | cncf-goal-phase | sm-goal-phase | Observed change |
| --- | ---: | ---: | ---: |
| Completed Phases / declared Steps | 3 / 9 | 2 / 8 | — |
| Total credits | 8,998.47 | 2,620.68 | Different sample sizes |
| Credits / Step | 999.83 | 327.58 | **-67.2%** |
| Parent credits / Step | 736.67 | 236.16 | -67.9% |
| Child credits / Step | 263.16 | 91.43 | -65.3% |
| Parent responses / Step | 1,072 | 383 | **-64.3%** |
| All responses / Step | 1,936 | 542 | -72.0% |
| Parent management-primary credits / Step | 271.66 | 142.90 | **-47.4%** |
| Mean parent input tokens / response | 149,298 | 159,592 | +6.9% |

Step averages use pooled cost divided by pooled declared Steps, not an
unweighted average of Phase ratios. Repair attempts do not enlarge the Step
denominator. The parent's management share rises from 36.9% to 60.5% while its
absolute management cost/Step falls. Sol accounts for 97.2% and 99.2% of the
two groups' costs; this is not a saving from greater use of cheap Luna.

| Phase | Skill | Steps | Credits | Completion commit |
| --- | --- | ---: | ---: | --- |
| [6.1](../../../phase/phase-6.1.md) | cncf-goal-phase | 3 | 2,889.18 | `63ae6d846d441cb9dab40b09958bfee76a249f8e` |
| [6.2](../../../phase/phase-6.2.md) | cncf-goal-phase | 3 | 4,232.93 | `2eb89ae1c4318f42a38dea37f8c5a5c4d666ff8b` |
| [6.3](../../../phase/phase-6.3.md) | cncf-goal-phase | 3 | 1,876.36 | `350153ba2441d802ba948b879fd5c83aaea9ca63` |
| [6.6](../../../phase/phase-6.6.md) | sm-goal-phase | 4 | 677.33 | `cd10dd8c1ba4681d5f9e107b56a84d505d02f70a` |
| [6.7](../../../phase/phase-6.7.md) | sm-goal-phase | 4 | 1,943.35 | `cb2d74b0fb4d6683929060088f76f0a5758f07b8` |

Observed JST intervals, inclusive start and exclusive end:

| Phase | Start | End |
| --- | --- | --- |
| 6.1 | 2026-10-17 10:59:11 | 2026-10-18 06:37:38 |
| 6.2 | 2026-10-18 06:50:21 | 2026-10-19 13:34:30 |
| 6.3 | 2026-10-19 13:44:07 | 2026-10-20 02:38:21 |
| 6.6 | 2026-10-25 20:10:47 | 2026-10-26 13:20:59 |
| 6.7 | 2026-10-26 14:52:12 | 2026-10-27 12:55:01 |

The old group builds foundational command, plan, admission, repair and production
connections; the newer group adds SBT diagnosis and Task/Fix delivery on an
existing foundation. Step sizes, reuse, work prepared before invocation and
review policy differ. Old 6.1/6.2 formally defer repository-full validation to
6.3; new 6.6/6.7 each run it. New 6.6 includes conditional Step admission without
independent Step review, distinct from its independent integrated Phase review.

Fewer parent round trips are observed, while average parent context does not
shrink. This is consistent with a benefit from internal execution, but it does
not isolate that benefit from different work or acceptance policies. **The 67%
observation is not a causal or universal saving for sm-goal-phase.**

### Counterexample and continuing work

In goldenport-cncf, old Phase 103 costs about **202.97 credits/Step** (4 Steps)
and native Phase 116 about **310.06 credits/Step** (7 Steps): approximately
**53% higher**, the opposite direction. The work differs substantially:
validation/test metadata ABI versus CAR/SAR shared-cache ownership and recovery.
The 103 window also contains parallel Phase integration, so its request-level
attribution is incomplete. It is a supporting example, not a matched benchmark.

Continuing sm-workflow-dev periods were deliberately not divided by completed
Steps or treated as zero-cost unfinished work:

| JST observation period | Mixed activity | Direct credits |
| --- | --- | ---: |
| 10/20 03:14 to 10/25 20:10 | Phase 6.4 start to before 6.6, including 6.5 and related work | 4,088 |
| 10/28 08:05 to 11/09 06:14 | Phase 6.9 onward, continuing development, recovery and synchronization | 12,813 |

These are time-window totals for an existing parent and its selected children,
not a single Phase's cost or pure sm-goal-phase overhead. Other chats' dependency
repair is not automatically charged to the consumer. There is therefore **no
established reduction rate for overall operation**, including all recovery and
unfinished work. Selecting only completed Phases creates survivorship bias.

## Improvement candidates and required invariants

Prioritize investigation of deterministic parent round trips: repeated state
inspection, retained-result collection, control argument transfer and simple
wait/continue cycles. Internalize these through existing typed runtime owners
where equivalent behavior is possible. Preserve semantic development/review,
real Human decisions and attribution of actual execution outcomes.

Use bounded assignment context and existing retained results to avoid repeatedly
shipping unrelated history. Evaluate cached as well as uncached input, actual
model, response count, rework and successful closure; token totals alone do not
measure cost or quality. Do not infer a model-quality conclusion from agent
names or these unmatched historical samples.

Keep the local-LLM chat waiter deferred. Any waiting/internalization optimization
must demonstrate the existing active-Goal continuation and recovery behavior,
not only delivery of a notification. Preserve Session/history/counters and
required validation/review; savings do not authorize weaker acceptance or a new
approval/controller mechanism. These are investigation candidates, not newly
scheduled implementation or changes to the existing Phase ownership.

## Local evidence and checks

Local-only analysis artifacts are outside this repository under
`/Users/asami/src/target/`. These paths are diagnostic locations on this Mac,
not portable repository identities or a requirement on another workstation.
The summary above remains useful if temporary analysis files are later removed.

- `luna-usage-20261109/report.md`, `usage.json`, `cost-summary.json`: initial
  seven-day model/role inventory and its denominator.
- `luna-usage-20261109/parent-classification/report.md`, `final-summary.json`,
  `requests-final.jsonl`, `children.json`, `audit-samples-reviewed.json`:
  response classification, sensitivity and qualitative audit.
- `phase-workflow-cost-comparison-20261109/report.md`, `cohort-summary.json`,
  `case-results.json`, `case-results-with-context.json`,
  `response-cost-records.jsonl`: Phase intervals, descendant membership,
  native record positions, usage and costs. Prefer final case-results over
  intermediate candidate inventories.

The Phase pass normalizes UTC/JST before filtering, follows native parent/child
relationships, and streams both ordinary and compressed rollouts. The endpoint
uses the completion commit and a parent turn completion within 30 minutes when
available. It includes descendants created within the interval; pre-existing
children and independently requested cross-chat work are not automatically
included. A chat time window is not perfect request-level attribution.

All ten completed/context intervals were reconciled against **64,243 retained
response records**, with matching cost totals/counts, valid cache-token bounds,
no selected-source file missing and no unpriced model in these intervals. These
are accounting consistency checks, not causal validation or exhaustive billing
coverage. Source Phase-document links were checked. No SBT execution or product
code/configuration change was needed for this journal entry.

## Follow-up decision: selected for Phase 8

After recording the investigation, the user selected the operational
improvements for [Phase 8](../../../phase/phase-8.md#parent-round-trips-and-context-transfer-2026-11-09).
The earlier candidate-only description above records the investigation stage;
the following planning adoption does not claim implementation or acceptance.

The selected work is coherent runtime-owned request units, compact evidence
references and changed-state responses, scoped subagents for substantial
implementation even when Sol High is selected, runtime-owned waiting and
mechanical continuation, reuse of applicable successful work, and deterministic
input checks before semantic dispatch. Use existing workflow, effect, evidence
and agent owners rather than a second coordinator or approval/accounting ledger.

Phase 8 now includes connected scenarios and unchecked completion criteria:
normal development, actual semantic/Human boundaries, resolvable and invalid
evidence references, a substantial Sol High worker, active Goal continuation,
same-Session ordinary repair, retained results with affected rechecks, and
invalid control input without external effects or a semantic repair attempt.
Compare available round trips, exchange/context sizes and rework at equivalent
scenario scope; the historical 67% difference is not a required savings target.

Systematic usage collection stays in Phase 13, broader model comparison and
observed provider rerouting in Phase 9. Their full delivery is not a prerequisite
for these bounded improvements. The local-LLM chat waiter remains deferred;
neither notification-only waiting nor a changed model establishes equivalent
active-Goal continuation. This decision edits planning documents only and does
not change an issued Request, active Session, model configuration or runtime.

## Follow-up example: unnecessary Human boundaries in P611-B5

The user supplied the response187 source-adoption question as a concrete example
of unnecessary Human/AI round trips. Its exact described target was Session
`3585e04191dce0bc0f70e394b6745767`, Plan2/P611-B5, source/frontier3148,
generation31, repair1/10. The proposed canonical source was published/recollected
CNCF main `c6c6f09bcf94e5ac1b116a59d189dc563cebf64c`, binding4 to5, with the
original30 plus nine approved File-budget paths, original B2 conditions and B5
approval retained. `AdoptCanonicalDependencySourceRevision` would record only
Direction, followed by ordinary Advance into the same B5 Repair. New primary
publication authority was expressly outside that proposed choice.

Read the retained records under the existing sm-workflow-dev task artifacts:

- `179-b5-later-source-publication-repair-proposal/proposal.md`;
- `180-b5-later-source-publication-repair-delivery/human-authorization-channel.json`;
- `187-b5-canonical-source-description-after-coverage-repair/source-adoption-decision.md`.

Their local task root is
`/Users/asami/src/dev2026/sm-workflow-dev/target/sm-workflow/runtime-phase-6.11-restart-20261106/target/sm-workflow/tasks/phase-6.11-restart-20261106-01a1aee5/`.
This is retained diagnostic provenance, not a required checkout layout or an
instruction to operate the live Session. The small case summary is preserved
here rather than making this journal depend on temporary files remaining.

The saved channel record attributes the earlier maintenance answer to
sm-workflow-dev chat `01a1555d-b4a7-7be3-8232-5396c2f5e293`, turn
`01a1bcd2-7302-73e3-ad0e-4d0096e9f49e`, user message
`01a1bcd2-7317-71b3-bd69-c3f5c6b3c524`. Its quoted AI-written question said:

> 実Sessionの新ソース採用や新公開試行は今回に含めず、反映後の正確な公開対象で別途判断します。

The recorded Human answer was:

> 承認：限定復旧契約の修正・検証・反映を依頼する

The exclusion was thus part of the AI's maintenance proposal that Human approved,
not an independently stated Human requirement to forbid dependency adoption.
The initial explanation in this investigation justified a further question from
that exclusion without first challenging the unnecessary division of work. The
user clarified that work necessarily adds files or expands its technical target
and that these means should not themselves become Human decisions.

Selected Phase 8 correction: authorize and continue coherent development work
within the existing objective, Component/ownership, acceptance conditions and
effect authority. Include necessary file additions, inventory changes, adoption
of approved dependency delivery and routine source/binding updates. Record the
reason through existing owners; do not demand a new answer just for a path count,
revision or API transition. Keep a helper's bounded maintenance assignment
distinct from authority already held by the original requester.

Return to Human for an actual change beyond authorized purpose or effects, an
unresolved consumer/compatibility or ownership choice, acceptance changes or a
specific residual risk. Preserve genuine explicit exclusions and unknown/live
effects. This is a prospective caller/runtime contract correction, not authority
to rewrite saved consent or bypass today's source-adoption gate. Qualify the
owning public route and retain normal verification and independent review.

Avoidable questions also add parent inference: question generation, context
processing after resumption, target recollection and preparation/transfer of
answer evidence. LLM-based monitoring may add more. Human waiting time itself
is not an inference charge, and no incremental cost for this incident has been
measured. Track prompts and associated responses/transfers without assigning a
made-up saving or assuming every Human decision is removable.

Phase 8 now has this case, the normal same-purpose continuation scenario and a
contrasting genuinely new decision, with unchecked acceptance criteria. Retain
the exact historical scope, B1-B4, accepted B5 implementation, B2/B5 obligations,
UNKNOWN/failure history and repair1/10. After a genuinely necessary answer,
ordinary adoption/Advance should proceed without another confirmation for each
mechanical operation. No live adoption, Advance, new publication, Session change
or implementation was performed while recording this example.

## Follow-up example: RepositorySync input classification at frontier44

The user requested this second concrete Human-question example for Phase 8.
Synchronization Session `9d01b91080ca146bf2aee9a46e16aa4e`, public target
frontier44, offered `AdoptInputClassificationAndContinueReview`. The prompt
reported successful Compile, FULL (2,692 tests) and FixCheck and proposed retaining
two purpose-based commits, all successes/history/repair counts and the existing
non-force GitHub main authorization. The intended next work was independent
review in the same Session, without repeating tests. These are the described
historical target and reported validation, not new checks executed by this chat.

Read the two public descriptions under the local diagnostic directory
`/Users/asami/src/dev2026/sm-workflow/target/sm-workflow/repository-sync-20261109/`:

- `classification-description01.stdout.json`: same Session/frontier44; six
  applicability issues, each refusing a selected consumed program input proposed
  as a document; only Defer offered.
- `classification-description02.stdout.json`: same Session/frontier44; the six
  paths retained as consumed inputs; zero applicability issues; adoption and
  Defer offered. The earlier refused proposal remains historical.

The six paths were:

- `docs/journal/2026/10/2026-10-20-phase-12-hygiene-follow-up.md`;
- `docs/spec/goal-phase-current-inventory-continuation.md`;
- `docs/spec/goal-phase-plan-revision.md`;
- `docs/spec/goal-phase-prerequisite-publication-recovery.md`;
- `docs/spec/goal-phase-published-dependency-source-adoption.md`;
- `docs/spec/goal-phase-source-history-recovery.md`.

The retained [v3 public contract](../../../spec/repository-sync-validation-continuation.md#retained-review-input-classification-continuation-v3)
currently demands exact independent Human evidence. That describes today's API,
not an intrinsic user decision: classifying actual build/packaging/skill inputs
and determining whether existing successful checks still apply is ordinary
semantic assessment and runtime consistency work within the authorized purpose.
The selected Phase 8 correction removes a new Human prompt solely for that
classification after the public route is changed and qualified. It does not
weaken checks or relabel all Markdown files as non-input documents.

Acceptance retains candidate identity, exact selected check applicability and
genuine receipt provenance before reusing successful validation. Corrected
classification then proceeds to fresh independent review, with purpose commits,
history and repair counts preserved. Zero applicability issues is not review
PASS. Consumed-input demotion, changed candidates/checks or missing evidence
must instead trigger the necessary investigation/checks; Human is needed only
for an actual unresolved acceptance, compatibility, ownership or authority
choice. Previously authorized non-force synchronization keeps its normal review
and effect gates and receives no new authority from classification adoption.

Record the unnecessary prompt and associated preparation/resumption work as an
additional cost candidate; this incident's incremental credits were not measured.
Only Phase 8 planning and this journal were edited. No adoption, Advance, test,
review submission, Git mutation or live synchronization was performed here.
