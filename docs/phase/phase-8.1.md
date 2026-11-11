# Phase 8.1: Shared Scala Lint and Goldenport Conventions

Stage Status:
- Current status: OPEN
- Owner: sm-workflow development
- Update rule: Update from actual acceptance evidence; planning is not completion.
- Closure basis: [Phase 8.1 checklist](phase-8.1-checklist.md), SLP-A01–SLP-A33,
  SLP-B01–SLP-B04 and SLP-C01–SLP-C05

Planned: 2026-11-01
Sequence: Phase 7 -> Phase 11.1 -> Phase 8 -> Phase 8.1 -> existing Phase 9 -> Phase 18.
Specification: [shared Scala lint profiles](../spec/scala-lint-profiles.md).
Handoff: [English journal](../journal/2026/11/2026-11-01-shared-scala-lint-profiles-handoff.md).
Producer plans: [cross-project deliveries and dependencies](phase-8.1-dependencies.md).

## Renumbering and priority — 2026-11-11

Formerly Phase 6.12. Preserve the full existing A/B/C scope and SLP identifiers;
all criteria remain OPEN. The Phase order is 7 -> 11.1 -> 8 -> 8.1 -> 9 -> 18; parallel
development is prioritized within Phase 7. Existing Phase 9 keeps its number
and convergence/provider-routing purpose. Producer-local
Phase numbers and historical records are not renumbered by this plan.

## Purpose and preservation

Centralize baseline/team/project lint settings and make `sm-workflow.source.style=goldenport`
select naming conventions and inherited Asami-style history handling, with
`sm-workflow.source.history.style` as an optional history-only override.
Project `.scalafix.conf` contains only local differences and remains optional.
The generic safety/security baseline applies under every selected style, with
explicit supported analysis scope and no claim of complete security assurance.
Deliver strict Scala 3 operation in three stages: lint detection, strictEquality
in Cozy-generated/AI-authored code, then type-safe collection extensions from
goldenport-core and actual consumer adoption. This plan does not claim any stage
implemented or automatically activate stricter settings in current work.
Reuse accepted Phase 6.5 Q3 without reopening its acceptance. Preserve ongoing
work, staged edits, Sessions, history, repair counters and successful results.
This planned Phase adds no gate to active work and does not adopt configuration
into an executing request. The prior unnumbered plan is relocated here with all
SLP identifiers and open checklist states preserved.

## Three-stage delivery and producer ownership

| Stage | Scope | Implementation/integration owner |
| --- | --- | --- |
| 8.1-A | Shared strict lint, including safety/security/equality rules | sm-workflow profile integration and shared Scalafix rules; existing compiler/CAR boundaries retained |
| 8.1-B | Active strictEquality in generated and AI-authored Scala 3 | SimpleModeler templates and Cozy-owned output/integration; development-workstation AI instructions/templates; normal sm-workflow distribution/acceptance |
| 8.1-C | Safe standard-collection extensions and their adoption | goldenport-core API producer; Cozy/AI consumers and sm-workflow integration |

Retain accepted work and complete the stages in order using bounded producer
handoffs. Missing Core support blocks only affected 8.1-C acceptance; do not
wait for unrelated whole producer Phases. These are planning assignments, not
implementation dispatches, new repository edit permissions or new Sessions.

The [dependency plan](phase-8.1-dependencies.md) records local plans in
Core, SimpleModeler, Cozy, SimpleModeling Model, sbt-cozy, CNCF and
development-workstation, with bounded early handoffs and acceptance mapping.
sm-workflow owns the independently reusable Scalafix rules artifact and profile
resolver; Core owns safe collection APIs. CNCF's typed execution and ordinary
build support are qualified first as existing capabilities, with no speculative
new framework/plugin feature. Producer preparation may overlap; integrated A/B/C
acceptance retains the sequence above. All handoffs remain unaccepted until actual
delivery; this coordination update completes no checklist item.

## Subphase 8.1-A: Shared convention execution

Stage Status:
- Current status: OPEN
- Owner: sm-workflow development
- Update rule: Advance only on observed SLP acceptance outcomes.
- Closure basis: [SLP-A01–SLP-A33](phase-8.1-checklist.md); all required items must pass.

Steps SLP-1–SLP-4 below belong to this Subphase. Their closure evidence is mapped
in the checklist; step narrative does not replace that ledger.

## Main route first

1. **SLP-1 — Resolve and execute shared defaults.** Inventory the current lint Action,
   installed resource/configuration owners and source selection. Implement baseline
   plus the selected `goldenport` team profile and retain one effective configuration through
   the existing runtime route. Prove a tiny project without `.scalafix.conf` runs a
   shared private-member rule and reports `_wrapCommand` as a violation. Resolve
   source style and history inheritance from normal CNCF-resolved parameters.
   Include the generic `NoOptionGet` baseline independently of team/history
   selection, the baseline safety/security rules and the adopted finalizer
   prohibition; project rule additions must not erase inherited rules.
2. **SLP-2 — Complete the initial convention rules.** Add local-helper/flatcase and
   version-comment placement checks. Package reusable rules once; use positive and
   negative fixtures for production and test code, including documented exceptions.
   Detect `scala.Option.get` through resolved method/type information in Scalafix,
   distinguishing `Map.get` and unrelated APIs. Qualify the required Scala 3
   analysis; unavailable analysis must fail explicitly rather than report clean.
   Extend that analysis to `Try.get`, Either projection `get` and the selected
   empty-sensitive collection operations; detect executable `Predef.???` without
   flagging unrelated methods, strings or comments. Use the exact scope and
   alternatives in the specification, with scoped intentional exceptions.
   Include the bounded security rules for embedded credentials, unsafe SQL
   construction, disabled TLS verification, weak cryptographic configuration,
   unchecked Java deserialization and predictable security randomness. Qualify
   supported APIs, safe alternatives, redacted diagnostics and honest unresolved
   coverage through the same shared-rule/FIX route; reuse existing security
   ownership without adding a runtime guard or another security review cycle.
   Include discarded failure results, array equality and inferred Any. Extend
   discarded-result checks to Consequence/Program under the Goldenport profile.
   Prioritize contains and examine comparisons in predicates and implicit
   collection membership/de-duplication/grouping/key operations, preserving
   value/reference/domain identity and honest warnings/analysis limits.
   Share the package with CNCF rather than duplicating its implementation.
   Enforce NoStructuralIndentation for Scala 3: explicit braces for structural
   definitions and domain/framework DSL composition; indentation only in terminal
   implementation-expression regions. Use tree context and original tokens, not
   literal AST-child counts or a colon regex. Preserve permitted leaf expressions
   and qualify structural versus ordinary lambdas; formatter/compiler success
   alone does not establish this rule.
3. **SLP-3 — Add project differences and exceptions.** Resolve optional project
   settings, rule additions/removals, leaf precedence and standard suppressions.
   Keep selected profile provenance and effective configuration in existing evidence.
4. **SLP-4 — Connect acceptance and delivery.** Feed findings into ordinary FIX and
   selected re-validation, qualify Compile and Test handwritten-source coverage,
   and adopt profiles for subsequent validations. Retain prior findings/results;
   no repository-wide naming cleanup or successful-test replay is a prerequisite.

Use `sm-workflow.source.style` and `sm-workflow.source.history.style`; the latter
inherits only when absent. Keep external history rule data at
`sm-workflow.source.history.rule`. SLP-1 establishes source/history separation;
SLP-3 covers explicit overrides, old-key rejection and removal from all owned
configuration producers/consumers. SLP-4 qualifies new configuration/runtime adoption
without changing issued work or historical evidence. No old-key aliases, dual-read
fallback or automatic conversion is delivered. See the specification's six-row matrix.

Align shared artifact ownership and project-override settings with existing build
mechanisms. Runtime owns validation and any necessary local publication. This
planning change does not execute SBT, publish, edit live configuration, change a
Session or dispatch work to another chat.


## Subphase 8.1-B: Strict generated and AI-authored Scala 3

Stage Status:
- Current status: OPEN
- Owner: Cozy/actual Scala 3 generator owner and development-workstation AI
  instructions/templates; sm-workflow owns delivery integration.
- Closure basis: [SLP-B01–SLP-B04](phase-8.1-checklist.md), actual generated/AI
  consumer compilation and retained compiler-policy evidence.

1. **SLP-B1 — Make strictEquality standard.** Generate the active
   `import scala.language.strictEquality` in the selected Scala 3 source scope
   and require it in the AI coding instructions/templates. Preserve other
   language backends. Reflect common instruction changes from their existing
   development-workstation owner into product distribution at the normal boundary.
   SimpleModeler's shared class generator already emits this import; inventory
   specialized and Cozy-owned output and qualify the existing path before
   implementing any missing coverage. Its Scala 2 generator host stays separate.
   Apply the same structural-braces/terminal-expression policy to generated and
   AI-authored Scala 3, with generated-output evidence and shared authoring guidance.
2. **SLP-B2 — Define intended comparison contracts.** Use derives/bounded CanEqual
   instances only for intended comparisons. Reject bypasses using universal
   CanEqual, Any widening/casts, equals substitution or disabling the feature.
   Qualify the strongest applicable supported compiler settings and their
   null/unchecked/diagnostic/interop scope; the import alone is not all strictness.
   Actively adopt provided compiler facilities as the normal strict target:
   explicit nulls, safe initialization, discarded/unused-value and unchecked/
   deprecation/feature diagnostics, with warning-as-error enforcement. Resolve
   version-specific flags and record concrete compatibility exceptions; do not
   replace available compiler checks with duplicate lint or silently disable them.
3. **SLP-B3 — Qualify and adopt.** Use small valid and compile-rejection fixtures
   for real generator output and representative AI-authored code, including
   predicates and the still-unprotected library contains case. Adopt qualified
   settings for subsequent work, preserving current Sessions and evidence.

## Subphase 8.1-C: Core type-safe collection extensions

Stage Status:
- Current status: OPEN
- Owner: goldenport-core API producer; Cozy/AI consumer owners and sm-workflow
  integration use the bounded accepted delivery.
- Closure basis: [SLP-C01–SLP-C05](phase-8.1-checklist.md), producer and actual
  consumer evidence; no claim from a type-class signature alone.

1. **SLP-C1 — Establish equality and identity APIs.** Reuse existing Cats Eq and
   Identified/Identifiable where appropriate. Distinct extension names make value
   equality and ID matching explicit; names such as containsEq/containsId/
   findById/distinctById are provisional. Absent IDs do not equate two entities.
2. **SLP-C2 — Prove type and collection semantics.** Fix receiver element/ID
   types against argument-driven widening, including generic Eq parent instances.
   Qualify compile rejection of different ID/element types, coherent Eq/hash/
   Ordering where applicable, ordering and declared duplicate/collision behavior.
3. **SLP-C3 — Adopt available APIs.** Connect representative Cozy-generated and
   AI-authored consumers and lint guidance to the actual accepted Core API.
   Preserve fallback-free missing-delivery diagnostics; do not emit nonexistent
   methods or automatically replace value membership with business-ID matching.

Use the specification's three-stage contract and the existing validation/review/
publication owners. Representative compiler-negative cases replace no behavioral
obligation, and add no second whole-repository test or review cycle. This update
adds producer planning documents only; it does not implement Core/Cozy/common
instruction changes, execute a workflow or publish.

## Completion

Close only with actual results for all required checklist items and recorded dispositions
for any explicitly relocated work. Publish an English completion record describing
configuration selection, rule coverage, preserved history behavior, validation and
consumer adoption. Plan edits alone do not satisfy delivery or close Phase 8.1.
