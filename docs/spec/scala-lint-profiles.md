# Shared Scala lint profiles

Status: approved development direction, not implemented by this document.

Delivery ownership, producer-local plans and capability dependencies are tracked
in the [Phase 6.12 dependency plan](../phase/sm-workflow-6.12-dependencies.md).
That work ledger does not establish implementation or change this contract.

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

## Initial baseline safety rule: Option.get

The generic sm-workflow baseline includes `NoOptionGet` (proposed rule ID).
Report calls resolving to `scala.Option.get`, including chained access such as
`value.toOption.get` and `submitted.evidence.workerIdentity.get`. This rule is
independent of the selected team, source style and history style, and applies
without a project `.scalafix.conf`. A project rule list does not erase it.

Use resolved method/type information through Scalafix; a textual `.get` ban
cannot distinguish `Option.get` from `Map.get` or an unrelated API. Qualify the
required analysis on the supported Scala 3 version through the existing runtime
lint route. Unavailable required semantic information or rule implementation
is an explicit configuration/analysis failure, never a clean result or a silent
fallback to text matching. This extends the planned profile; it does not change
the accepted minimal syntactic lint or its historical results.

Recommend `fold`, pattern matching, `map`/`flatMap`, or conversion to the owning
typed error result with explicit handling of `None`. Do not automatically rewrite
to another throwing expression or choose a business default. Intentional
exceptions use the existing scoped suppression and reason policy; tests have no
blanket exemption. Apply the existing changed/affected-scope admission rules and
report unrelated historical occurrences separately.

Acceptance includes positive detection on an optional worker identity, chained
`toOption.get` and handwritten Test sources, plus non-findings for `Map.get`,
unrelated `get` methods and total Option handling. Demonstrate the inherited rule
without project configuration and with project additions, a rule-specific scoped
exception, and an explicit failure when required analysis is unavailable.

## Additional baseline safety rules

Selected 2026-11-09: extend the same generic baseline with the following small
set of failure-prone operations. IDs are proposed names for the shared Scalafix
package, not claims that these rules are installed or supplied by stock Scalafix.
Each selected diagnostic blocks new/affected scope under the existing admission
contract; unrelated historical occurrences remain separately reported debt.

| Rule | Required behavior and rationale |
| --- | --- |
| `NoTryGet` | Detect calls resolving to `scala.util.Try.get`, which throw for `Failure`. Require explicit success/failure handling through `fold`, pattern matching, composition or the owning typed error result. |
| `NoEitherProjectionGet` | Detect the partial `get` methods of Scala Either left/right projections. Use `fold`, pattern matching or right-biased composition; replacing one partial extraction with `toOption.get` does not resolve the finding. |
| `NoEmptySensitiveCollectionOps` | Detect standard Scala collection operations that throw when empty: `head`, `last`, `tail`, `init`, `reduce`, `reduceLeft`, `reduceRight`, `min`, `minBy`, `max` and `maxBy`. Prefer applicable Option-returning operations, folds, `drop`/`dropRight`, explicit empty handling or an API whose type guarantees nonemptiness. |
| `NoUnimplementedPlaceholder` | Detect executable references to `scala.Predef.???`, including aliased references. An unimplemented body must not reach accepted handwritten product or test code as if implemented. Comments, strings and documentation examples are not executable placeholders. |

For the first three rules, resolve actual method symbols/types and reuse the
semantic analysis required by `NoOptionGet`. For placeholders, use syntax and
symbol resolution as necessary to distinguish Predef from unrelated definitions
and aliases. Do not reject methods solely because their names are `get`, `head`,
`last` or similar. A distinct nonempty collection API is not automatically a
partial standard collection operation. Qualify the actual supported Scala 3
method families, including immutable/mutable collections and relevant inherited
methods; unsupported required analysis must fail explicitly.

No automatic rewrite may choose an empty-case value, swallow a failure or move
the exception to `getOrElse(throw ...)`. Intentional uses, including tests of
failure behavior, follow the existing narrowly scoped suppression and reason
policy. No blanket test exemption or additional exception registry is selected.

Retain the already adopted `DisableSyntax.noFinalize` rule in the shared baseline:
JVM finalization does not establish managed resource/process settlement. This
does not add new blanket bans on `var`, `null`, `throw` or casts. Types, syntax,
exhaustiveness and existing compiler diagnostics stay with the compiler; CAR
boundary checks stay with CAR lint. Do not duplicate them as private scanners.

The partial-operation cases are also described by the existing
[WartRemover rule catalog](https://www.wartremover.org/doc/warts.html)
(`OptionPartial`, `TryPartial`, `EitherProjectionPartial`, `IterableOps` and
`TripleQuestionMark`); the finalizer rule is documented by
[Scalafix DisableSyntax](https://scalacenter.github.io/scalafix/docs/rules/DisableSyntax.html).
These are references for rule behavior, not a requirement to add another lint
engine or evidence that the selected Scalafix implementation already exists.
Use small positive/negative fixtures, the existing three-layer resolution and
the same runtime/FIX/evidence route for all selected baseline rules.

## Result handling, inferred types and array comparison

Selected 2026-11-09: add the three previously proposed checks to the generic
profile. The rule names remain provisional implementation IDs.

| Rule | Scope and disposition |
| --- | --- |
| `NoDiscardedFailureResult` | Detect discarded Either/Try values, including statements and adaptation to Unit-returning callbacks. Require handling, propagation or the existing justified scoped exception. Do not ban every discarded non-Unit mutation/interop result. |
| `NoArrayValueEquality` | Detect array operands of universal `==`/`!=` and corresponding equals calls. Distinguish explicit intentional reference identity and proper element comparison; shallow `sameElements` does not establish nested-array value equality. Do not choose a replacement equality automatically. |
| `NoInferredAny` | Warn on supported branch/collection/generic inference widening to Any, showing the original alternatives. Distinguish explicit heterogeneous/interop Any, legal numeric relationships and supported union types. A warning alone does not prove a business defect. |

The selected Goldenport profile extends discarded-result detection to actual
Consequence and UoW Program APIs. Distinguish legitimate return/composition/
storage/handoff from dropping a result, and Program construction from execution.
Do not insert silent discard bindings or execute a Program outside its managed
owner as a lint repair. Reuse applicable compiler diagnostics without duplicate
findings. The generic baseline does not require Goldenport to be installed.

## Equality and identity in collection operations

Distinguish value equality, explicit reference identity and domain identity
(for example, the same ID at different revisions). CanEqual constrains comparison
eligibility; it neither chooses domain identity nor changes a library's comparator.
Do not infer an identity contract from a field name or ban collection combinators.

| Rule | Scope and disposition |
| --- | --- |
| `NoIncompatibleEquality` | Detect provably incompatible operand/element/key types in direct comparisons and supported collection APIs. Inspect caller types before generic widening hides the mismatch. Preserve permitted numeric relationships and valid explicit comparison contracts; reuse compiler diagnostics where applicable. |
| `NoImplicitArrayEqualityInCollections` | Report default reference-based array element/key equality in supported membership, de-duplication and grouping operations. Missing value-versus-reference intent is a policy warning, not proof that the application intended value equality. |
| `NoMutableEqualityKey` | Detect statically established mutation of state participating in key equality/hash/ordering while used in a supported collection. Distinguish unrelated mutable fields and valid stable keys; report relevant unresolved cases with analysis limits rather than asserting all equality laws are proved. |

Prioritize contains: its supported existing library signature can widen the
argument type before comparing internally. Check the original receiver element
and argument types, not only the inferred common supertype. Qualify compatible
numeric comparisons and genuinely heterogeneous collections as well as different
ID/entity types that should not compare. strictEquality alone does not establish
protection for such a precompiled library method.

Coverage includes comparisons inside exists/find/filter/filterNot/forall/count/
indexWhere and supported aliased callbacks; implicit comparison in contains/
indexOf/lastIndexOf/containsSlice/sameElements/diff/intersect; and element/key
equality in distinct/distinctBy/groupBy/groupMap/groupMapReduce/toSet/toMap and
supported Set/Map operations. Follow each actual API's equality/hash/Ordering
contract. A key projection or an available custom Eq is not evidence that the
standard collection uses that Eq. Sorted collections need their actual Ordering.

Where a domain identity policy is already declared, check the actual projection
or comparator against it. Without that policy, do not assume that full snapshot
equality is wrong or that every entity needs ID-only equality. Do not rewrite a
business key, equality implementation, deduplication policy or collision winner
automatically. Warnings follow existing disposition/review rules and create no
blanket additional AI review or approval gate.

Reference the [Scala equality contract](https://docs.scala-lang.org/scala3/reference/contextual/multiversal-equality.html)
and actual supported library implementations, including
[Seq comparison/de-duplication](https://github.com/scala/scala/blob/v2.13.16/src/library/scala/collection/Seq.scala)
and [grouping](https://github.com/scala/scala/blob/v2.13.16/src/library/scala/collection/Iterable.scala).
Qualify the project's actual versions rather than assuming a newer documented
signature. Types/symbols and supported local flow supply detection evidence;
method-name matching or an inferred Any alone is not semantic proof.

## Baseline security rules

Selected 2026-11-09: include the following security checks in the generic
sm-workflow baseline, independently of Goldenport/source/history selection.
These are planned shared Scalafix rules, not an implemented security scanner
or a claim that stock Scalafix already provides them. Rule IDs are provisional.

| Rule | Required detection scope |
| --- | --- |
| `NoEmbeddedCredentials` | Detect literal credentials/private keys supplied to known credential or cryptographic-key APIs, including locally constant values. Do not classify arbitrary strings as secrets by variable name alone. Diagnostics identify the location and API without reproducing the secret. |
| `NoUnsafeSqlConstruction` | Detect dynamic data concatenation or ordinary string interpolation into known raw SQL execution/preparation APIs. Distinguish parameter binding and SQL interpolators that actually bind values; preparation after concatenation is not parameterization. Constant query structure and explicitly bounded structural choices need their actual evidence, not a blanket interpolation ban. |
| `NoTlsVerificationBypass` | Detect explicit accept-all certificate trust managers, always-true hostname verifiers and known client switches that disable peer/hostname verification. A custom trust manager is not automatically a bypass; inspect the supported implementation/configuration rather than its class name. |
| `NoWeakCryptographicConfiguration` | Detect known weak security algorithm/mode selections, including DES/RC4/ECB and MD5/SHA-1 in security-sensitive digest/signature use, plus statically evident constant IV/nonce misuse where the selected algorithm requires freshness or uniqueness. Do not confuse a non-security checksum with authentication, or prescribe a cryptographic replacement automatically. |
| `NoUncheckedJavaDeserialization` | Detect Java `ObjectInputStream.readObject`/`readUnshared` calls without an explicitly qualified boundary. Input filtering or a class allow-list alone must not be reported as proof that arbitrary untrusted input is safe. Ordinary data-only decoding is a distinct API. |
| `NoPredictableSecurityRandom` | Detect ordinary pseudo-random generators, timestamps or fixed seeds used for known token/key/nonce generation, including insecure seeding of an otherwise secure generator. Distinguish security-sensitive sinks from simulation, backoff and deterministic test data; do not ban ordinary random generators globally. |

Use resolved API symbols, constant/configuration analysis and supported local
value flow supplied by the shared rule implementation. Publish the supported
API/use scopes with each rule version; do not infer a security sink solely from
a variable name. A statically established prohibited use blocks affected scope.
An unresolved security-relevant flow/configuration is reported for the existing
review route with its evidence limits, not asserted vulnerable or safe. Missing
required rule/type-analysis support remains an explicit execution/configuration
failure. A clean bounded lint result proves only the selected patterns were not
found; it never proves absence of injection, secret disclosure or other attacks.

Qualification uses small positive/negative cases for each supported pattern:
actual unsafe use, the supported safe alternative, unrelated same-named APIs,
local aliases/constants, unresolved analysis and scoped intentional exceptions.
Credential fixtures use dummy values and diagnostics must not disclose them.
No blanket test exemption, automatic cryptographic/security rewrite, new secret
store, extra AI gate or repository-wide security matrix is introduced.

The rule behavior follows the relevant OWASP guidance on
[secrets](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html),
[SQL construction](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html),
[TLS](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html),
[cryptography/randomness](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
and [deserialization](https://cheatsheetseries.owasp.org/cheatsheets/Deserialization_Cheat_Sheet.html).
These references guide the bounded rule contracts, not new runtime enforcement.

Keep the existing [security review ownership](../phase/phase-6-security-review-profile.md):
sm-workflow resolves/selects the shared lint profile and consumes its findings;
it does not build a substitute security engine, taint-analysis framework or
runtime guard. CNCF owns effect authorization, managed Git/SBT/process/file/
network boundaries and delivered DataStore guarantees; CAR lint retains CNCF
boundary/escape checks and Cozy owns generated-surface guarantees. Ordinary
conforming framework use reuses its applicable evidence instead of repeating
upstream security tests. General cross-file taint analysis, dependency CVE scans
and repository-history secret scans are outside this Scala lint rule delivery.
Cryptographic findings introduce no artifact hashes, fingerprints or publication
format changes. Any specifically required unavailable rule stays visibly open
without making unrelated producer roadmaps or completed Phases new prerequisites.

## Three-stage strict Scala 3 adoption

Selected 2026-11-09: deliver the following stages in order, retaining the current
lint/security plan and existing accepted results. This is a development plan,
not immediate activation in an executing Session or authorization to edit other
repositories. sm-workflow owns integration; each producer owns its implementation
and provides attributable, bounded delivery rather than waiting for unrelated
producer Phase completion.

### Stage 1: strict lint

Subphase 6.12-A delivers the shared baseline/team/project resolver and the
selected safety/security/equality rules above. Preserve error, warning,
unverified and unavailable distinctions; add no private analysis engine. Reuse
the existing runtime-owned lint/FIX/evidence route and applicable compiler checks.

#### Structural layout: braces outside expression leaves

Selected 2026-11-09: the Scala 3 baseline includes planned
`NoStructuralIndentation`. Structure uses explicit `{ ... }`; indentation syntax
is permitted only in terminal implementation-expression regions. This applies
to selected handwritten Compile/Test sources under every source style. Generated
Scala 3 follows the same policy through generator-output qualification, while
remaining excluded from the handwritten lint source set.

"AST leaf" here means a terminal implementation region, not literally a node
without children: an if/match expression or lambda still has AST children. The
lint contract distinguishes structural definition/composition from that region:

| Region | Required syntax |
| --- | --- |
| Package bodies; class/trait/object/enum bodies; given implementation and extension-group bodies | Explicit braces whenever a body/region is present. Bodyless declarations and single-expression aliases are not errors merely for having no braces. |
| Component/Service/Operation composition; DSL blocks declaring domain/framework hierarchy or public API structure | Explicit braces. Being passed as a lambda does not make a structural DSL block an allowed leaf. |
| Terminal method implementation expressions, ordinary computation lambdas, local control flow and terminal DSL expressions | Indentation syntax may be used where the region introduces no further structural definition/composition. Ordinary local value bindings alone do not define an API hierarchy. |

For example, reject `class Greeter:` but allow the following terminal method
implementation inside the explicitly delimited class:

```scala
class Greeter {
  def greeting(name: String): String =
    if name.isEmpty then
      "hello"
    else
      s"hello, $name"
}
```

An indented block containing a nested structural declaration/composition is not
an allowed leaf; give that enclosing region explicit braces. Structural rules
also apply to declarations nested in otherwise permitted expression regions.
An end marker does not substitute for the required structural braces. Scala 3
keywords such as then/do/using, type-ascription colons, comments and strings are
not forbidden merely by their spelling. This is a structure policy, not a ban
on Scala 3 syntax or a line-count/indent-depth limit.

Implement the shared rule using parsed tree context plus original source tokens
and positions to distinguish braces from indentation; an abstract tree or a
regex over colons alone is insufficient. Known framework DSL structure uses its
qualified contract/symbols rather than a method-name-only ban. Report unresolved
DSL classification honestly instead of certifying it as a safe leaf. Qualify
actual supported parser/dialect versions and diagnostic locations. No separate
analysis engine or runtime guard is introduced.

Scalafmt handles supported whitespace/layout normalization; its selected settings
must preserve required structural braces. Do not enable a blanket optional-brace
removal rewrite or a global no-indent setting that defeats the permitted leaf
syntax. A successful formatter or compiler run alone is not evidence of this
structural rule. Retain valid/invalid paired fixtures, including nested/anonymous
definitions, given/extension/enum/package bodies, structural DSL lambdas and
ordinary expression lambdas. No automatic bulk syntax rewrite is required.

This policy aligns with the existing
[CNCF indentation rule](../../../goldenport-cncf/docs/rules/repository-rules.md#scala-3-indentation-style-rule).
Implementation references:
[Scalafix trees/tokens/positions](https://scalacenter.github.io/scalafix/docs/developers/api.html)
and [Scala 3 optional braces](https://docs.scala-lang.org/scala3/reference/other-new-features/indentation.html).

### Stage 2: strict generated and AI-authored Scala 3

Subphase 6.12-B makes this active import standard in new or deliberately migrated
Scala 3 source produced through Cozy's generator chain and the AI coding path:

```scala
import scala.language.strictEquality
```

Cozy/the actual generator owner updates Scala 3 generation templates; the
development-workstation common AI instructions/templates are updated at their
existing owner and reflected into sm-workflow distribution at the normal boundary.
Do not inject a Scala 3 import into a Scala 2 backend or count text inside a
comment/string as active strictEquality. Generated code stays excluded from
handwritten lint; validate the generator's output and fix its generator.

Define CanEqual only for intended comparisons, using derives or explicit bounded
instances where appropriate. No blanket CanEqual[Any, Any], universal cross-type
fallback, widening/cast to Any, direct equals substitution or local disabling of
strictEquality may be introduced just to make an invalid comparison compile.
CanEqual does not define Eq/domain identity or prove equality/hash laws.

Use small positive and compile-rejection fixtures: two different ID types must
not compare, intended same-type comparisons compile, and comparisons inside
collection predicates remain checked. Preserve a case showing the remaining
precompiled contains gap rather than claiming strictEquality solved it. Inventory
the strongest applicable supported compiler settings for null safety, unchecked
operations and diagnostics, with exact version/interop scope and existing lint
coverage; the import alone is not a claim of maximum overall type strictness.
Adopt actual qualified settings for subsequent work without a blanket rewrite or
silently changing current compatibility contracts.

#### Prefer the compiler's provided safety features

Actively use the applicable Scala 3 compiler/language facilities before adding
an equivalent lint implementation. The strict profile selects supported checks
as the normal target, rather than merely listing optional features. Qualification
records exact options, Scala/backend/source scopes, diagnostic disposition and
any concrete compatibility reason an applicable feature remains unadopted.
Do not silently omit a provided check or claim an unavailable feature is active.

| Concern | Compiler/language facility to qualify and adopt |
| --- | --- |
| Comparison type safety | Active strictEquality import and intended bounded CanEqual instances. |
| Null safety | Explicit nulls (`-Yexplicit-nulls`) and explicit Java/nullable boundary handling. |
| Initialization safety | The version's initialization checker: Scala 3.3.8 exposes `-Ysafe-init`; newer documentation uses `-Wsafe-init`. Resolve the actual version rather than copying a flag blindly. |
| Discarded/unused values | Supported `-Wvalue-discard`, `-Wnonunit-statement` and applicable `-Wunused` categories, supplemented only for uncovered result/effect types. |
| Unsafe/deprecated language use | Applicable `-unchecked`, `-deprecation` and `-feature` diagnostics, alongside existing type/exhaustiveness checks. |
| Diagnostic enforcement | `-Werror` for the adopted strict compiler profile; exceptions remain specific, justified and visible rather than global warning suppression. |

Use the actual compiler definitions and focused positive/negative qualification,
not a flag's spelling alone, as evidence of support. Keep compiler and lint
findings attributable without duplicate reporting. Refine the profile when the
supported compiler changes; experimental or version-specific features require
their actual compatibility evidence, not blanket activation of unrelated flags.

Do not defeat these checks with blanket unsafeNulls imports, unchecked casts,
indiscriminate `.nn`, universal equality instances or broad warning silencing.
Necessary interop exceptions use the existing scoped policy with their reason;
they do not establish safety elsewhere. Fix generated output through its generator
and AI-authored output through its source/instructions. No new exception registry,
live build change, additional review cycle or historical-result replay is selected.

References: [Scala 3.3.8 compiler options](https://github.com/scala/scala3/blob/3.3.8/compiler/src/dotty/tools/dotc/config/ScalaSettings.scala),
[explicit nulls](https://docs.scala-lang.org/scala3/reference/experimental/explicit-nulls.html)
and [initialization checking](https://docs.scala-lang.org/scala3/reference/other-new-features/safe-initialization.html).

### Stage 3: type-safe collection extensions in goldenport-core

Subphase 6.12-C consumes a bounded goldenport-core delivery. Reuse existing Cats Eq
and Identified/Identifiable abstractions where suitable, adding distinct domain
identity semantics only where required. Give new standard-collection extensions
distinct names; containsEq, containsId, findById and distinctById are examples,
not finalized or already available API names. Existing collection members are
not changed by importing extensions.

Fix the element/ID type from the receiver and its declared identity relation;
an argument must not widen it to a common parent/Any just to make a call compile.
Eq[A] in a signature is not sufficient proof: qualify actual compiler inference
and existing generic Eq instances. Different ID types and mismatched elements
must fail compilation even when they share a representation/base class. Do not
add an Eq[Any] or unchecked universal-equals fallback to defeat that guarantee.

Separate value equality from ID matching; absent IDs must not make two entities
identical. De-duplication/grouping/key operations need Eq-consistent hashing or
an explicitly qualified alternative, preserving ordering and declared collision
semantics. Inserting values into an ordinary Set/Map does not make it consume
custom Eq. No ID/key or collision policy is guessed from an arbitrary record.

Qualify the producer API with positive/compile-negative and small behavioral
cases, then use it in representative Cozy-generated and AI-authored consumer
code. Lint recommends/requires the actual available safe API only after delivery
and adoption; do not emit calls to nonexistent extensions or auto-rewrite general
contains to a business-ID operation. Stage 1/2 can advance independently of an
unavailable Core delivery; only affected Stage 3 acceptance remains open.

All stages reuse existing validation, independent review where required and
normal publication/consumer verification. No second review/test loop, runtime
image copy, new workflow/Goal or retroactive successful-result replay is selected.

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
justification for a numeric input limit are outside these lint checks.

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
