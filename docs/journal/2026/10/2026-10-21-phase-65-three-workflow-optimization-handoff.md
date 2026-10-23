# Phase 6.5: 三つのworkflowのAI呼出し削減と開発handoff

date=2026-10-21
status=adopted-plan-not-implemented
intended_chat=sm-workflow-dev開発
related_chat=sm-workflow開発

## 採用範囲と正本

ユーザーはRepositorySyncとSplitPhaseのレビュー結果をPhase 6.5へ採用した。
LLM評価はスコープ外。既存の対応経路でLuna化できれば採用するが、できなくても
適切な既存モデルで機能要件を満たせば6.5は完了できる。モデル比較と高度なprovider
routingはPhase 9に残す。Local LLM評価をPhase 9の必須条件に追加したものでもない。

- [共通原則とworkflow別仕様](../../../spec/workflow-semantic-gap-routing.md)
- [GoalPhase仕様](../../../spec/goal-phase-step-assessment.md)
- [Phase 6.5計画](../../../phase/phase-6.5.md)／[チェックリスト](../../../phase/phase-6.5-checklist.md)
- [Phase 9責務](../../../phase/phase-9.md)
- 検討元: [RepositorySync案](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/10/2026-10-21-repository-sync-ai-optimization-review.md)
  と[SplitPhase案](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/10/2026-10-21-split-phase-ai-optimization-review.md)

GitHubの両案は設計入力。Luna比較実験や全semantic処理の一括化をそのまま実装要件には
しない。先行する[deterministic assessment handoff](2026-10-21-phase-65-deterministic-assessment-handoff.md)
のGoalPhase方針は維持し、Q3とQ4の間にQ3-R/Q3-Sを加える。

## 開発チャット向け実施順序

1. **Q1〜Q3: GoalPhaseを先に通す。** compile/focused/適用lintと実結果のassessmentから
   AIなしStep受入れ・commit。Step/Release FIX、最小Scala lintと意図的抑制、
   必要時triage／明示された詳細レビューを接続する。既存チェック項目は維持する。
2. **Q3-R主経路: RepositorySyncの正常マージ。** conflict-free pending mergeから
   選択検証・適用lint・機械判定、正式なmerge admission、native commit、許可された
   convergenceまでAIなしで通す。実pending candidateと両親・treeの既存契約を使う。
   ReviewAcceptedの偽装や非merge用IntegrationValidatedへの置換は不可。
3. **Q3-R枝: FIXと必要な意味判断。** テスト/lintの明確な失敗は、AI指摘を捏造せず
   bounded FIXへ直結。修正後は影響検証と同じ統合処理へ戻す。意味的不確実性だけtriage、
   明示必須レビューは直接実施。AIによる競合解消には独立した詳細レビューを残す。
   再レビューは指摘・修正差分・影響範囲に限定し、既存判断を再利用する。
4. **Q3-S主経路: SplitPhaseの既知入力・既存回答再利用。** 既存の計算エンジンで
   AIなし経路を確認し、保存済みsemantic回答とfrontierをAdvance/reopenで再利用する。
   再読取のためのAI再呼出し、分割再計算、全context再取得を減らす。
5. **Q3-S枝: 未知事項とcontextの最小化。** 実際に未解決の見積り・境界・scope・reasoning
   のみ既存型で依頼し、既知値と最終分割のruntime所有を維持する。通常のenrichmentに
   二重AIレビューは不要。同時点で既知の独立したgapだけ、安全で効果がある場合にまとめる。
   後段で初めて決まるgapを先読みして質問しない。一括化機会がなくても完了可能。
6. **Q4: 接続受入れと引渡し。** 既存の適用可能なテスト・レビュー結果を再利用し、
   変更した分岐と実workflow/client接続を検証する。選択済みfull方針を適用し、
   HEAVYやモデル評価を追加しない。Phase Reviewは統合確認、Finalは結果再利用とする。

共通化は原則と既存機構の再利用に留める。三つのworkflowを新しい汎用engineへ
載せ替えたり、Gap管理台帳・スナップショット・評価基盤を追加したりしない。
既存Q1〜Q4とP65-01〜20は保持。新規P65-21〜27を追加し、Q4の接続対象を拡張した。
項目の追加は完了認定ではなく、すべてplanned/pendingのまま。

## 実装で解消する契約の差

読取確認時点のRepositorySyncNativeConvergenceは、mergeのコミットにReviewAcceptedを
要求し、IntegrationValidatedはmergeHeadのない経路に限る。NativeMergeValidationも
MergeValidatedの後にreviewへ接続する。レビューを単にOFFにする変更だけでは進まない。
正式merge admissionと実consumerの接続が必要。

現在のnative merge repair仕様はReviewRepairRequiredを起点としている。
検証失敗起点のFIXを、既存のbounded修正・stage・継続へ接続する。検証不明と失敗は
分け、不明な効果を自動再実行しない。

SplitPhaseは既にSemanticRequiredと既知値保持を実装し、scope/reasoningは後段で
発生し得る。機械的受入れは型・coverage・数値制約・分割条件に対する保証であり、
未知作業の見積り自体の正しさまで証明しない。ただし毎回の追加AIレビューは設けず、
実際に矛盾や残存する意味判断がある場合に限って対応する。

DecisionNeededは既存Human経路を持つ。能力不足、情報不足、ユーザーの方針／権限判断、
通信・実行失敗を同じDECLINEにまとめない。強いモデルでHumanの意思決定を代替しない。
Luna化のためだけに新しいprovider switchingやconfidence scoringを作らない。

## Lunaとコストの扱い

通常の作業で既存設定からLunaを使えるなら採用する。満たせない場合は適切な既存profileへ
戻す／維持する。対応経路がなければ現行profileで進める。Lunaで成功するまで再試行する
運用、比較用の課題セット、精度／性能の採点、Local LLM導入は行わない。

通常実行で観測できた呼出し有無・選択profile・理由・結果・再利用はcommon Journalへ
runtimeが記録する。保存失敗で元Actionをやり直さず、記録専用AIも呼ばない。
Luna採用できなかったことは品質失敗でも6.5 blockerでもない。一方、必須の検証や
workflow受入れは省略しない。モデル選択より先に不要な呼出しをなくす。

## 保持と採用手順

今回の編集はsm-workflow-devの仕様・計画・journalのみ。進行中6.4/12、既存Goal・Session・
E1受入れ・実装・修正回数・成功/失敗の検証結果は変更しない。開発チャットは現在の
作業を保持して完了し、後続6.5の計画として採用する。live SessionのStartや初期化、
保存済み判断の直接編集、既存検証の一律無効化、旧履歴の上書きはしない。
既存機能は作り直さず、適用可能な受入れ結果を残して不足接続だけ補う。

このhandoff作成時点ではruntime実装・SBT実行・配備・commit/pushは行っていない。
本書は開発チャット向けの採用指示であり、実行済み・配備済みの報告ではない。

## 追記: Step早期警告／Release単一統合レビュー

同日、ユーザーが
[Step early warning / Release semantic assurance案](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/10/2026-10-21-step-early-warning-release-semantic-assurance.md)
のレビュー結果を採用した。[GoalPhase仕様](../../../spec/goal-phase-step-assessment.md)、
[6.5計画](../../../phase/phase-6.5.md)、[チェックリスト](../../../phase/phase-6.5-checklist.md)
を具体化した。上記の開発順序Q1→Q2→Q3→Q3-R→Q3-S→Q4を維持する。

### 開発チャットへの追加指示

1. **Stepは早期警告と継続可能性を確認する。** Step自身の受入条件と後続作業の前提を
   満たし、検証済み部分を再確認しない。通常の増分コンパイル・focused test・適用lintを使い、
   毎Stepのclean buildや意味レビューは追加しない。文書だけのStepは適用検証を使う。
2. **即時対応とRelease確認をPLANで分ける。** 必須検証の失敗・不足、明示必須レビュー、
   後続作業が依存する保証の不確実性はStepで処理する。後続Stepで完成予定の統合事項や
   非ブロッキング観測は、Step自身の条件と後続前提が満たされる場合に限りReleaseへ回せる。
   延期可否を毎回AIやユーザーに問い直さず、既存policyと実際の根拠で扱う。
3. **延期を解決済みにしない。** source・未解決事項・延期理由・予定作業・Release確認先を
   既存Journalへ残す。Step commitはその観測のCLEAN／Finding認定／解決を意味しない。
   依存条件が変わった場合は即時対応を再評価し、そうでなければ毎Step再審査しない。
   Releaseでは後続の実解決結果も含めて消化し、必須の意味判断を未解決のままreleaseしない。
4. **構造検知は既存対応関係の範囲内。** Contract・Operation・feature・scenarioの明示mappingと
   inventoryの完全性が利用できる場合だけ構造的signalにする。featureタグだけでGiven/Thenの
   網羅性を推定しない。テストファイル変更なしでも既存scenarioが対応できる。
   metadata不足はcoverage欠如の証明ではなく、未対応の任意detectorはOFFのままでよい。
   有効な必須checkの失敗・取得不能をOFFに落としてはならない。
5. **AI Semantic Inspectionは既存triageの別名。** 新Actionは増やさない。
   即時に意味判断が必要な場合だけ呼び、CLEAN/SUSPICIOUS/DECLINEの既存契約を使う。
   明示必須レビューは直接実行する。CAR lintの構造チェックをsm-workflowで二重実装しない。
6. **Releaseは一つの統合レビュー。** Plan/Usage Contract、Given/When/Thenの意味、実装との対応、
   Step間の統合、Component境界、状態や機構の重複、過剰な複雑さを同じreviewの観点として扱う。
   既存Phase Reviewがこの役割を果たす場合はそれを使い、別のcomprehensive reviewを追加しない。
   既存compile/test/lint/reviewを入力に、残る意味判断と統合を確認する。
7. **FIX後は影響範囲だけ。** Releaseのblockingな意味対応不備は既存FIXへ渡し、
   指摘・修正差分・影響する意味だけ再レビューする。全体レビューをやり直さず、
   Finalは受入れ結果を再利用する。修正回数や過去の受入れをリセットしない。
8. **フィードバックでStep負担を自動増加させない。** Releaseで十分だった指摘には
   新しいStepチェックを足さない。早期検知が必要だった場合だけ、安価なcompiler/model・
   テスト選択・lint等の改善候補として同じreview/resultとJournalでまとめる。
   指摘ごとのAI振り返り、全非ブロッキング指摘の修正義務、新しい解析台帳は追加しない。

このRelease方針はGoalPhaseの既存release境界に適用する。RepositorySync/SplitPhaseに
新しいReleaseレビューを付け足さず、それぞれの正式な受入れ契約を維持する。

### 受入れと保持

P65-01〜27のIDとpending状態を保持し、P65-28（即時／延期）、P65-29（構造signalと
Inspection同一化）、P65-30（単一統合レビューとFIX・記録再利用）を追加した。
延期可能な観測を残してStep commitできる経路と、即時条件を延期できない経路を区別する。
既存の受入れシナリオへ組み込み、同じ保証を別テストやレビューで重複確認しない。

CBDはStep/ReleaseともPhase 10までOFF。LLM評価・モデル比較は6.5対象外のまま。
既存対応経路でLuna化できれば採用し、できなくても適切な既存profileで完了可能。
新しいCNCF/Cozy/CBD解析機能の全面完成を6.5の前提にしない。

今回も仕様・計画・handoffだけを変更し、6.4/12の進行中作業、既存実装、Goal/Session、
E1受入れ、履歴、修正回数、成功/失敗の検証結果は保持する。live workflowの直接変更、
Start/Submit/Advance、SBT実行、配備、commit/pushは実施していない。
