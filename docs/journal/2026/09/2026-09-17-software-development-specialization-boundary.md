# sm-workflow as Software Development Workflow Specialization

Date: 2026-09-17
Status: Design decision

## Position

`sm-workflow` は汎用Skill Workflow基盤ではなく、Software Development向けWorkflow specialization/reference consumerとする。

汎用Skill Workflow SupportはCNCF側に置き、`sm-workflow` はそれを利用してsoftware development固有のworkflow/profile/operation/policyを提供する。

```text
CML/Cozy generic Workflow semantics
  -> CNCF Workflow Runtime
     -> CNCF Generic Skill Workflow Support
        -> sm-workflow Software Development specialization
```

## sm-workflow owns

- development PLAN / IMPLEMENT / REVIEW / RE-REVIEW等のprofile
- software repository/worktree context
- build/test/Executable Specification operations and policy
- local git inspect/stage/commit closing
- development-specific evidence/receipt
- development phase/slice/ticket/commit traceability integration
- development-specific review/closing policy

## sm-workflow does not own

- generic Continuation identity/resume semantics
- generic ContextBundle/Snapshot/Reference
- generic SkillWorkOrder projection mechanism
- generic Parent/Worker handoff mechanism
- generic AI/Human continuation runtime
- generic capability/complexity/risk transport

これらはCML/CNCF generic contractまたはCNCF Generic Skill Workflow Supportから利用する。

## Development feedback loop

`sm-workflow` はSkill Workflowの実戦的な検証場所として重要である。実装中に発見した便利機能は、software development固有かをまず判定する。

- 固有なら `sm-workflow` に保持する。
- domain非依存でSkill利用一般に有効なら CNCF Generic Skill Workflow Supportへ提案/昇格する。
- Skill以外のUI/Human/Serviceにも一般化できるなら CML/CNCF generic Workflow contractへの昇格候補とする。

この境界により、`sm-workflow` は特化による使いやすさを維持しつつ、汎用protocolの実利用フィードバック源として機能する。
