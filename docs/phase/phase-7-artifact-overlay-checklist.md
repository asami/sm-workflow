# Phase 7: Development Artifact Overlay checklist

Date: 2026-10-06
Status: planned; no implementation or validation claimed
Contract: [Development Artifact Overlay](../spec/development-artifact-overlay.md)
Owner: [Phase 7](phase-7.md)

Verification uses the Phase 5 managed-suite runtime and Phase 6 design/FIX
connection. The [Phase 7 suite integration items](managed-test-suites-phase-checklist.md)
cover per-project FULL and selection-aware consumer verification; this checklist
does not introduce a second test-command selector or per-run AI selection.

## Connected implementation

Follow the [Phase 7 execution plan](phase-7.md#execution-plan--2026-10-07).
Batch A connects 01..04 and the publish/select/status parts of 06/07 through
actual native invocation and SBT compilation; batch B completes 05..07 including
generation, verify/detach/prune. Batch C adds workspace synchronization on the
same resource/validation contracts. Batch D covers all acceptance below and
the existing CNCF Phase 101 closure order. Partial operation coverage never
checks off a whole row. Reuse one explicit two-consumer driver fixture; current
checkboxes remain unchanged until actual evidence exists.

- [ ] P7-DAO-01: Define typed owner/publication/selection records and explicit
      producer/consumer membership through CNCF resources; support the declared
      logical repository/staging resources and typed consumer bindings without
      proximity discovery or a parallel skill-owned JSON lifecycle ledger.
      Store shared project/worktree definitions in the standard component
      resources area; resolve machine-local existing-worktree paths through CNCF
      configuration and keep work.d checkouts/artifacts outside Git.
- [ ] P7-DAO-02: Fix SBT development-version format, cross-module identities,
      setting/task API, direct/transitive overrides and completion visibility.
      Use per-build explicit JVM-property input and reusable common SBT
      integration; no shell-wide environment selection or per-selection edits
      to build.sbt/project dependency declarations. Document one-time plugin
      bootstrap and verify overrides under real project setting precedence.
      A supported, enabled sbt-cozy handles the explicit argument without an
      overlay-specific build.sbt declaration; preserve ordinary dependency
      declarations without per-dependency wrappers. No argument keeps ordinary
      behavior; invalid explicit selection fails rather than falling back.
- [ ] P7-DAO-03: Publish only declared development JARs/Ivy metadata and selected
      auxiliary outputs; record real dirty-source origin and build dependencies;
      prevent overwrite and incomplete/multi-module publication visibility.
- [ ] P7-DAO-04: Implement immutable selections and actual resolution reporting;
      reject conflicts, unavailable selections and foreign-overlay dependencies
      without fallback while preserving ordinary resolution/cache behavior.
- [ ] P7-DAO-05: Connect Cozy generation selection, generation records and targeted
      regeneration with SBT/Cozy classpath reload or restart on update/rollback.
- [ ] P7-DAO-06: Add native typed Operations and the publication execution adapter
      with producer, owner, output scope and build selection; retain execution
      permissions, leave shared-cache exclusion to SBT/Ivy/Coursier and add no
      SBT-wide or machine-wide product mutex;
      distinguish overlay success from shared publishLocal. Public product
      contracts do not depend on Codex runner identities or shared-skill receipts.
- [ ] P7-DAO-07: Connect publish/select/status/verify/detach/prune to the normal
      sm-workflow operation path, with protected active/pinned/in-use references
      and dependency closure; no implicit global-cache cleanup or deletion.

These are tracking items, not separate review gates per helper. Implement native
sm-workflow operation/adapter wiring together with the first SBT publication and
resolution path, then extend through Cozy generation and the remaining operations.
Write Executable Specifications alongside each connected batch; validate and
independently review the integrated behavior.

## SimpleModeler driver acceptance

- [ ] P7-DAO-A01: Two real consumer worktrees independently use two different
      SimpleModeler development versions.
- [ ] P7-DAO-A02: Ordinary Cozy resolution/generation remains unaffected.
- [ ] P7-DAO-A03: Generated Scala compilation rejects a new-generator/old-CNCF
      API mismatch and accepts a compatible selected combination.
- [ ] P7-DAO-A04: Failed/partial publication or an unavailable selected artifact
      fails explicitly without resolving to an ordinary version.
- [ ] P7-DAO-A05: Update/rollback/detach refresh actual generated output and
      classpaths, including running SBT/Cozy processes. The same checked-in
      build definition supports ordinary and selected builds without rewrite;
      only the explicit per-build input changes. Use a pre-overlay build.sbt
      without overlay-specific settings, with supported sbt-cozy installed and
      enabled; no-argument and valid-selection cases pass, invalid selection fails.
- [ ] P7-DAO-A06: All six operations leave target shared ~/.ivy2/local artifacts
      untouched; ordinary caches remain available, without wholesale copying.

## Supporting behavior and closure

- [ ] P7-DAO-A07: Direct/transitive conflicts and foreign-overlay dependencies
      reject; exact completed development coordinates can use global cache.
- [ ] P7-DAO-A08: Failed multi-module publication is not selectable; switching
      does not mutate an in-flight build's chosen dependency set.
- [ ] P7-DAO-A09: Prune protects consumer selections, active build inputs,
      retained sets and their dependency closure; deleting a producer worktree
      does not erase published artifacts. Status reports unverified/failed use.
- [ ] P7-DAO-A10: Required integrated tests and independent review pass; usage
      describes the six operations, normal resolution and actual limitations.
- [ ] P7-DAO-A11: Independent SBT operations remain eligible for concurrent
      execution; only real shared-output/selection/prune conflicts coordinate.
      Local development-harness serialization is not a product requirement.
- [ ] P7-DAO-A12: Shared project definitions and local configuration correctly
      bind dedicated and explicitly registered existing worktrees. Resolve
      selected JARs/metadata from logical work.d/artifacts repositories or normal
      cache without proximity/latest discovery. No-selection builds retain
      ordinary dependencies, and status exposes actual selected coordinates/paths.

Preserve ordinary Git and execution evidence. Do not introduce management
hashes, source copies, unchanged-content certificates or internal TTL gates.
No checkbox changes until actual implementation/validation evidence exists.
