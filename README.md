# SmWorkflow

## Next development — 2026-10-23

[Complete GoalPhase, then attempt repository sync on a best-effort basis](docs/notes/goal-phase-post-completion-sync-plan.md).
Sync failure or deferral retains completed Phase status and unrelated uncommitted
work. This is the next bounded delivery before Phase 6.6 diagnosis work;
the initial completion/safe-deferral implementation is verified and locally
published, and the CNCF Phase 116 owner has completed same-Session adoption.
The later Human-confirmed HEAD/dirty continuation and shared-skill promotion
remain pending. See the
[current roadmap](docs/phase/README.md) for preserved ongoing work and sequencing.

## Current Phase 6 planning sequence (2026-10-13)

The applied split is [6](docs/phase/phase-6.md) -> [6.1](docs/phase/phase-6.1.md)
-> [6.2](docs/phase/phase-6.2.md) -> [6.3](docs/phase/phase-6.3.md), followed by
the newly planned [6.4 Evidence storage](docs/phase/phase-6.4.md), then Phase 7.
Phase 6 delivers the actual typed skill/client loop; 6.1
adds runtime commands/live plans/Step Admission; 6.2 adds FIX/recording/decisions;
6.3 accepts the integration and starts gradual daily use. Phase 6.4 first proves
one file's notification, exact destination allocation, archival outside Git,
actual AI journal recording and fresh-process lookup, then adds bounded branches.
Repository-full SBT validation for 6–6.3 remains final-only at 6.3; Phase 6.4
has its own validation and closure. Each member retains its focused/review/release
obligations. Wait if CNCF or Cozy changes are needed. No implementation, child
Goal, test or commit is claimed by this planning update. Older broad "Phase 6"
references below describe the complete family; the [current roadmap](docs/phase/README.md)
and [ownership map](docs/phase/phase-6.md#exhaustive-unfinished-requirement-ownership)
select exact current owners. The older dated development-status paragraph below
is a historical projection; actual Phase 5.3 completion is recorded in its
[2026-10-13 closure](docs/phase/phase-5.3-closure.md) and local release `b852427`.

## Current development boundary (2026-10-12)

Phase5.2 is formally released locally at
`8e87c18647a712be909470224ac05aca52f5d799` (95 suites/626 tests passed).
Phase5.3 is active, not accepted or released. Its common typed Operation loop
uses named business inputs, one returned sessionId per execution, explicit
Advance and truthful Submit acceptance/message. Structured responses use the
existing CNCF RecordResponse/ValueReader boundary, not native JSON-string frames.
See the [current Operation contract](docs/spec/common-workflow-operation-loop.md)
and [Phase5.3 scope](docs/phase/phase-5.3.md). Skill/CLI wiring remains Phase6.

Phase5/5.1 retain their authorized formal approved-unverified release meaning.
The older delivery/fixture sections below are historical; they neither require
retrospective validation nor reopen excluded replay/recovery guarantees.

## Historical delivery boundary (2026-10-11)

Phase 5 is formally released. Phase 5.1 accepts the normal public Operations of
GoalPhaseWorkflow, SplitPhaseWorkflow and RepositorySyncWorkflow, safe selected
file effects, and starting a NEW execution from the beginning after abandonment.
Workflow issues semantic requests; current Codex returns their results. Actual
skill/CLI integration remains Phase 6.

Duplicate answers and competing callers are caller misuse. Intermediate exclusion,
unique winners, complete runtime history, identical replay responses and successful
intermediate recovery are not guaranteed. No new comparison certificate or replay
framework replaces the removed checks; existing shared infrastructure is unchanged.
The [fixed Phase 5.1 conditions](docs/phase/phase-5.1.md#current-fixed-acceptance-boundary--2026-10-11)
supersede broader historical milestones below. Their prior executions remain
history, not current acceptance requirements.

Current development documentation: [reference manual](src/main/car/manual/index.md),
[user guide](src/main/car/manual/user-guide.md), and
[SplitPhase handoff scope contract](src/main/car/manual/split-phase-scope.md).
These sources distinguish verified host bindings from unfinished workflow
delivery. Follow the [current Phase 2 acceptance table](docs/phase/phase-2-executable-specification-checklist.md)
and [normal three-workflow host configuration](docs/spec/normal-three-workflow-host.md).

The normal ComponentFactory now composes fifteen provided Operations across
GoalPhase, SplitPhase and RepositorySync on the Subsystem's initialized shared
storage/process facilities. Phase 2 calls them directly from Executable
Specifications, with explicitly test-owned selected input/review/Human evidence
and actual bounded Git/SBT/document effects. No separate deployed skill client
or CLI caller is required for those tests. CLI/skill wiring belongs to Phase 6.

Connected examples cover ordered whole-Phase closure, repository convergence,
SplitPhase Preview/Apply and selected fresh-runtime/JVM recovery. Existing real
Operation/SBT scripted scenarios passed1117:3/3. These are selected examples,
not integrated acceptance. The four admitted findings were corrected and passed
1121 focused validation (163/163) and independent bounded review. Historical
1116 remains FAILURE (1451 passed,36 failed), and1122's historical whole-root
FAILURE (1471 passed,16 failed,one suite aborted) is retained. The user has since
admitted synchronization of its two stale GoalPhase ABI references from current
complete generated output;1123 passed all40 cases in the four affected whole
suites without an abort. Fresh1124 whole-root validation passed1492/1492 in167
complete suites, with no abort or skipped case; SBT/wrapper exited0 and the
shared lock was released. Actual1125 local CAR/manual verification succeeded;
one independent integrated recovery-full-review passed the finite Phase2 scope
with no Current Boundary Blockers and is recorded in the retained ledgerR7.
Phase 2 subsequently closed in native commit
`ed1a76b07bb00c88123c50ef907f90604d3ce1bf` on 2026-10-08 with a verified
closure receipt. Phase 5 formally released in
`cf053352161bccc59871395cc8797e492f5e13c6` under the user's approved-unverified
release decision; its additional tests/review are NOT RUN — USER-APPROVED, not
PASS. Phase 5.1's fixed normal-use/check-removal functional acceptance has passed;
the user has authorized its formal local release without additional tests/review.
The final declaration is [Phase 5.1](docs/phase/phase-5.1.md); actual native
commit/closure receipt establishes release completion and the adopted Goal end.
Phase6+ remains future work, without automatic execution. Historical
recovery examples above do not restore guarantees excluded by the current scope.

## Historical fixture milestones

The following early Phase 2 milestones describe their then-current additive
fixtures, not the current factory's availability or full validation status.

Earlier Phase 2 fixture implementation connects public SplitPhase first-root
reasoning issue, retained pending delivery and provided result/durable successor.
Selected Preview and its final-acknowledgement recovery passed six cases in 968;
affected-suite 969 passed 125/125, including public scope-to-Preview continuation.
Focused 970 passed all eight scope/Human-derived cases, including independently
verified ProvideResolution and physical reopen without renewed consent.
Fresh 971 passed the whole affected suite and new selected-document examples,
134/134 in two suites. Public Preview now includes deterministic source sections
and child documents; it still performs no file writes. Fresh whole-family 977
passed 1,166/1,166 tests in 138 suites. The selected-document effect program now
has real separate-JVM file application/readback and bounded recovery evidence.
Fresh981 passed all five additive-host public APPLY/reopen scenarios, including
interrupted acknowledgements, occupied-child refusal and runtime grant recovery.
Whole-family982 passed 1,171/1,171 tests across 138 suites. Normal runtime installation and semantic/
Human document-conflict continuation remain unfinished.
This is not normal three-profile acceptance. GoalPhase Phase closure and actual RepositorySync Git effects remain
in progress; see the [resumption record](docs/journal/2026/10/2026-10-06-three-workflow-resumption.md).

Phase 1 complete Textus CAR project for software-development workflows.
The CML defines GoalPhaseWorkflow, SplitPhaseWorkflow, and RepositorySyncWorkflow
with typed application Operations. Cozy-generated ABI is admitted by CNCF;
explicit test Providers and Service bindings exercise the common
Start/Handle/Continuation/Result/Terminal contract.
See [Phase 1](docs/phase/phase-1.md) and its
[executable-specification checklist](docs/phase/phase-1-executable-specification-checklist.md).
The [evidence map](docs/phase/phase-1-evidence-map.md) distinguishes verified
items from remaining application work.

## Prepared development entry

The existing development server entry is `scripts/run-server.sh`:

```sh
/path/to/sm-workflow/scripts/run-server.sh --textus.assembly.descriptor=/path/to/assembly.yaml
```

Prepare the official `target/cncf.d` artifacts with `cozyPrepareRuntime` through
the configured serial SBT execution route first. The script executes the same
canonical launcher and prepared classpath as one-shot CLI, without resolving
another CNCF version or running SBT. It preserves the caller's working directory
and forwards explicit host configuration arguments; it supplies no storage
grants, user identity or human consent. Declare the endpoint and managed storage
in the host configuration. Current executable evidence covers real loopback
MCP publication and CLI-equivalent negative admission, plus cold native Service
Help routes through the production HostDecoder/factory. The current native
catalog has fifteen workflow Operations and six standard tools. Current catalog
expectations passed the affected1121/1123 suites and current1124 whole-root
regression; current integrated review passed, while closing remains. Help alone is
not successful Start/Preview/Apply/completion or Phase acceptance. Selected
positive direct-Operation examples do not prove production Human/review issuance
or deployed clients. Generic installable skill wiring belongs to Phase 6.

Shared `conf/cozy/config.yaml` sets `generation.skip_unchanged: false` because
SimpleModeler and CNCF are refreshed at unchanged SNAPSHOT coordinates. Normal
Cozy generation must consume those artifacts even when CML timestamps have not
changed; generated Scala is never repaired by hand. This setting does not
redirect SBT caches, choose another runtime or grant command authority.

Keep machine-specific overrides in `.cozy/config.yaml`, which is ignored by Git.
The shared configuration is read first and the local configuration overrides it;
`show cozyConfigSources` reports the selected files in that order. Use a
sbt-cozy build that supports these shared/local configuration layers. A local
configuration is optional for ordinary development. Registration of
`publication.local_application_update` belongs only in the local file: its
producer checkout, installation root, updater and admission host refer to the
current machine. Configure that registration explicitly on each host; a fresh
clone does not inherit another machine's runtime update target. When migrating
an existing checkout, preserve its local configuration before integrating the
change that removes it from Git, then restore it as an ignored local file.

Public skills distributed with this component use only the `sm-*` namespace:

| Skill | Workflow |
| --- | --- |
| `sm-goal-phase` | `GoalPhaseWorkflow` |
| `sm-split-phase` | `SplitPhaseWorkflow` |
| `sm-repository-sync` | `RepositorySyncWorkflow` |
| `sm-test-fix` | `StandaloneFixWorkflow` |
| `sm-review-fix` | `StandaloneFixWorkflow` (original findings) |
| `sm-goal-task` | `GoalTaskWorkflow` |
| `sm-delegate-task` | `DelegateTaskWorkflow` |

These are the skill-bundle naming/binding contract, not a claim that Phase 1
installs the production skill bundle. Public `sm-*` skills are designed to return
only requested semantic results through the component's assigned Operations;
their command/CLI wiring and real integration are Phase 6 work, without invoking or
depending on legacy `cncf-*` skills. Existing `cncf-*` skills remain unchanged for
long-term compatible parallel operation; shared development execution helpers
are not the distributed public skill interface.

Delegation uses [finite public operations and ordinary host bindings](docs/spec/native-delegate-task-caller.md).
An authorized existing-chat transport, destination local commit, runtime publication,
returned result, requester artifact attachment and actual consumer activation retain
separate attributable outcomes. The adapter adds no queue or publication executor.

## Historical application-contract evidence

The remaining record describes Phase 1 and earlier additive fixtures at their
recorded revisions. Its unavailable-provider/default-factory statements and
then-current test totals are historical, not claims about today's normal
three-workflow connection. Retain those original results alongside the current
acceptance table rather than converting them to new PASS evidence.

Earlier executable fixtures include GoalPhase review/Step-close and exact
program + planning commit receipts, SplitPhase PREVIEW/APPLY with source-bound
evidence and copied-document CAS, and RepositorySync retained review,
two-parent commit, non-force push and verification-fetch convergence.
Public fixture Operations and CNCF CLI JSON share the typed common envelopes.
GoalPhase preserves scoped evidence in an issued full-review WorkOrder and
automatically closes after admitted fresh full-review completion, without a
second Step-close request. Stale / scoped acceptance is rejected before claim
consumption. Repair-candidate preparation retains the admitted repair judgment,
reloads actual program / planning bytes and re-evaluates retained evidence
against the new candidate. Even unchanged content does not turn a repair
disposition into acceptance. The connected bounded repair loop admits typed
Repair completion, publishes a fresh full-Step review for the actual current
candidate and returns accepted completion to deterministic closure on the same
instance. The explicit fixture Service binds public Start and all three
Step-close / completion Operations to the same host ports. Service / CLI
fixtures now start with an empty instance store and prove Start → review →
typed Result → exact commit → Completed on that Start-created instance.
Changed and unchanged repair candidates follow the same retained Start history,
with one close request and one commit. Proactive full review closes directly
without a semantic WorkOrder. Missing human selection / host association,
foreign Handles, state selectors and persistence failures never manufacture
another instance or a false terminal. The default one-repair budget stops at
nonterminal WAIT when exhausted; failed publication prerequisites claim no
WorkOrder or Closed state. These separate durable steps are not atomic.
Repository Git commit/push execution is simulated by retained fixture evidence,
not a production Git adapter.

An opt-in pre-reviewed GoalPhase binding now checks separately retained,
host-owned local-commit authority before calling the fixture commit port.
Missing authority returns a strict common typed Decision on the Start-created
Awaiting instance without a commit, history append or authority grant.
Authority is bound to the exact Handle, instance revision, candidate bytes and
full request/evidence digest; malformed, stale or changed authority fails closed.
Decision identities remain host metadata, not callable human endpoints or
WorkOrder Results. Separately recorded exact authority permits one reviewed
fixture closure. Service / CLI retries and closure accept rehydrated ABI bindings
only when every immutable ABI fact and the remaining instance record match.
The validated baseline is a pre-reviewed-only fixture, not a human-result
resume engine or a production Git provider.

The same authority gate is now connected to automatic review / repair
completion in the explicit GoalPhase fixture assembly. It retains the
consumed accepted-review proof before authority lookup, supports host-only
Decision re-projection without another Result submission, and refuses commit
from that read-only projection. Ten additional executable specifications passed
focused-219 (42/42, four suites) and full-220 (507/507, 54 suites) on the same
tree. Already recorded exact authority permits one fixture commit; missing
authority stops on Decision after the one-shot review completion. Retention
failure leaves the Result consumed and the instance Active, without claiming
atomic rollback. Unstarted-218 remains historical; the newly synchronized
registered runner actually executed the uv-managed route in 219 / 220.
This adds no human Result/resume API or production Git provider.

SplitPhase now freezes unresolved novel-work and split-boundary inputs into
one typed semantic request. Its pure admission gate rejects context drift,
known-input overwrite, incomplete coverage and newly invented authority. Only
the missing inputs are supplemented, and the existing deterministic proposal
checks run again. The generated JudgmentAction / Required SPI, common WorkOrder
publication and public `submitSplitSemanticResult` Operation now connect this
foundation to automatic read-only PREVIEW on the same instance. Service / CLI
fixtures prove Start, semantic suspension, typed Result admission and Completed
PREVIEW through revisions 1–4, with no source-document write. Invalid coverage,
ranges, authority, context or state selectors are rejected before claim
consumption. `decision-needed` remains nonterminal WAIT, not authority Decision.
The semantic connection is PREVIEW-only; deterministic APPLY remains unchanged.

An opt-in, host-bound identity Decision projection now covers occupied child
Phase identities and nested/unknown source numbering. Public Start → assessment
returns CNCF `DECISION` with a strict typed input on the same Active revision-1
instance, without advancing, approving, previewing or applying. Host-supplied
human-boundary operation identities are stop-only metadata, not generated or
registered callable endpoints. They cannot reuse the semantic Required SPI or
its completion Operation. Invalid host context/payload and instance drift fail
closed; Decision cannot be admitted as a WorkOrder Result. The unconfigured
legacy assessment still returns WAIT. The same fixture assembly now binds both
identity Decision and semantic work to the same host ports and instance store.
Identity conflicts stop before AI work is published; collision-free semantic
work still completes automatic PREVIEW on the same instance. Changed identity
evidence during semantic work rejects completion before claim consumption,
without converting it into authority. Semantic `decision-needed` is still WAIT.
No human-result submission/resume engine is added.

RepositorySync now has a validated fixture connection for the
same stop-only projection after an admitted `decision-needed` merge review.
The public completion first saves Active revision 9 and retains the exact typed
review; an opt-in host port then projects Decision without granting commit
authority. A host-only read projection can recover a failed display without
repeating the one-shot review completion. No human completion endpoint is
registered, and the other dispositions / unconfigured binding preserve WAIT.
Focused-207 exposed an opaque-definition identity comparison error. The
comparison and its rehydration checks were corrected. After the command contract
separated read-only rendering from execution review, focused-210 passed 137/137
tests across two suites and full-211 passed 490/490 across 53 suites on the same
tree. Denied-208 / 209 remain unstarted historical attempts; their intakes were
not reused. This proves the stop-only fixture, not a human approval/resume engine.

Versioned application-owned ReasoningLevel mapping selects compatible Host
profiles without altering WorkOrders or Workflow guards. Host-attested external
completion uses CNCF normalization and records ExecutionEvidence; deterministic
Providers invent no dispatch profile. Fixture profile / model names are test
values, not a production model policy or evidence of actual Codex execution.

The [judgment guard map](docs/phase/phase-1-judgment-guard-map.md) now records
all eight alternatives of the three generated JudgmentActions. Connected
SplitPhase and reviewed-merge RepositorySync progression checks the actual
generated StateMachine edges, alongside the existing GoalPhase closure guard.
Workers cannot select events, target states or commit authority.

Phase 1 is complete: the actual Codex judgment round trip, deterministic worker
replacement and final intended-tree validation / artifact-dependency handoff
are retained below. A WAIT message or pure disposition assessment is not
evidence that a production path is implemented. Production runtime,
distribution, shared transactions and restart/recovery hardening remain outside
Phase 1; the default ComponentFactory is not the three-profile bound fixture
runtime.

Phase 2 preparation has inventoried the current server/datastore/bootstrap
facilities and the remaining CNCF Phase 88 / 87 integration prerequisites.
See the [entry inventory](docs/notes/phase-2-runtime-entry-inventory.md) and
[executable-specification checklist](docs/phase/phase-2-executable-specification-checklist.md).
No durable production Workflow runtime or public skill installation is claimed.

Component:

- artifact: `textus-sm-workflow` (`org.simplemodeling.textus.SmWorkflow`)
- package: `org.simplemodeling.textus.smworkflow`
- version: `0.1.0-SNAPSHOT`

The SplitPhase semantic-input foundation passed focused validation with 69/69
tests across four suites and full validation with 456/456 across 52 suites
(2026-09-28). The subsequent connected SplitPhase slice passed the explicitly
permitted five focused suites: 65/65 tests, including nine new connected
specifications. Its CML generation produced 65 Scala sources. Full-198 then
passed 465/465 tests across 53 suites on the connected product tree, reusing
those generated sources. At that slice, all 190 product / build paths matched its receipt;
full-191 remains historical foundation-only evidence. The subsequent GoalPhase
public Start-to-close composition passed focused-201 (31/31, five suites,
including nine new specifications) and full-202 (474/474, 53 suites).
Both validate the same tree; at that slice all 191 product / build paths matched
its receipt. The subsequent identity Decision slice passed focused-204
(68/68, three suites, eight new specifications) and full-205 (482/482, 53
suites), with SBT / wrapper exit 0 and `lock=released`. Both validate the same
tree; all 192 product / build paths matched full-205 at that slice. CML, generated ABI
fixtures, Cozy and CNCF source were unchanged in this slice.
The subsequent RepositorySync Decision repair passed focused-210 (137/137,
two suites) and full-211 (490/490, 53 suites), both with SBT / wrapper exit 0
and `lock=released` on the same tree. At that slice all 193 product / build paths matched
full-211; later documentation updates are outside its complete validated tree.
Focused-206 / 207 retain their failed evidence and denied-208 / 209 remain
unstarted. The updated command route was actually executed, not merely rendered.
The GoalPhase pre-reviewed authority slice passed focused-216 (32/32, four
suites) and full-217 (497/497, 54 suites), including seven new specifications
and rehydrated Service / CLI closure checks. Both identify the same tree with
SBT / wrapper exit 0 and `lock=released`. At that slice all 195 product / build
paths matched full-217; subsequent development is outside that earlier tree.
Compile failures 212 / 213 and earlier passing 214 / 215 remain historical
evidence in Phase 1. CML, generated ABI fixtures and upstream source were unchanged.
The integrated review / repair authority slice passed focused-219 (42/42,
four suites) and full-220 (507/507, 54 suites), with SBT / wrapper exit 0 and
`lock=released` on the same tree. At that slice all 196 product / build paths matched
full-220. Those post-validation documentation updates were outside its complete
validated tree; unstarted-218 is not reused as execution evidence.
The combined SplitPhase semantic / identity slice passed focused-222 (80/80,
four suites) and full-223 (517/517, 54 suites) on the same tree, with SBT /
wrapper exit 0 and `lock=released`. At that slice all 196 product / build paths matched full-223.
Focused-221 recorded 79 passed / one failed: common completion compared newly
rehydrated opaque ABI objects by identity. A local exact-ABI adapter repairs
this without changing CNCF or admitting foreign bindings. Ten new specifications
cover both ports through Service / CLI, pre-AI identity stops, semantic PREVIEW,
strict host / live-context checks and unchanged APPLY / semantic WAIT behavior.
Later documentation is outside the complete validated tree. That slice brought
Phase 1 to 28 of 34 items verified.
The subsequent generated judgment-guard slice passed focused-224 (199/199,
seven suites), including four new specifications of all declared entity edges
and judgment alternatives plus connected public disposition paths. All 197
product / build paths match its verified receipt. P1-S08 is now verified by
this focused evidence, bringing Phase 1 to 29 of 34. Full-225 was rejected by
platform execution review before the receipt helper or SBT started. After
explicit informed approval for one retry, full-226 passed 521/521 tests across
55 suites with SBT / wrapper exit 0 and `lock=released`; its immutable receipt
was independently verified. Full-225 remains denied and unstarted. All 197
product/build paths still match focused-224. Result-recording documentation is
outside full-226's complete validated tree.
The subsequent live-worker slice completes those application items. An actual
gpt-6-astra worker returned the issued ReviewStepResult; Host normalization and
public CLI completion preserved the same Start instance and exact semantic
payload. An independent known-candidate Provider retained identical definition/
revision/transition semantics without AI metadata; both stopped at missing
authority and performed no real Git mutation. Private temporary-file transport
removes dependence on unavailable runner-to-parent callback tools; discarded
stdin sessions are not worker proof. Focused-227 passed 35/35; focused-228 passed
37/37; final full-229 passed 526/526 tests across 56 suites, with verified receipt,
exit 0 and lock released. All 200 product/build paths and generated ABI identities
match the final intended tree. Phase 1 is complete (34/34 items), limited to
application/common-contract fixtures. Closure records follow validation and are
documentation-only. Production `sm-*` skill delivery, runtime/Git/SQLite/CAR
work is not claimed. See the [consumer handoff](docs/phase/phase-1-consumer-handoff.md)
and [live proof](docs/phase/evidence/goal-phase-codex-worker-20260929.json).
The initial focused-194 action was denied before SBT started. After informed
user permission, new invocation 195 ran and exposed a missing CML input kind;
196 exposed a generated Command-wrapper namespace collision. Both were fixed
in application CML before focused-197 passed. No upstream source edit was needed.
The preceding connected repair-loop slice passed focused 24/24
and full 447/447.
GoalPhase CML / generated ABI now uses `goal-phase-step-close-v2`, with an
ordinary Repair Operation, Required SPI and typed completion API. A bounded
CNCF receiver fix admits ordinary Required operations alongside exact CAM
actions; its focused tests passed 3/3. No Cozy source change was required.
The earlier denied full-178 action remains unstarted and was not retried; the
full-188 and subsequent full-191 results validate independently selected new
source trees, not retries of that action.
Exact source/fixture hashes,
validation-tree identity, runner identities and immutable receipts are recorded
in Phase 1. Later documentation-only acceptance updates are not included in that
validated tree. SBT runs only through the registered command runner and shared
serialized wrapper required by the workspace execution policy.

The standalone Scala 2.12 producer probe in `src/test/probes/` also exports
canonical GoalPhase JSON from the actual Cozy generators. The v2 checked-in
fixtures contain those exact generated bytes plus one trailing newline. The
probe does not replace current `cozyGenerate` or CNCF consumer specifications.

Generated Scala sources are written under `target/scala-3.3.8/src_managed/main/scala`.

The Task/FIX source adapters and passive existing-chat result/adoption Operations
are described in [Task/FIX delivery](docs/spec/task-fix-delivery.md). Source
packaging does not establish installed availability or actual chat authorization.
