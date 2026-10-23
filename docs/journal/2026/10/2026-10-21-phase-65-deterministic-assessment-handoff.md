# Phase 6.5: deterministic assessmentと必要時だけのAI判定

date=2026-10-21
status=adopted-plan-not-implemented
intended_chat=sm-workflow-dev開発

## 採用した変更

ユーザーは、deterministic assessmentでStep受入条件を満たせばAIを省略し、
意味判断が残る場合だけAI Bad Smell Triage、さらに必要なら詳細レビューへ進む方針を採用した。
[仕様](../../../spec/goal-phase-step-assessment.md)、[Phase 6.5](../../../phase/phase-6.5.md)、
[チェックリスト](../../../phase/phase-6.5-checklist.md)へ反映した。

検討元はGitHubの
[Bad-Smell案](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/10/2026-10-21-phase-6.5-bad-smell-triage-and-finding-management.md)。
その全Step必須AI検出を採用せず、必要時だけの二次判定へ変更する。
同案のCBD必須化・AI Final Recording必須化・モデル固定をまとめて採用したものではない。

## 実行分岐

1. 必須検証が未実行・取得不能・結果不明なら、既存の実行準備／結果観測経路で扱う。
   AI CLEANで不足を代替しない。不明な効果を盲目的に再実行しない。
2. 明確な受入条件違反なら、既存FIXと影響範囲の再検証へ進む。
3. 検証を満たし、PLANで詳細レビュー必須なら、triageを挟まず直接詳細レビューへ進む。
4. 検証を満たし、未解決の意味signalと必須レビューがなければ、AI呼出しゼロでStep受入れ・commit。
5. 検証を満たしても意味判断が残るなら、独立した軽量AI triageへ進む。
   CLEANなら通常受入れ、SUSPICIOUS／意味判断のDECLINEなら根拠を渡して詳細レビューへ進む。

「十分にCLEAN」は宣言された条件と選択policyの充足であり、全欠陥の不存在証明ではない。
lintが0件でも、目的に比べた変更量、dependency/Component境界、public contract、
観測可能な構造複雑化を根拠にtriageへ進める。signalだけで不具合・修正義務にはしない。
予定された変更と既存判断を照合し、普遍的な行数閾値や新しい重い解析基盤は作らない。

## 開発チャット向け指示

- Q1でPLANのStep種別／選択条件と実結果による機械分岐を接続し、通常の局所変更を
  compile/focused/適用lint -> assessment -> AIなし受入れ/commitまで先に通す。
- Q2のStep/Release FIXを再利用する。Q3で最小Scala lintと条件付きtriage／直接詳細レビュー
  の接続を追加する。既存Q1〜Q4の順序を維持し、全signalの実装を主経路の前提にしない。
- triageには実差分または範囲を限定したソース参照、lintを含む既存検証、発火理由、
  既存判断を渡す。詳細レビューには同じ根拠を引き継ぎ、lintを再実行して根拠を作り直さない。
- 機械判定、未確認のSmell観測、reviewで確認したFindingを区別する。triage CLEANを
  review PASSに偽装せず、正式なレビューなしStep受入れを使う。
- FIX後は影響のある検証・signalだけ再評価。検証済みの構造保証、既存レビュー、
  ユーザーが受容したlint抑制を新しい理由なく再審査しない。履歴・修正回数を保持する。
- Journalへ適用ルール・参照結果・分岐理由・実行/省略Actionをruntimeが自動記録する。
  記録のためのAI呼出しや別台帳は追加しない。記録失敗で元Actionを再実行しない。
- Q4で通常ルートのtriage/review呼出しゼロ、lint cleanでもsignalでtriageする経路、
  明示レビューの直接経路、CLEAN/SUSPICIOUS/DECLINEと既存FIX復帰を実証する。
  既存証跡から呼出し数と誤検出・重要な見逃しを確認し、未測定コスト削減を断言しない。

P65-01/03/07/11/13を具体化し、新規P65-17〜20を追加した。既存項目ID・checked状態、
Scala lint/抑制の受入れ、CBDはPhase 10までOFF、収束強化はPhase 9という分担を保持する。
Findingの重複整理・状態管理の全面追加は今回のrouting変更の必須条件にしない。

## 保持範囲

今回の変更はsm-workflow-dev側の仕様・計画・handoffのみ。6.4と12の進行中作業、
既存Goal/Session/Plan、E1受入れ、履歴・修正回数・成功/失敗の検証結果は変更しない。
未コミット作業を保持し、native Start/Submit/Advance、runtime編集、テスト・配備・commitは
実施しない。Phase 6.5もplannedのままで、項目を実施済みにしない。
実際の開発時には適用可能な既存実装・検証を再利用し、変更した分岐と不足接続だけを検証する。

## 追記: RepositorySync / SplitPhase採用

同日後続の[三workflow handoff](2026-10-21-phase-65-three-workflow-optimization-handoff.md)で
Q3とQ4の間にRepositorySyncの正式merge admission/FIX、SplitPhaseの既存回答/context再利用
を追加した。本文のGoalPhase方針と保持範囲は維持する。LLM評価は対象外で、Luna化が
可能なら採用し、不可能でも適切な既存profileで6.5を完了できる。モデル比較はPhase 9へ残す。
