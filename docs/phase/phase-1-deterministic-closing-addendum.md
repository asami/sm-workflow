# Phase 1 Addendum: Deterministic Operations and Closing

Status: planned / normative addendum to [Phase 1](phase-1.md)

## Goal extension

Phase 1 の `advance` は automatic transition に加えて、Workflow が明示した typed deterministic operation を実行し、次の semantic boundary または terminal state まで進行する。

AI/Codex は PLAN、EDIT/REPAIR、REVIEW、exception analysis 等の semantic work に限定する。build、test、Executable Specification、local git closing を含む procedural external command は、typed operation として admission できる限り `sm-workflow` Workflow runtime が component-local provider を通じて実行する。Skill は外部 process を起動しない。

## Design invariants

1. raw shell / arbitrary script を Workflow Action として公開しない。
2. external process は `BuildProject`、`RunTests`、`RunExecutableSpecification`、`InspectChanges`、`StageChanges`、`CommitChanges` 等の typed Operation implementation/provider の内部から実行する。
3. `advance` は automatic transition と許可された deterministic operation を bounded に処理する。
4. deterministic operation は input/output、permission、failure semantics、idempotency policy、receipt/evidence を持つ。
5. REVIEW ACCEPT 後に COMMIT Work Order を発行しない。
6. REVIEW ACCEPT の受理後は Closing へ遷移し、local git commit まで server-side に処理する。
7. commit SHA を WorkflowRun Evidence/Receipt として保存する。
8. push、PR、merge、deployment は Phase 1 Non-Goal の remote publish として local commit から分離する。
9. deterministic operation failure は自動的に Codex Work Orderへ変換せず、retry / WAIT / WORK_ORDER / DECISION / failure の明示規則で分類する。
10. AI tool sandbox は緩和しない。Workflow provider は operation registry と current state/revision/guard により admission された command だけを実行し、operation-specific executable、typed argv projection、working directory、mutation root、network/credential policy、timeout、receipt を強制する。
11. AI/Skill からの executable、free-form argv、generic shell、arbitrary script の注入は fail-closed で拒否する。
12. document apply conflict は typed base/current/desired model で分類し、一意で invariant-preserving な merge は Workflow provider が実行する。
13. Workflow が一意に解消できない semantic conflict だけを bounded AI Action に委譲し、authority conflict または複数の妥当解は human Decision で停止する。
14. AI conflict resolution は file mutation ではなく typed resolution plan を返し、Workflow validation と compare-and-set write を必須とする。
15. すべてのsemantic AI Actionはdeterministic prepareとdeterministic admission/validationの間に置き、AI resultからcommit/terminalへ直接遷移しない。
16. AI result schemaはnext state、command、validation acceptance、ledger mutation、cycle count、retry policy、commit readinessを拒否する。

## Reference workflow extension

```text
Requested
 -> [automatic] InitializePlan
 -> [semantic] PLAN
 -> [semantic] CHANGE
 -> [deterministic] BuildProject
 -> [deterministic] RunTests
 -> [deterministic] RunExecutableSpecification
 -> [semantic] REVIEW
 -> ACCEPT
 -> [automatic] EnterClosing
 -> [deterministic] InspectChanges
 -> [deterministic] StageChanges
 -> [deterministic] CommitChanges
 -> [automatic] RecordClosingEvidence
 -> [terminal] Completed
```

Build/test/spec の配置は profile により変更可能だが、reference workflow では deterministic operation が Codex/model invocation を必要としないことを実証する。

## Acceptance extension

- semantic work の間にある build/test/spec/git 処理で Codex/model invocation が発生しない。
- Review ACCEPT から Completed まで、追加の semantic boundary が不要な正常系では追加 Codex turn がゼロである。
- CommitChanges は accepted review/validation evidence と current revision を precondition として検証する。
- commit failure 時に不正な Completed へ遷移しない。
- successful closing receipt に commit SHA と operation evidence が含まれる。
- remote push/PR/merge/deployment が Phase 1 closing から実行されない。
- non-overlapping/generated document conflicts は追加 Codex turn なしで merge される。
- semantic conflict だけが AI Work Order になり、AI result は Workflow 側の invariant validation と compare-and-set write を経る。
