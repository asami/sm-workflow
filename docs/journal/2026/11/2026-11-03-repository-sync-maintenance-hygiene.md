# RepositorySync maintenance hygiene

- ID: HYG-RS-EXISTING-HELPER-NAMING
- Status: OPEN
- Discovered: 2026-11-03, independent maintenance review
- Owner: sm-workflow
- Locations: `RepositorySyncFileEffect.scala`, `RepositorySyncNativeConflictInput.scala`,
  `RepositorySyncNativeProgression.scala`, `Phase69DirtyPurposeSpec.scala` in the
  admission main/test packages.
- Evidence: pre-existing private helpers and members use camelCase rather than
  the current local helper naming convention. New maintenance helpers follow
  the current convention.
- Category: naming maintenance; low priority. No demonstrated behavioral risk.
- Boundary: broad renaming is independent of the text-admission and
  purpose-to-conflict continuation repair and is deliberately deferred.
- Follow-up: a separate mechanical helper-naming cleanup with affected call
  sites. It grants no changes to public, persisted or serialized names.

## HYG-AUTO-CML-WIRE-01 — Primitive preparation wire fields

- Status: OPEN
- Owner: sm-workflow
- Discovered: 2026-11-03, automatic necessary-file inventory maintenance review.
- Location: `src/main/cozy/sm-workflow.cml`, the repair-scope preparation
  `sessionId`, `paths`, and `rationale` wire fields.
- Category: internal wire metadata maintenance; low priority. Primitive fields
  trigger the existing string-attribute convention warning. Actual Session,
  literal path, rationale and candidate/File qualification remain enforced by
  admission; no unsafe behavior has been demonstrated.
- Boundary: no value-type or schema expansion belongs to this repair.
- Follow-up: include these fields in a separately scoped wire-metadata cleanup.

## HYG-RL04-01 — Existing launcher fixture helper names

- Status: OPEN
- Owner: sm-workflow
- Discovered: 2026-11-03, independent required-lint continuation review.
- Location: `ThreeWorkflowLauncherAssemblySpec.scala`, existing private
  `limitKey`, `component`, and `creation` fixtures.
- Evidence: these names already exist in the repair base; the normal operation
  registration change introduces no new names of this form.
- Category: naming maintenance; low priority, no demonstrated behavioral risk.
- Boundary: the required-lint continuation repair does not broaden into
  fixture renaming. Review finding is retained without weakening acceptance.
- Follow-up: include these private fixture names in the existing helper-naming
  cleanup; preserve public and serialized identifiers.
