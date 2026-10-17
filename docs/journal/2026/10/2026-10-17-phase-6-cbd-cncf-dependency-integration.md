# Phase 6 CBD/CNCF dependency integration

Date: 2026-10-17
Status: bounded dependency integration accepted by the original independent S6-01 Step review; Step commits and Phase review/release pending

The human-expanded existing dependency integration is implemented under
PHASE-6 / S6-01 / S6-01D35 in the admitted main checkouts. CNCF contains only the
selected existing static Capability metadata/discovery and raw Component Usage
package transport, their executable specifications and fixture, and matching
ComponentFactory facades. The reference implementation's historical protected
Step acceptance remains attributed to that reference; this port does not accept
CNCF78 or CBD11/14 as whole Phases.

The public fresh-UnitOfWork helper delegates to the existing ExecutionContext
runtime rebind. The authored specification covers a real provider step on the
rebound UoW, preserving runtime capabilities, selected profile/control, security,
session and framework settings, and the unsupported-context failure. It adds
no scheduling, lock, retry or cancellation policy.

CBD consumes the current three-field carrier declaration and validates strict
UTF-8/schema plus the exact expected Component and logical release. Development
runtime evidence requires the supported manifest shape and one declared carrier
path; it does not compare carrier digests or bytes. Value-only evidence URIs use
separately escaped Component/release path components. Detail and review profiles
no longer carry carrier-derived digests, and development observations do not
substitute a carrier for an artifact checksum. Existing resource metadata and
the six attributable metadata review diagnostics remain. The unrelated legacy
provider-bundle protocol is outside this migration.

The normal `CbdSupport.CbdReviewAdmin.reviewManagedEffects` QUERY remains wired.
Normal generation and production/Test compilation succeeded. The selected CNCF
Capability/UoW/effect-policy/Job and CBD consumer/provider specifications passed
270 tests across 25 suites, with no failed, aborted, canceled, ignored or pending
cases. Normal `cozyPrepareRuntime` generated the complete classpath, descriptor
and development manifest together, without restoring a retired SHA field.

Discovery-only and explicit Main-factory launches returned operation 404. The
normal development activation, `CncfMain --component-dev-dir`, reached the
current QUERY: six actual commands returned exit 0 and the expected typed
classification; missing SBT operation returned a structured input 400. Inputs
included actual historical/current sm-workflow source observations plus an
explicit normative unknown-command case. Observed Git/SBT/file effects were
never executed by this diagnostic.

The private harness assumed camelCase output and exited 1 despite those six
successful commands. Parent intake checked the original responses against the
generated snake_case wire contract; it retained the failed harness result and
did not replay effects or alter product output. An earlier diagnostic's lost
original verify tool record remains an incomplete retention record, not a
successful validation receipt. Execution evidence remains in the active private
Phase workflow storage. The original protected independent Step review accepted
the bounded integration together with the complete S6-01 accumulator.

P6-SBX05 is accepted at Step scope and the whole Phase remains in progress.
The original Goal/start/binding/bases, epoch1, repair79/streak0 and review history
persist; all 24 original independent repair obligations are resolved. No successor Phase starts;
PHASE-6.3 remains the sole aggregate full-suite owner. The existing
[Phase 6 scope](../../../phase/phase-6.md) and
[checklist](../../../phase/managed-test-suites-phase-checklist.md) retain the
normal validation, actual consumer use and independent acceptance requirements.
