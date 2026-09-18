# SM Goal Phase Workflow Definition

Date: 2026-09-18

Status: Design decision

## Decision

`sm-workflow` の最初のreference Workflow名は `GoalPhaseWorkflow`、対応する配布skill名は
`sm-goal-phase` とする。両者は`SkillBundleManifest`で明示的にbindする。legacy
`cncf-goal-phase` は変更せず、独立した互換workflowとして長期併用する。

legacy skill の手続き全文を `sm-goal-phase` skill に写すのではなく、Phase Entry、PLAN、
Step/Slice delivery、review、repair、commit、Phase closure を CML/CNCF の純粋な
Workflow / StateMachine として定義する。

Normative design input:

- [SM Goal Phase Workflow Definition](../../../notes/sm-goal-phase-workflow-definition.md)

## Boundary

- Workflow が state、guard、repair/review budget、evidence gate、next Action を所有する。
- deterministic/internal Action は provider が実行し、`advance` が次の semantic boundary
  まで吸収する。
- Git/SBT を含む procedural external command の exact execution、process completion、
  retry/failure classification、receipt 永続化は可能な限り Workflow runtime/provider
  が所有し、skill/AI は実行しない。
- AI 担当 Action だけを IoC で external provider に bind し、
  `Suspended(Continuation)` と typed Result/Evidence で skill に接続する。
- model、reasoning effort、agent role、prompt、command routing、Markdown status は
  Workflow semantics に含めない。
- human decision は AI skill と別の participant binding を使う。
- Phase Entry、provisional adoption、planning、implementation、review、repair、re-review は
  deterministic prepare -> semantic AI -> deterministic admission/validation に分け、AI result
  からcommit/terminalへ直接遷移させない。
- skillはWorkflowが選択したexact AI requestを一件実行してresultを返すだけとし、resultを
  見て次のskill/agent/operation/command/stateを選ばない。

## Consequence

skill は thin execution driver になる。skill が state graph、retry、repair cycle、
review count、external command、commit readiness を再実装しないため、conversation
history と model turn への依存を減らせる。

AI tool sandbox は緩和しない。各 typed operation が executable、argv projection、
working directory、mutation root、network/credential policy、timeout、receipt を明示し、
Workflow runtime が current state/revision/guard と provider allowlist の中だけで実行する。
これにより AI に command 権限を与えずに、管理された command execution を提供する。

actual CML source、generated ABI、CNCF admission、runtime binding は Phase 1 の fixed
upstream handoff gateを満たした後に実装する。この文書はその実装前に semantic
definition を固定する設計入力である。
