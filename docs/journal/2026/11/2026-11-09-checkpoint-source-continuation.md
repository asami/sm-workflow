# Checkpoint source continuation maintenance

The current repair connects explicit canonical dependency-source selection to
an unstarted, authenticated common-development inspection. An ordinary primary
checkpoint remains a nonacceptance commit; accepted work, the original base,
repair accounting and historical failures remain unchanged. Source selection,
artifact acceptance and publication keep their separate authority.

## Deferred hygiene

- ID: HYG-P611-CHECKPOINT-LEGACY-NAMING
- Status: OPEN
- Discovered: 2026-11-09, independent focused source review of checkpoint-source
  continuation, after its selected behavioral validation.
- Owner: sm-workflow
- Location: `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseOrderedProgression.scala`,
  existing private helpers including `phaseWide`, `checks`, `semantic`,
  `validSnapshot` and `validCommit`.
- Evidence: these helpers already exist at base `9649985`; this repair introduces
  no new private-helper naming violation.
- Category and risk: naming maintenance, low priority; no demonstrated
  behavioral impact on this repair.
- Boundary: a separate mechanical naming task must preserve named arguments,
  public and serialized names, controller behavior and native-history codecs.
  Broad renaming is outside the source-selection continuation repair.
- Follow-up: retain this item until a separately reviewed naming change records
  its resolution. This record grants no cleanup or additional runtime effect.

Existing controller and native-traversal size debt remains recorded in
[the common development reconstruction journal](2026-11-05-common-development-reconstruction.md).
It is not a reason to decompose the state machine during this repair.

## Additional naming locations under the same hygiene item

The option-A review also observed inherited private helper names in
`GoalPhaseNativeValidation.scala` (`covers`, `id`),
`GoalPhaseNativeClosure.scala` (`recordsBaseC`, `id`, `covers`),
`ComponentPackagingDescriptorSpec.scala` (`readJson`, `array`, `text`), and
`GoalPhaseOrderedProgressionSpec.scala` (existing private fixtures/helpers).
Reconcile review candidate `HYG-P611-A-PRIVATE-NAMING-01` into the existing
`HYG-P611-CHECKPOINT-LEGACY-NAMING` item above rather than creating a duplicate.
Status remains OPEN, with the same naming-only boundary and low maintenance
risk. The current repair changes only newly introduced nonconforming local
names; it does not authorize this inherited cleanup.
