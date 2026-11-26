# Phase 25.5: Hierarchical Instruction Generation and Caching

Status: planned; execution not started
Planned: 2026-11-25 (Asia/Tokyo)
Scheduled: after [Phase 25.4](phase-25.4.md), before [Phase 23](phase-23.md)
Depends on: Phase 25.4 strategic judgement and typed Task Specification
Checklist: [Phase 25.5 acceptance](phase-25.5-checklist.md)

## Goal

Amortize advanced strategic inference across repeated tasks by generating
and caching scoped instructions from versioned templates. Terra-class models
remain the main bounded execution capability. Reuse is context-aware, not
unconditional replay of a previous task prompt.

## Delivery

### P255-01 — Templates and scoped instantiation

Provide typed templates for review, repair, implementation, test, judgement
and Take Over. Generate Project Instructions from template and project
architecture, policies and quality constraints; generate Phase Instructions
from project instruction and current Phase goals, plan and risks. Bind
current candidate, source, diff and diagnostics at Task execution time.
Prefer deterministic binding; use bounded AI adaptation where needed.
Keep mandatory policy and validation authoritative outside generated text.

### P255-02 — Cache lifecycle

Key by scope identity, template version, relevant context dependencies and
parent instruction identity/version. Retain provenance, generation route,
model, applicable constraints, validation status and usage evidence.
Invalidate affected descendants on relevant template, architecture, policy,
Phase plan or strategy changes. Ordinary source changes invalidate task
bindings unless they alter a recorded higher-scope dependency. Validate
applicability before reuse; never reuse across unauthorized projects or
incompatible workspaces. Handle concurrent generation, atomic publication,
restart and stale entries. Prefer lazy generation or planned Phase-start
generation; no broad knowledge repository or embedding search required.

### P255-03 — Template-specific model suitability

Define minimum and preferred capabilities, model suitability (including
Astra, Sol and Terra where available), scope, expected reuse, quality impact,
generation budget and fallback per template. Treat initial suitability as
hypotheses and refine with comparable evidence. Architecture/security
templates may favor Astra; ordinary review/test templates may favor Sol;
routine templates may be adequately served by Terra or programmatic rules.
No fixed model assignment. Keep exact model identity, execution route and
entitlement separate.

### P255-04 — Strategic investment and allowance-aware generation

Use Phase 25.3 verified daily allowance admission and Phase 25.4 free-first
policy. Where Astra and Sol both satisfy quality requirements, prefer Astra
when free and economically suitable, and lower-cost Sol when paid and
explicitly authorized. Account for shared quota, remaining capacity,
reset/expiry, forecast demand and opportunity cost; do not assume separate
Astra/Sol balances. Consider observed Codex weekly allowance headroom and
reset time when an eligible route exists; unknown balances are not free.
Reuse valid instructions before regeneration. Prefer investment in
high-reuse Project and Phase Instructions over individual tasks when justified.
Allow bounded pre-generation for planned work using otherwise-expiring
surplus only when likely reuse and context stability justify it.
Phase 27 owns generalized cross-route scheduling and paid optimization;
Phase 25.5 preserves free-only defaults and programmatic fallback, with
explicit budget authorization required for any paid generation.

### P255-05 — Qualification and economics

Verify template binding, scope isolation, invalidation, concurrent cache
publication, resume, free exhaustion, unknown weekly balance and fallback.
Compare cached and uncached design with equivalent validation; measure
generation, adaptation, repeated input, lookup, validation, retries,
quality, latency and cost per accepted task. Record reuse count and
amortized generation cost; distinguish estimates from observations.
No claim that reuse or Astra always improves quality or cost.
Record scoped qualification and independent review before closure.

### P255-06 — Diagnostic-driven repair strategy templates

Make recurring compiler and lint diagnostics an initial practical target
for hierarchical instruction reuse. Match structured compiler diagnostic
codes and lint rule IDs where available, with tool/language version,
normalized message patterns and relevant semantic evidence (e.g. expected
and actual types). Use deterministic matching first; distinguish ambiguous,
unknown and multiple interacting diagnostics rather than guessing from text.
Seed templates for type mismatch, unresolved names, missing given/implicit,
ambiguous overload, non-exhaustive match, strict equality, unused imports,
deprecated APIs, Scala/CAR lint and applicable smell findings.

Each template specifies applicability, required context, investigation order,
repair alternatives and priorities, prohibited shortcuts, change scope,
validation and escalation criteria. For example, type mismatch repair
preserves type safety and does not introduce unchecked casts merely to
silence the compiler. Specialize by Project and Phase through strategic
inference and cache using P255-02; bind exact current diagnostics, candidate
and relevant source at Task time. Reuse existing Phase 28 deterministic
fixes first; Terra-class execution is the main AI repair candidate after
those fixes. Preserve existing scope, repair limits, rollback, validation,
Admission and Take Over. Unknown or conflicting patterns use existing
assessment/fallback rather than an unsafe template.

Record pattern ID, template/version, scope, execution model and observed
first-attempt success, retained repair, retries, tokens, latency and Take Over
where evidence permits, reusing Phase 25.2 metrics. Compare against ordinary
repair with equivalent validation; use evidence to target template refinement
with Sol/Astra under existing allowance/budget policy. Promote sufficiently
stable strategies to deterministic repair only after review and verification
at the owning boundary. Qualify representative Scala compiler and lint
fixtures, including version variation, false matches, multiple diagnostics,
cache reuse/invalidation and successful revalidation. Do not claim measured
savings before qualification.

### P255-06 implementation design — initial repair strategy catalog

The following is a proposed catalog, not a claim of implemented rules or
measured savings. Prefer actual FixCheck/compiler/lint records over synthetic
fixtures. Phase 28.1's formal local release records 27/27 focused tests and
557 affected diagnostics in an unresolved FixCheck; do not equate that count
with independently repairable errors. Obtain and classify the original logs.

Each row defines applicability and required evidence, deterministic repair
when qualified, bounded Terra instruction, prohibitions, verification and
escalation. Diagnostic examples are illustrative unless linked to observed
evidence. All rows inherit the common lifecycle below.

| ID / diagnostic | Applicability and context | Deterministic route / Terra instruction | Prohibitions | Specific verification / escalation |
| --- | --- | --- | --- | --- |
| CE-01 type mismatch (E007; Option[A] vs A) | Resolved actual/expected types, expression, enclosing contract, conversions and tests | Qualified typed conversion only; Terra preserves signatures and composes existing effects or handles absence explicitly | Casts, unsafe get, null, widening | Compile and absence/error tests; Sol for contract changes, Astra candidate for domain redesign |
| CE-02 unresolved name (E006) | Symbol index, imports, package and dependencies | Allowlisted unique Metals import quickfix; Terra distinguishes missing import, typo and rename | Invented API, dummy declarations, unauthorized dependency | Verify resolved symbol; Sol for cross-file rename/dependency issues |
| CE-03 missing given/implicit (E172) | Required typeclass, companion, imports, existing instances and semantics | Unique qualified import; Terra selects existing semantically appropriate instance | Universal Eq/CanEqual, circular or fabricated instances | Compile and instance semantics; Sol for placement across modules, Astra candidate for equality policy |
| CE-04 ambiguous overload | Resolved candidates, receiver, arguments and intended signature | Qualified unambiguous selection; Terra uses safe annotation or named argument | Cast-based selection, arbitrary arguments | Verify selected symbol and behavior; Sol if intent spans files, Astra candidate for API design |
| CE-05 non-exhaustive match (E029) | Sealed hierarchy/enum, missing cases, return type and transition contract | Add only specified behavior; Terra explicitly handles missing cases | Catch-all hiding missing states, placeholders, arbitrary throws | Missing-case tests and exhaustivity; Sol for unspecified transitions, human decision if contract absent |
| CE-06 missing member (E008) | Receiver type, extension symbols, imports and actual artifact | Qualified extension import; Terra distinguishes API/version/receiver mismatch | Invented methods, unauthorized artifact update | Compile against actual classpath; Sol for API implementation or cross-module changes |
| EQ-01 strict equality | Both operand types, value/identity/reference/content intent, Eq/CanEqual and project policy | Phase 28.2 qualified rule first; Terra preserves intended comparison using approved API | Universal equality, casts, stringification, disabling strict equality | Equal/unequal and negative type fixtures; Sol for cross-file semantics, Astra candidate for domain equality policy |
| EQ-02 membership/index lookup | Resolved Vector receiver, method symbol, argument type, Eq semantics and Core API | Phase 28.2 qualified containsEq/indexOfEq migration; Terra only for residual cases with established intent | Text-only replacement, widening, unapproved ID comparison | Empty, hit/miss, duplicate first index, wrong-type rejection; decline unsupported overloads; Sol for missing API |
| EQ-03 AnalysisUnavailable | Current candidate, build target, classpath, semantic analysis freshness | Refresh qualified analysis; Terra diagnoses missing evidence, then reclassify | Treating unavailable analysis as PASS or proven bad comparison | Verify analysis availability; Sol for build/analysis infrastructure, Astra candidate for architecture |
| LN-01 unused import | Current semantic evidence and import scope | Qualified Scalafix RemoveUnused or allowed quickfix; Terra only for unresolved implicit/extension effects | Speculative bulk deletion | Compile, lint and symbol resolution; Sol for cross-file effects |
| LN-02 unused declaration | Visibility, references, initializer effects, reflection/registration | Qualified side-effect-free local removal; Terra distinguishes unused binding from required effect | Public API deletion, dropping effects | Compile, API and behavior tests; Sol for framework registration or public contracts |
| LN-03 deprecated API | Resolved old/new API, version, migration contract | Qualified versioned Scalafix rule; Terra uses approved replacement | Suppression, unauthorized upgrades | Compile, deprecation check and behavior equivalence; Sol for cross-module migration |
| LN-04 formatting/hygiene | Active formatter configuration, admitted files | Scalafmt or qualified source cleanup; no AI normally | Unrelated reformatting or policy relaxation | Format check, compile and diff scope; reclassify parse/tool failures |
| LN-05 Scala/CAR lint and smell | Actual rule ID, evidence, architecture, Usage Contract and quality policy | Qualified dedicated rule; Terra repairs within existing architecture | Suppression, weakening policy, unnecessary redesign | Applicable lint, focused tests and contract checks; Sol for component boundaries, Astra candidate for security/architecture |

LN-05 subtemplates are candidates (not assertions of existing rule IDs):
unsafe cast/extraction, oversized source, component boundary, unauthorized
I/O bypass, public API contract and evidence-backed AI smell. Prioritize
EQ-01–03 and LN-01/04, then CE-01–06 and LN-02/03, then project-specific
LN-05; revise priorities from observed frequency and risk.

#### Typed strategy contract and execution

Represent each template with ID/version, diagnostic matcher (tool/version,
code/rule, normalized message, semantic predicates), applicability and
decline reasons, required/optional context, investigation order, qualified
deterministic rule references, bounded Terra instruction, prohibited edits,
allowed scope, validation, escalation, dependency fingerprints and
qualification evidence. Distinguish Matched, Ambiguous, Unknown,
InsufficientEvidence and InteractingDiagnostics. Cluster root causes and
recompile before treating cascading diagnostics as independent tasks.

Common Terra instruction: repair only the admitted scope using current
diagnostic and semantic evidence; preserve public contracts and behavior;
follow project-approved APIs and policy; do not suppress diagnostics or
invent APIs; return NeedsEscalation with missing evidence if safe repair
cannot be established. Mandatory policy is enforced outside generated text.
Use Phase 28/28.1/28.2 deterministic routes first, with existing allowlists
(Metals quickfix only in normal repair), revision checks, rollback and
Admission. Validate originating diagnostic, compile, applicable lint,
focused tests and patch scope. No AI call after successful deterministic
repair. Preserve repair limits and Take Over; do not repeat unchanged
failed inputs. Refresh missing context, invalidate defective instructions,
or select Sol for multi-file work and Sol/Astra for strategic design;
request human judgement when specification or authorization is missing.

#### Hierarchical specialization and cache

Global templates contain reusable repair knowledge. Project Instructions
specialize architecture, library APIs, equality/identity and quality
policies. Phase Instructions specialize goals, allowed changes and
verification. Task bindings use current source, exact diagnostic, resolved
types/symbols and candidate revision, preferably programmatically.
Cache strategies, not old patches. Key by authorized project/workspace,
scope, template/version, parent instruction identity/version and relevant
architecture, policy, Scala/tool, API and Phase-plan fingerprints.
Ordinary source edits invalidate task bindings only unless a higher-scope
dependency changes. Stale analysis/revision invalidates task bindings;
changed parent/template/policy/API invalidates affected descendants;
revoke defective instructions and descendants. Track generated, validated,
qualified and invalidated states, provenance, generation route/model,
reuse and failure evidence; use atomic publication and restart-safe
concurrency. Initial model suitability is a hypothesis: Program for
routine rules, Terra for bounded execution, Sol for ordinary strategic
specialization, Sol/Astra for domain/security strategy. Apply P255-03/04
allowance-aware selection, free-only defaults and explicit paid authorization.

#### Phase 28.2 boundary and comparative qualification

Goldenport Core owns typed comparison APIs; Phase 28.2 owns qualified
comparison analysis and deterministic migration, including safe decline
and verification. Phase 25.5 owns classification, strategy selection,
specialization and cache; do not duplicate rewrite engines. Phase 29 owns
lint policy expansion and Phase 27 generalized cross-route optimization.
Capture actual diagnostic, candidate/tool version, resolved types/symbols,
rule, eligibility/decline reason, patch and verification from 28.2 as
qualification evidence.

Compare A ordinary AI repair, B Phase 28.1, C B plus 28.2, D C plus
25.5 templates without scoped cache, E D with cache. Use identical
candidate snapshots, dependency/tool versions, validation and Admission;
repeat stochastic trials and report median and dispersion. Compare model
routes separately. Include real 28.1/28.2 logs, real project compiler and
lint failures, interacting diagnostics, stale cache/analysis and negative
fixtures. Record calls, input/output tokens, generation/adaptation/lookup,
validation, retries, first-attempt and retained repair, Admission,
regression, scope violations, Take Over, latency, actual paid cost and
allowance consumption per accepted repair. Include cold/warm cache and
invalidation costs; distinguish estimates from observations.

For equal repair cost, illustrative break-even is n(S-B)>G, where S is
uncached per-task strategy cost, B cached per-task binding/use cost, G
initial generation cost, n reuse count. Measure differing repair costs
separately; no measured savings are claimed. Qualify version variation,
false matches, scope isolation, concurrent publication/resume, invalidation,
free exhaustion/unknown balances, paid authorization and independent
review before closure. Promote AI strategies to deterministic rules only
at the owning boundary after semantic-preservation evidence, negative
tests and review. This is an Executable Knowledge feedback loop, not
generalized knowledge extraction.

## Boundaries

sm-workflow owns templates, scope, context dependencies and instruction
lifecycle. textus-ai owns provider capabilities, eligibility and accounting.
Phase 27 consumes suitability and reuse evidence for deterministic cost-aware
routing. Generalized knowledge extraction and pattern-language composition
are future work. Planning does not start a Session.

## Cross-project delivery — typed Domain Rules (2026-11-26)

Phase 25.5 is the parent delivery window for CNCF Phase 114 and the Textus AI structured-evaluation work. Preserve existing phase ordering. First define repair-template applicability (Predicate) and candidate strategy selection (Selection) usage contracts and fixtures. Then deliver CNCF Phase 114 typed evaluation SPI, followed by Textus AI adapter with OpenAI Structured Outputs, then integrate the released mechanisms in sm-workflow. Do not build a temporary duplicate evaluator. CNCF Phase 114.1 (full hybrid Evidence integration) is explicitly outside this closure dependency.

Prefer deterministic matching and candidate narrowing, then bounded AI Predicate/Selection with explicit decline and output validation. Value is a CNCF contract but not required for the initial repair pilot. Use free eligible routes first under existing allowance and authorization policies; preserve deterministic bootstrap routing and avoid recursive provider selection. Keep mandatory validation and Admission outside AI judgments. Use Corpus/Experiment to compare prompt complexity, accuracy, false decisions, declines, tokens, latency and cost per accepted repair; no savings claim before measurement. Qualify provider substitution and deterministic replay. Coordinate dependency releases and sync before integration; retain existing Phase 25.5 acceptance requirements.
