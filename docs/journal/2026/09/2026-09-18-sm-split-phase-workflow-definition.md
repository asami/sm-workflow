# SM Split Phase Workflow Definition

Date: 2026-09-18

Status: Design decision

## Decision

`sm-workflow` の分割Workflow名は `SplitPhaseWorkflow`、対応する配布skill名は
`sm-split-phase` とする。両者は`SkillBundleManifest`で明示的にbindする。
legacy `cncf-split-phase` は変更せず、独立した互換workflowとして長期併用する。

`SplitPhaseWorkflow` は、`sm-goal-phase` の `SPLIT_REQUIRED` recommendation から自動開始
しない。人間が `sm-split-phase` を明示選択した場合だけ独立 run として開始し、prior run
からは typed source/evidence/proposal reference だけを入力として受ける。split terminal 後も
child goal を開始せず、人間が選択した child ごとに別の `sm-goal-phase` run を開始する。

Normative design input:

- [SM Split Phase Workflow Definition](../../../notes/sm-split-phase-workflow-definition.md)

## Cost boundary

AI cost reduction を明示的な受入目標とする。current で complete な prior split proposal
があれば semantic AI invocation は 0 回とする。proposal がない場合も、過去 Phase の
予定/実績時間と作業分類を versioned policy で収集・calibrate し、contiguous candidate
を列挙して Workflow が決定的に最適化する。既知作業と明示済み boundary だけなら AI は
0 回、未知作業または未確定 boundary があるときだけ `AssessNovelSplitWork` 1 回を目標とする。

履歴収集、既知作業の見積もり、候補列挙/選択、番号付け、scope inventory、
packing/validation rule、document projection、collision scan、three-way merge、static
validation は Workflow-owned deterministic operation とする。

## Conflict decision

apply 中の競合は base/current/desired の typed three-way model で扱う。

- 結果が一意で invariant を保存できる競合は Workflow が自動マージする。
- ownership、goal、closure、dependency、handoff 等の意味的競合だけを
  `ResolveSplitMergeConflicts` AI Action に委譲する。
- identity collision、nested numbering、completed-history rewrite、複数の妥当な authority
  resolution は human Decision で停止する。

AI は file edit や Git command を行わない。AI の typed resolution は Workflow validator
と compare-and-set write を経て初めて適用される。

未知作業/boundaryのAI補完も `SEMANTIC_ENRICHMENT_ADMITTING` を通し、semantic mergeは
`RESOLUTION_VALIDATION` を通す。AI resultからproposalまたはwrite setを直接更新しない。
skillはWorkflowが選択したexact AI requestを一件実行してresultを返すだけで、resultから
follow-up処理をdispatchしない。

Workflow/skill/terminal result は別 profile を dispatch せず、host/client は候補表示だけを
行う。開始権限は常に明示的な profile/child 選択から得る。

## Consequence

split skill は thin participant になり、会話履歴を replay して番号付け、編集、検証、
衝突処理を再推論する必要がなくなる。AI sandbox は緩和せず、managed provider が
current state/revision/guard と operation allowlist の中で planning mutation を行う。

この Workflow は product implementation、SBT/runtime validation、child goal start、
commit、push を行わない。
