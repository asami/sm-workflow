# Retained authority specification pending Cozy recovery

Date: 2026-09-30
Status: source added; NOT compiled or executed

Historical status above: resolved after user-reported Cozy repair and resume.
Cozy compile/export 358 passed; unchanged pending spec compiled and all five
authority configurations passed in focused-359 (239/239, 26 suites). See the
retained-authority-resumed journal in this directory for final evidence. This
does not close the Phase or remaining production requirements.

Previous goal turn progressed with focused-355 (238/238). Continued active
Phase 2 / admitted CNCF Phase 88 under existing simplified parent recovery.

Added one Given/When/Then spec with five authority misuse configurations to
ManagedEventObservationIsolationSpec: foreign host with identical actual provider,
service, policy, thread AND lease; wrong thread with joined terminal thread;
actual revoked lease; explicitly released lease; escaped preparation attempted
both on the released original host and a fresh admitted host. Expectations:
zero provider writes/commits/readbacks and fresh full-field inspection showing
open Workflow pair and unchanged cursor/retained facts. These are expectations,
NOT verified outcomes. Production sources unchanged.

Invocation 356 stopped before CNCF test compilation because the configured Cozy
development classpath was stale. No suites/tests ran. SBT/wrapper 1/1 and
lock=released; summary/log read. Attempted the same ordinary derived-output
recovery used in 343: compile then cozyExportRuntimeClasspath in Cozy, serialized
through the shared wrapper. Invocation 357 failed Cozy compile with 13 errors;
classpath export was not reached. Both processes terminal, locks released.
No concurrent SBT, isolated cache, generator bypass, source edit or publish.

Read-only inspection confirms SourceDeclaration(id) and AssetDeclaration(id,
mediaType) no longer expose sha256, but CrossMediaProjection/Receipt still read
value.sha256. VisualPage Asset references likewise fail in MediaPresentation,
SummarySlidesPdf and VideoStoryboardReview. Cozy has extensive unrelated dirty
source/doc changes. Their semantic repair is outside the admitted Phase88
mutation scope: preserved all changes, no compatibility-field guess or bypass.

356 log: /tmp/skill.cncf.d/cncf-sbt-dd059b42aa97264c9293d64ac0e42e2c870c6c1536e4765bbe0a823729914685-d3f0d303530bf0d74f6acc8ebac67ce1/61548-20260930T122739Z.log
357 log: /tmp/skill.cncf.d/cncf-sbt-ef91c276535fef64c267a0774e9308c17ef7cfcdc50b4211dbc5c0cb50c3070f-ffff7d7a4643f1c8d2d876b715e6d2f0/61973-20260930T122826Z.log
Adjacent .summary.json files read; 357 argv: --batch compile cozyExportRuntimeClasspath.
Current UNVALIDATED isolation spec SHA-256:
7834c2f2235dd75d0531e1ad91dcf2da0ebced7406b561df4b1d8dda3ff71fea.
Focused-355's 238/238 describes the previous source hash, not this added spec.

Next required: Cozy owner resolves the sha256 contract/caller mismatch and
successfully compiles/exports the project-owned runtime classpath, or user
explicitly expands this task's mutation scope to include that repair. Then run
the unchanged 26 focused suites on current CNCF sources with a fresh invocation.
Do not claim authority closure or accumulate unverified production changes.
Retained absence/key-free cases, transient-only completion, deterministic common
routing, native cleanup/crash, application/UoW and CLI/MCP/restart/management
remain open. S12/P2-U15/P2-U16/P2-U05 unchecked. No commit/acceptance/goal closure.
First turn observing this Cozy source blocker; Phase goal remains active.

## Second-turn blocker revalidation

Read current authoritative Cozy sources again before considering another build.
VisualPage Asset sha256 callers from the previous failed compile are no longer
present in MediaPresentation, SummarySlidesPdf or VideoStoryboardReview. However,
Explanation SourceDeclaration(id) / AssetDeclaration(id, mediaType) still have no
sha256 member, and four live references remain in CrossMediaProjection.scala
lines 236/242 and CrossMediaReceipt.scala lines 322/328. Read their enclosing
serialization helpers: both still put the nonexistent member into JSON.
Current explanation design describes resource byte comparison and ordinary
declared IDs separately. Semantic projection/receipt identity repair must preserve
the Cozy owner's chosen contract, not restore a guessed compatibility field.

No further SBT invoked: known missing-member references still prevent compile.
No live owned execution to wait on; both prior attempts were terminal. No source
change or new validation evidence for the pending authority spec. CNCF
git diff --check passes. The same external contract/caller blocker persists for
the second consecutive goal turn. Await Cozy repair or explicit mutation-scope
expansion; do not mark blocked until the required three-turn audit threshold.

## Third-turn blocked audit

Rechecked current sources: the same four missing-member calls remain at
CrossMediaProjection.scala 236/242 and CrossMediaReceipt.scala 322/328, while
the referenced SourceDeclaration and AssetDeclaration still omit sha256.
The dependency contract/caller mismatch has persisted for three consecutive
goal turns. Ordinary compile/export recovery was already attempted and failed;
no live owned process remains to wait on. No authorized Cozy semantic repair,
alternate admitted generator, or safe bypass of the required native validation
is available. Adding more unvalidated implementation cannot close the required
boundary. Goal is blocked pending Cozy owner's repair or explicit scope expansion.
The full Phase objective and pending work remain unchanged; not complete.
