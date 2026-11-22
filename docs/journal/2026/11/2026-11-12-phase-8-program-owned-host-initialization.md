# Phase 8: program-owned host initialization and Evidence setup

Date: 2026-11-12 (Asia/Tokyo)
Status: adopted planning direction; implementation and qualification pending
Owner: sm-workflow ordinary host initialization, integrated by Phase 8

## Observed operational gap

Read-only inspection of the parent Phase 7 startup record found an actual
`prepareGoalStart` setup-required response: derived-import retention needed a
CommonEvidence policy. The parent AI then read implementation/test fixtures,
extracted storage declarations from the selected CAR, and assembled the host's
Evidence collection/workspace/configuration before retrying preparation. The
later parent report says a Phase-specific Evidence location was configured.
This records that observed setup method, not an independent audit of every
created binding, an assertion that isolation itself is wrong, or product PASS.

The user selected this gap for the development plan. Routine startup should
resolve typed host defaults and normal configuration, provision/reuse the
needed admitted storage through program-owned initialization, and return a
concrete result. Parent/child AI should not rediscover internal storage schemas,
extract assembly settings or write configuration plumbing for each Phase.

## Allocation and desired behavior

[Phase 8](../../../phase/phase-8.md#program-owned-host-initialization-and-evidence-setup--2026-11-12)
owns operational integration, reusing the existing
[Phase 15 early initializer](../../../phase/phase-15.md) and its authoritative
[initialization contract](../../../spec/project-initialization.md). Keep one
initialization implementation and existing CommonEvidence/CNCF ownership.
Normal home/project/Component overrides, genuine permissions and explicit
isolation choices remain applicable; Phase-specific installations are not the
default. Keep the runtime host distinct from the development target repository.

Initialization readiness, passive preparation, trusted provisioning and Start
retain distinct meanings. Existing settings, stored Evidence, allocations,
Sessions, accepted results, UNKNOWN/failure history and counters are preserved.
There is no automatic legacy-full fallback, fabricated consent/grant, private
store access, new executor or skill-owned configuration generator.

## Proportionate acceptance and current work

Use one small installed startup scenario to demonstrate normal retention/detail
retrieval, repeatable setup with existing-installation reuse, and the relevant
override or missing-permission outcome. Reuse applicable owner evidence; do not
create another broad validation campaign. Existing Phase 8/15 conditions remain
pending at their actual scope. This allocation does not require all of Phase 15
or Docker Phase 19 to complete first.

This planning update changes no Phase 7 runtime configuration, Session or
storage. It sends no message to the parent/producer, starts no new workflow,
implementation or validation, and performs no commit/publication/push. Historical
startup success is not relabeled as implementation of this planned automation.

## Related submission-format gap — 2026-11-12

The user also selected deterministic result submission for Phase 8. Read-only
inspection of Phase 7 P7-A found a saved `{schemaVersion, payload}` worker return
whose semantic `payload` already contained `summary`. The initial submission
placed that entire storage envelope into the public result input and was refused
for missing top-level `summary`. The subsequent public-payload submission was
accepted. The public required-field reader was therefore behaving as declared;
the observed gap was conversion at the caller/dispatch boundary.

[Phase 8](../../../phase/phase-8.md#program-owned-semantic-result-submission--2026-11-12)
now requires the existing adapter and authoritative typed constructors/codecs to
produce the actual issued public result deterministically. Parent AI should not
inspect generated value source or hand-reconstruct JSON on each result return.
Keep complete semantic fields, source/result references, genuine execution
facts and the original issued context; do not make `summary` optional, invent
missing content, infer authority or replay accepted work. Unsupported/ambiguous
formats and crossed contexts fail at coherent construction before Submit.

One small connected format-to-public-reader example covers supported wrapped,
direct and needed retained-detail forms, plus malformed/crossed refusal. Reuse
existing tests and the proportionate-validation policy, without a separate
publication/recovery campaign. Implementation and acceptance remain pending.
This decision changes no Phase 7 submission, Session, result or repair count;
it performs no implementation, runtime call, delegation, test or publication.

## Session-owned operational state and skill boundary — 2026-11-12

The user selected a related RepositorySync case for Phase 8. The supplied parent
report says the 26-file purpose grouping was accepted and a native candidate was
retained, but validation stopped for a missing fixed workspace slot. The earlier
directory-selection error and this later slot wait are distinct prerequisites;
neither establishes an executed test failure or completed synchronization.

Procedural skill additions described `dirty-purposes` slots, checks and workspace
bindings, but did not create the missing runtime capability. The user clarified
the desired boundary: keep Session-bound information inside sm-workflow as far
as possible instead of requiring parent/child AI to inspect and assemble it.

[Phase 8](../../../phase/phase-8.md#session-owned-operational-state-and-thin-skill-inputs--2026-11-12)
now includes runtime-owned candidate workspace/slot allocation, validation
connections, progress, accepted results, history and repair accounting. Reuse
normal project validation, host policy, admitted resources and the existing
initializer/continuation owners. External configuration still carries actual
targets, business intent and genuine resource/authority choices; this does not
silently grant permissions or pretend missing resources exist.

The caller adapter retains/forwards opaque typed continuation context. Semantic
skills receive only needed targets, constraints, input/evidence references and
the return contract. Internal slot/state setup recipes should be removed from
the skill's normal responsibilities as the runtime route is implemented.
Scoped public diagnostics remain available when needed; reduced exposure is
not loss of evidence, hidden failure or removal of a necessary semantic fact.

Use one small connected installed example to show ordinary resolution, minimal
skill input and same-Session continuation after a real setup prerequisite is
supplied. Preserve accepted grouping, prepared candidates, checks, UNKNOWN/
failure records, histories and counters; do not restart, resubmit accepted work,
edit private stores or add a parallel setup engine. Keep genuine permission/
resource shortages explicit and reuse applicable tests proportionately.

This records planned Phase 8 work, not implementation or current-host readiness.
It changes no live Session, candidate, runtime configuration or synchronization
and sends no maintenance request to another chat.
