# Command-review history transport investigation (2026-09-29)

Status: diagnosis recorded; one explicitly authorized full-test retry succeeded
as full-226. No general infrastructure repair is claimed.

## Incident and scope

The user authorized investigation and repair of context transport after
full-225 was denied, by the native current turn
`01a0ea88-4517-7db3-b3ba-1dc8eb5dfd67`, exact text `go ahead\n`.
The investigation concerns the earlier originating development turn
`01a0ea6d-a92a-7c90-95ed-11722e529011`, not a renewed SBT authorization.
No denied action was rerendered, redispatched, reconstructed or executed.

Expected: a runner fork selected to retain the saved originating user occurrence
actually contains that occurrence as native role=user history before command
review. A saved origin, summary or justification is not an authenticated
substitute for that history.

Observed: focused-224's child contains the direct originating `go ahead\n\n`
as role=user; full-225's child does not. Both used the same saved origin and
helper-selected positive `fork_turns = 1`. Full-225's refusal refers to an
older agents-synchronization request instead of the current development handoff.

Inspection used existing native thread records, relevant local rollout records,
the already-recorded renderer output and installed dispatch source. It did not
reproduce the denied execution. Logs were read only for message roles, origin
presence, timestamps and configuration identity; no credentials or private
reasoning were inspected.

Allowed update scope for this investigation is documentation in sm-workflow.
No source repair boundary has been frozen: implementation ownership is not
established inside this repository. Preserve its dirty product work and the
existing SplitPhase design-note change. The development-workstation checkout
also has existing changes in the runner, agent/command/protocol skills and a
protocol test; none were edited.

## Discriminating evidence

All timeline values below are UTC (JST is UTC + 9 hours).

| Event | Timestamp / native identity | Observation |
| --- | --- | --- |
| Focused-224 child creation | 2026-09-28T23:57:54.202Z / `01a0ea74-2084-7143-9baf-f00b29a347bb` | Two persisted response_item role=user messages: actual `go ahead\n\n` and injected AGENTS/environment instructions. Successful SBT receipt. |
| Parent compaction | 2026-09-29T00:02:52.283Z / parent `01a0a6d7-7043-73a2-a808-1367fe5fdb1f` | replacement_history has 66 items, including 62 role=user messages. The latest is still `go ahead\n\n`; the originating turn ID is retained in the compaction record's context. |
| Full-225 child creation | 2026-09-29T00:04:42.222Z / `01a0ea7a-5a5d-79b0-accd-c84d2b5f140e` | One persisted response_item role=user message: injected AGENTS/environment instructions only. No originating `go ahead\n\n`. Renderer accepted; platform denied before helper/SBT start. |

The raw originating text SHA-256 is
`e82e2eedbf1737273559fb15dbb8e68bf3aa18170fb9760ae7083386d3ce08a5`.
This text occurs four times in the compaction replacement history. Equal text
does not authenticate a specific turn occurrence; no nearest-text substitution
is permissible. The AGENTS/environment text in both children is identical:
`4273f5dd3159a9b2930df745e8254f448cf74b2eb72916388b26cd120ab32895`.

Both child session metadata records identify the same native parent and the
expected registered `cncf_command_runner` path. The first developer message
also has the same SHA-256 in both children:
`7eb7646c2db4e9f1b40698ffa6028f315b27b93780b3297537fd9d28a195c0aa`.
Neither child has a local compacted record. Native thread summaries alone do
not expose inherited messages, so the persisted rollout message-role comparison
was needed.

Evidence locations (existing user-owned records; no edits):

- Parent:
  `/Users/asami/.codex/sessions/2026/09/16/rollout-2026-09-16T05-52-11-01a0a6d7-7043-73a2-a808-1367fe5fdb1f.jsonl`.
- Focused child:
  `/Users/asami/.codex/sessions/2026/09/29/rollout-2026-09-29T08-57-54-01a0ea74-2084-7143-9baf-f00b29a347bb.jsonl`.
- Full child:
  `/Users/asami/.codex/sessions/2026/09/29/rollout-2026-09-29T09-04-42-01a0ea7a-5a5d-79b0-accd-c84d2b5f140e.jsonl`.
- The [Phase 1 command record](../phase/phase-1.md#full-225-platform-denial-before-any-helper--sbt-execution)
  preserves the denied exact request, origin, runner IDs and raw refusal.
  No second copy of that refusal or a fabricated command receipt is introduced.

## Hypotheses and confidence

1. **Confirmed transport failure:** the saved origin / selected fork count and
   the actual full child's native role=user history disagree. Both renderer
   outputs are accepted, with the same workdir, require_escalated permission
   and exact receipt-helper prefix. Parent native history confirms the current
   development handoff. The failure is before command/helper execution.
2. **Probable compaction-sensitive fork selection:** parent compaction is between
   the known-good and missing-origin forks. A turn-count suffix computed from
   parent-observed native turns does not prove the runtime's active compacted
   history contains the same originating occurrence. This temporal/control
   evidence identifies a likely boundary, not a fully reproduced Codex defect.
   Runtime instrumentation or an independently authorized upstream reproducer
   is still needed to distinguish fork slicing from other history assembly.
3. **Rejected stale-deployment hypothesis:** deployed and canonical
   development-workstation runner definitions match SHA-256
   `20d41d4b3633ad74f716f9c6191bb0b20834a6ff171c63796f68043e49f4c740`.
   The deployed file is regular, not a symlink. The old Dropbox runner differs,
   but it is not the deployed agent source; skills resolve to development-workstation.
   No copy/symlink repair is warranted.

The narrow reproducer description is: retain a native development request,
start a registered receipt-first runner with a positive suffix, compact the
parent during the same ongoing user turn, then observe a later positive-suffix
fork's originating user occurrence. A successful renderer alone is insufficient.
This description was derived from existing evidence; it was not executed here.
Determinism is unknown. The focused command and full command differ, so this
is not a controlled proof that compaction alone caused the reviewer denial.

The reviewer's exact internal compact transcript is not available. Therefore
the investigation does not claim to know its complete input, why it selected
the older agents request, or that all full-suite executions are prohibited.

## Dispatch implementation boundary

The installed
`/Users/asami/.agents/skills/cncf-workflow-protocol/scripts/sbt_runner_spawn.py`
calculates `retained_turn_count = len(observed turns) - origin index` (line 297)
and returns it as `fork_turns` (line 319). It explicitly marks history
`parent-observed-not-platform-authenticated` and cannot introspect native
conversation storage. The history contract requires independent origin checks
and treats explicit platform denial as terminal.

The existing public spawn interface accepts a turn count, not an authenticated
origin-message selector or post-fork native-retention attestation. Repairing
the count speculatively, choosing `all`, copying user text into a delegation,
or rewording justification cannot prove native provenance. It must not be used
to reconsider the denied action.

The necessary repair boundary is native Codex fork/history construction or an
authoritative host-side verification boundary that establishes the exact user
occurrence survived compaction. A future implementation must fail closed when
that occurrence is absent; it must not authenticate parent-written summaries
as user messages. Acceptance needs a native compaction-crossing scenario, not
only a Python unit test or a mocked renderer. No such implementation or native
verification capability is available in this repository, so no local security
or public-protocol substitute was added.

## Official product evidence and recovery limits

OpenAI Docs confirms that Auto-review evaluates a compact transcript and the
exact proposed action, rather than private assistant reasoning; the reviewer
is separate from the main agent. It also documents a narrow TUI `/approve`
denial override, recorded for one exact action and still reviewed. This does
not establish that the desktop host exposes that same UI, and does not authorize
an agent to retry by changing envelopes or routes.
[Official Auto-review documentation](https://learn.chatgpt.com/docs/sandboxing/auto-review).

Subagents inherit parent permission policy and live runtime overrides; replacing
a custom agent's sandbox default is not a context-transport repair.
[Official subagent documentation](https://learn.chatgpt.com/docs/agent-configuration/subagents).

The applicable cncf-command-execution denial contract prohibits redispatch or
requesting repeated approval/manual execution. cncf-diagnose-fix requires a stop
when repair belongs to another execution base or requires an upstream capability.
Those boundaries caused this investigation to stop without speculative skill,
agent, permission-policy or harness edits. The present `go ahead` authorizes
this investigation, not a native override of denied-225.

## Outcome and usage ledger

- Current incident: native originating-user history absent in full-225; unresolved
  transport repair. Root cause is probable compaction-sensitive history assembly,
  not claimed confirmed reviewer implementation causality.
- No infrastructure repair performed; no repair verification claim.
- Original denied reproducer was not rerun. No SBT, command runner or new subagent
  was started, resumed or messaged during this investigation.
- Product source, CML, checked-in ABI and upstream agent/skill definitions unchanged.
  Only this supporting note and a Phase 1 cross-reference were written.
- Phase 1 remains in_progress, with 29 verified / 2 partial / 1 missing /
  2 pending-final items. Existing focused-224 evidence remains valid; full-225
  remains unstarted/denied. No new Phase/Task/Step/Slice, Hygiene or Development
  Candidate was created for the infrastructure condition.
- No commit, staging, push, publication, cleanup, policy weakening or credential
  access occurred. Documentation bytes are outside the earlier validated tree.
- Verification for this documentation record: preserved native evidence,
  local-link checks and git diff --check; no original runtime repair verification.
  Broader full validation and application-worker proof remain deferred.

Agent Usage Summary (parent-only; exact model/effort not exposed by this interface):

| Agent | Model | Reasoning effort | Runtime / type | Roles / states | START | RESUME | DIRECT | Scope / outcome |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `/root` | unknown | unknown | parent runtime / parent-task | diagnosis / DIAGNOSE | 0 | 0 | 1 | Read-only causal inspection and documentation bookkeeping; no FIX or repair claim |

## Development continuation decision (2026-09-29)

The user subsequently reported that `approve` is absent from the desktop slash
command menu and asked Astra to find a way to continue development. No native
denial override is established by that UI. The earlier suggestion to use
`/approve` is therefore not an actionable recovery instruction for this host.

The development stop was broader than the evidence requires. Full-225 is an
unstarted denied command, not a failing executable specification or a ban on
all repository work. Focused-224 already supplies the bounded P1-S08 evidence.
The existing checklist still contains independent application work in P1-S06
and P1-S07; it does not require full-225 to pass before that work can be designed
or implemented. P1-C01 requires validation of the final intended tree after
remaining application work, not an intermediate full run after every slice.
This observation does not grant permission to retry the denied command.

### Concrete next application boundary

Use GoalPhase's existing public fixture connection for the first actual-worker
proof. Inspection confirmed these reusable boundaries:

- `GoalPhaseRepairLoopSpec` already exercises public `startGoalPhase`,
  `requestStepClose`, the emitted WorkOrder, `submitStepReview`, and terminal
  progression on one Start-created instance through Service and CNCF CLI JSON.
- `WorkflowWorkerMapping.selectC` freezes a compatible host profile and policy;
  `normalizeC` checks the returned profile/model/policy before delegating to
  CNCF's `SkillWorkResultNormalizerV1`.
- `GoalPhaseReviewBoundarySpec` currently labels its external completion a
  **Host-completion stand-in**. That successful specification must not be
  relabelled as an actual Codex execution.

The bounded implementation sequence is:

1. Extract only the reusable test fixture needed to issue and retain a real
   common WorkOrder through the existing public Operations. Keep its instance,
   candidate bytes, context and continuation unchanged across the exchange.
2. Provide a tiny test-only Host exchange adapter for that issued WorkOrder and
   the returned common Skill result. It transports existing CNCF JSON and calls
   the Continuation-selected completion Operation; it adds no application-owned
   lifecycle or public production CLI. A live exchange must retain the same
   fixture instance rather than silently reconstructing another instance.
3. Dispatch one bounded candidate review to an actual Codex worker. Retain the
   issued JSON, candidate context, actual worker/model identity and raw result.
   The worker returns only the admitted decision, rationale and evidence; the
   host supplies observed execution provenance, and the Workflow determines
   progression. A hand-authored result or SBT runner completion is not proof.
4. Normalize the actual response and submit it through `submitStepReview`.
   Assert the typed result, matching Handle/revision/context, one-shot admission
   and resulting fixture progression. Use fixture commit ports, not real Git
   mutations. Do not force an acceptance decision when the actual judgment
   requests repair or a human decision.
5. Exercise an equivalent fresh fixture with a deterministic Provider and the
   same semantic judgment. Compare Workflow definition/revision and transition
   semantics while permitting independent instance identities and different
   execution provenance. The deterministic Provider records no invented Codex
   worker/model evidence. Keep live observation distinct from later replay of
   the recorded transcript.
6. Validate this bounded application change with its focused executable
   specifications through the required registered, serialized SBT route. Only
   successful observed execution can verify P1-S06/P1-S07. Preserve every denial
   as such; selecting genuinely new work does not automatically permit its
   commands or recover full-225.

Once the remaining application work is verified, record the exact three-profile
artifacts and resolved dependencies for P1-S05, and select the final intended-tree
validation required by P1-C01. Its execution remains subject to platform review.
Neither a different attempt ID nor a changed tree is, by itself, a supported
override of the earlier denial. Do not introduce cosmetic changes or split the
denied full suite into commands to obtain the same rejected execution indirectly.

This continuation decision creates no infrastructure Phase/Task/Step or new
closure gate. It establishes a concrete route for already-scoped application
work; it does not claim that the Host exchange adapter exists, that an actual
worker has run, that the transport defect is repaired, or that future execution
permission is guaranteed. At this decision point the checklist remains
29 verified / 2 partial / 1 missing / 2 pending-final. Only this supporting note
and the Phase cross-reference were updated; no runner or SBT was started.

## Explicitly authorized retry completed (2026-09-29)

After the exact full-test target, execution route and filesystem/network effects
were explained, the user replied `承認する\n` in native turn
`01a0ea9d-cfa2-7fc0-bcaa-03ef8a127cae`. The preceding scope explanation is in
turn `01a0ea9c-471c-7ba2-999d-92839b550f3d`. This supplied explicit informed
authorization for one retry of the denied test, a reconsideration condition
named in the actual platform refusal. The earlier claim that only a native
`/approve` marker could permit reconsideration was too restrictive.

That later user instruction controlled this one retry despite the skill's
blanket no-redispatch guideline. No policy, skill, runner or permission setting
was edited. The original full-225 artifacts and denial remain immutable; they
were not replayed or relabelled as successful. A separate explicit-user anchor
retains the new native approval occurrence, and full-226 records the original
denial and one-retry purpose. It is not an expiry refresh or an unrelated test.

The registered `cncf_command_runner` at
`/root/sbt_receipt_a42c0daaba1d8c12b61c` (gpt-5.6-luna / medium) submitted the
unchanged rendered scoped execution request through ordinary platform review.
The process actually started and completed **521/521 tests across 55 suites**,
with SBT / wrapper exit 0 and `lock=released`. The verified receipt is
`aaa2248e80283fe664342f9a4806be3ee62081722cdc68b177f647c8a240cd21`, binding
complete tree `e1d6ed112fa015ede9e4cd474f91a177c669936058f8401797569ab1d5e28402`.
See the [full execution record](../phase/phase-1.md#full-226-explicitly-authorized-retry-completed).

This proves permitted execution for this one informed retry. It neither repairs
nor disproves the earlier compaction/history transport finding, and it grants
no automatic retry authority for another denial. The remaining P1-S06 / S07
application proof and final Phase handoff remain open. Documentation recording
the result was updated after validation and is outside its complete receipt tree.
