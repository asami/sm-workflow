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
