# Shared Scala lint profiles

Status: approved development direction, not implemented by this document.

## Ownership and configuration

sm-workflow resolves Scala lint configuration and invokes the existing runtime-owned
Scala lint Action through CNCF typed SBT/UoW. It does not ask AI to select rules on
each execution. Scalafix implements analysis; CAR lint retains CAR boundary checks.
The authoritative coding conventions remain in ai-directive `core/RULE.md`.

Resolve these layers in order:

1. Versioned sm-workflow standard: generally applicable baseline lint rules.
2. Selected development-team standard: shared rules and settings, such as Goldenport/Asami
   naming and version-comment conventions.
3. Optional repository-root `.scalafix.conf`: project-specific additions and overrides.

Goldenport/Asami conventions belong to the `goldenport` team profile, not the generic
baseline. Shared profiles and rule artifacts are installed reusable resources,
not files copied into every repository. No new required project file is introduced.

## Source style and history override

The target configuration keys are:

```hocon
sm-workflow.source.style = goldenport
# Optional: omit to inherit the source profile's history default.
sm-workflow.source.history.style = none
# External history rule data, needed only for effective goldenport history:
# sm-workflow.source.history.rule = { ... }
```

`sm-workflow.source.style` selects the overall source convention. Initially its
values are `none` (also the absent default) and `goldenport`. The latter enables
Goldenport naming and defaults history style to `goldenport`. An explicit
`sm-workflow.source.history.style` overrides only history; its initial values are
`none` and `goldenport`. Absent is inheritance, not an implicit explicit `none`.

| Source style | History style input | Naming conventions | Effective history |
| --- | --- | --- | --- |
| absent / none | absent | Generic baseline only | none |
| goldenport | absent | Goldenport | goldenport (inherited) |
| goldenport | none | Goldenport | none (explicit override) |
| goldenport | goldenport | Goldenport | goldenport (explicit override) |
| absent / none | goldenport | Generic baseline only | goldenport (explicit override) |
| absent / none | none | Generic baseline only | none (explicit override) |

Resolve each explicit key using CNCF's existing configuration precedence through
`ExecutionContext.resolvedParameters`, then apply inheritance to an absent history
style. A resolved explicit history choice is not overwritten by a source default,
even when the source style was supplied by a higher-priority configuration layer.
Scalafix project differences apply to lint settings; they do not become a second
source/history selector or control the history update operation.

Effective `goldenport` history consumes external rule data at
`sm-workflow.source.history.rule` and reuses the existing history driver, rollover
and normalization behavior. Missing/invalid required rule data is a configuration
failure, not fallback. Effective `none` performs no history-rule access, history
check/update or history warning; it also disables the history-specific version
comment placement check. Naming lint follows source style independently. Generic
baseline lint stays applicable in every row. Unsupported styles are configuration
errors, not silent defaults.

## Old-key removal and preserved evidence

Remove `sm-workflow.source-history.style` and `sm-workflow.source-history.rule`
from current execution/configuration contracts. Do not retain aliases, dual reads,
fallbacks or automatic conversion during resolution. Update owned configuration
schemas, parsers, examples, templates, fixtures, installation and selected workspace
settings to the new keys as one delivery. When an old key is explicitly supplied,
report an obsolete-key configuration diagnostic with its replacement; old-only and
mixed old/new configurations must not silently disable history or choose a winner.
Use normal configuration validation, not a new machine-wide configuration scanner.

Historical records retain the exact configuration and results under which they
were produced. Historical readability is not an old-key execution path. Do not
rewrite live Sessions or replay successful work for this rename. Before adoption,
qualify the new runtime and resolved settings together; issue subsequent work with
new keys through the supported configuration route. Already issued execution and
its retained results remain unchanged. This document specifies a planned contract;
current `WorkflowSourceHistoryParameters` still implements the old history-only keys.

## Resolution semantics

- Resolve effective rule membership by ordered union and deduplication. A project's
  `rules` list does not replace inherited rules. Explicit removal identifies the
  rule; omission never disables an inherited rule.
- Later layers override individual configuration leaves. Arrays other than rule
  membership replace the earlier value unless that setting defines other semantics.
- Represent explicit rule removal as a documented sm-workflow resolution directive,
  separated from Scalafix settings and removed before rendering its configuration.
  Do not claim that stock Scalafix performs this layered merge automatically.
- Preserve standard Scalafix diagnostic settings and scoped suppressions. A rule's
  removal and a diagnostic suppression are distinct, visible effective settings.
- Missing project configuration uses shared defaults. Malformed selected configuration
  or unavailable selected rule implementation is a configuration failure, not CLEAN
  or an implicit empty profile.
- Resolve relative project references against the project and shared references
  against their owning resource. Preserve ordinary supported configuration behavior;
  document any actual unsupported case rather than silently dropping it.

Resolve and retain effective configuration for the issued validation. Configuration
changes apply to subsequently issued validation; do not mutate an executing request
or reinterpret previous successful results. Use existing evidence/result retention
for profile identities/versions, effective configuration reference and relevant
overrides/removals. No hashes, approval registry or additional AI call is required.

## Initial Goldenport convention rules

Rule IDs below are proposed stable names to finalize in the shared rule package.

| Rule | Required behavior |
| --- | --- |
| `GoldenportPrivateMemberNaming` | Private `def`, `val`, `var`, `lazy val` use `_snake_case`; distinguish type/object declarations and contractual overrides. |
| `GoldenportLocalHelperNaming` | Method-local helper functions use `_snake_case_`, not private-member naming. |
| `GoldenportLocalNaming` | Private-method parameters and ordinary local variables follow the flatcase rule; internal model fields follow the same rule except serialized/public schema or required API fields. |
| `GoldenportVisibilityNaming` | Protected members use snake_case; public APIs/parameters and required overrides preserve their prescribed names; type declarations use UpperCamelCase. |
| `GoldenportVersionCommentPlacement` | Enabled by effective goldenport history, including history-only selection. Version metadata belongs to the relevant top-level definition's documentation after package/imports, not before package. Preserve unrelated license/file comments. |

Use syntax trees and token/comment positions, not line matching alone. Exercise
Scala 3 syntax, qualified visibility, overrides, escaped names, nested definitions,
multiple top-level definitions and companion objects against the actual convention.
Where syntax cannot establish a contractual exception, do not invent semantic proof;
use an explicit scoped exception or defer that subcase for semantic support.

Check handwritten product and test code; exclude generated sources from this Action
and correct their generator separately when relevant. Boolean-name meaning and the
justification for a numeric input limit are outside these syntax checks.

## Admission and intentional exceptions

Keep lint detection separate from rewriting. Findings use the existing FIX and
re-validation path; no extra mandatory AI review is introduced. Use standard
Scalafix `@SuppressWarnings` / `scalafix:ok` and a reason for intentional exceptions
under existing decision policy, without a new annotation or exception registry.

Analyze full syntax context of changed files and attribute blocking findings to
new/affected scope. Report unrelated pre-existing violations separately; do not
require repository-wide cleanup to adopt profiles. Release uses its cumulative
delta and reuses applicable results. Whole-file analysis alone must not be reported
as precise changed-scope attribution.

## Delivery

Follow the [development plan and acceptance checklist](../phase/phase-6.12.md).
This extension preserves Phase 6.5 acceptance, ongoing work, Sessions, history,
repair counters and retained validation results.

History updates remain owned by the existing history operation; lint only detects
applicable convention violations. Reuse retained history-check evidence where
appropriate rather than adding a duplicate history-normalization pass. Profiles
control acceptance, not automatic identifier rewriting; original history dates
and records remain intact. See the [Phase 6.12 ledger](../phase/phase-6.12-checklist.md).
