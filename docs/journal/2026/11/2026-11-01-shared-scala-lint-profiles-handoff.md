# Shared Scala lint profiles: development handoff

Date: 2026-11-01
Status: planned; no implementation or runtime adoption claimed

## Decision and motivation

Recent handwritten code contains private `_wrapCommand`, unprefixed private
`exact`/`reference`, and version documentation before the package declaration.
These are mechanically detectable convention violations. AI should not repeatedly
review their formatting. The meaning of generic predicate names and the necessity
of a 16,384-character input limit remain separate semantic questions.

The user requested reusable sm-workflow and development-team standards, with
repository `.scalafix.conf` reserved for project-specific differences. Adopt:

    sm-workflow baseline -> selected team standard -> optional project differences
                        -> retained effective configuration -> existing Scala lint

Goldenport/Asami naming belongs to the `goldenport` team standard. Configuration resolution is
owned by sm-workflow; shared Scalafix rules own syntax analysis. Do not assume that
Scalafix merges these layers automatically or copy full standards into repositories.

## Work to deliver

Use [SLP-1 through SLP-4](../../../phase/phase-6.12.md), with the
[target specification](../../../spec/scala-lint-profiles.md). First prove one ordinary
runtime validation in a tiny project with no project config. Then complete the
initial convention rules, project overrides and scoped suppression, and integrate
ordinary finding/FIX/admission with effective configuration evidence.

Rule membership is additive unless a rule is explicitly removed; project rule lists
must not erase the baseline. Later setting leaves override earlier ones. Selected
configuration errors are failures, not clean lint. Include handwritten test sources,
which the current Compile-only `fixCheck` alias does not cover by itself.

## Preservation and adoption

This is Phase 6.12, after 6.11 and before 7, reusing accepted Phase 6.5 Q3 without
reopening that delivery or adding a completion condition to active work. Preserve all ongoing
edits, staged work, Sessions, accepted results, histories and repair counters.
Do not change the effective policy of executing validation. Adopt for subsequently
issued work through normal configuration/operation paths and retain provenance.

Do not run repository-wide cleanup as an adoption prerequisite. Use full syntax
context while distinguishing new/affected violations from unrelated existing debt.
Keep standard suppression with reasons, existing runtime-owned SBT/publication,
and ordinary FIX handling. No additional AI call is needed for profile selection,
lint execution or configuration reporting.

This journal records the handoff for later development; it does not claim that
another chat has received or adopted it. Implementation completion requires actual
SLP acceptance results and delivery evidence, not this planning record.

## Correction: Phase allocation and Goldenport selection (initial direction)

The first revision used an unnumbered independent plan. It is superseded by
Phase 6.12, Subphase 6.12-A, Steps SLP-1–4, and the separate SLP-A01–12 checklist.
All original acceptance items retain their identifiers and OPEN state; relocation
is not implementation. The old plan path remains only as a relocation reference.

Superseded selection mechanism: the final source/history hierarchy below replaces
the history-key-driven coupling in this paragraph. The user clarified that the existing `goldenport` selection should activate their
naming conventions as well as history handling. Reuse resolved
`sm-workflow.source-history.style=goldenport` without a second naming-profile switch.
Current history rule data/driver stay intact; this is planned lint integration,
not a claim that current runtime already performs it. Unset/none does not impose
Goldenport conventions, while generic baseline lint remains available. Project
settings remain explicit differences. Apply the canonical visibility-aware naming
rules, including model-field/schema exceptions, not blanket lowercase conversion.

Preserve current Sessions and accepted validations. Add actual profile and history
behavior coverage to SLP-A11/A12; do not adopt changed rules into executing work.

## Final configuration contract: source style and history override

The user approved a source-wide selector plus an independently overridable history
selector. Phase 6.12 now specifies:

- `sm-workflow.source.style`: absent/none or goldenport; goldenport selects naming
  conventions and defaults history to goldenport.
- `sm-workflow.source.history.style`: absent inherits the source default; explicit
  none disables only history, and explicit goldenport enables history independently.
- `sm-workflow.source.history.rule`: external rule data consumed only by enabled
  goldenport history, using the existing driver and its normalization behavior.

Resolve ordinary per-key CNCF precedence before inheritance. An explicit history
choice always overrides the inherited default. Generic lint remains enabled when
both styles are absent/none. Effective history=none also turns off history-specific
version-comment placement checks; source naming remains independently selected.

Remove the old `sm-workflow.source-history.style` and
`sm-workflow.source-history.rule` keys without aliases, dual reads or automatic
conversion. Explicit old-only/mixed inputs report obsolete keys through ordinary
configuration diagnostics. Update owned producers/consumers and deployment examples
as a coherent delivery. This is a breaking configuration rename, not a rewrite of
historical records: preserve original configuration evidence, issued work, Sessions,
histories, repair counts and successful checks. Qualify new settings/runtime together
before adopting them for subsequent work; no live Session/configuration is edited
by this planning update.

The authoritative checklist now includes SLP-A01–15. A11/A12 cover the six-row
selection matrix, A13 precedence, A14 obsolete-key removal, and A15 preserved-state
adoption. Existing IDs remain OPEN; no implementation or validation success is claimed.
This section supersedes the earlier proposal to let source-history.style select
naming conventions. No extra AI call or separate profile selector is introduced.
