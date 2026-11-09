# Phase 6.12 acceptance checklist

Stage Status:
- Current status: OPEN
- Owner: sm-workflow development
- Update rule: Mark items complete only with actual evidence and its reference.
- Closure basis: All SLP-A01–SLP-A33, SLP-B01–SLP-B04 and SLP-C01–SLP-C05 below;
  no implementation is claimed.

Plan: [Phase 6.12](phase-6.12.md), ordered Subphases 6.12-A / 6.12-B / 6.12-C.
Existing SLP-A01–10 are relocated unchanged from the unnumbered plan, not completed.
Step mapping: SLP-1 -> A01/A07/A11/A12; SLP-2 -> A03/A04/A05;
SLP-3 -> A02/A06/A08/A13/A14; SLP-4 -> A09/A10/A15 and integrated acceptance of all items.
Added 2026-11-09: SLP-A16–A33 belong to SLP-1/SLP-2, with exception and adoption
coverage through SLP-3/SLP-4. Existing identifiers and acceptance states remain.
Stage B maps SLP-B1 -> B01, SLP-B2 -> B02/B03 and SLP-B3 -> B04.
Stage C maps SLP-C1 -> C01, SLP-C2 -> C02/C03 and SLP-C3 -> C04/C05.

Producer dependency mapping and local plans:
[Phase 6.12 cross-project deliveries](phase-6.12-dependencies.md).
Use bounded accepted handoffs and actual consumer evidence for the existing
criteria. Planning adds no checklist IDs, acceptance claims or review cycle.


- [ ] SLP-A01: A project with no `.scalafix.conf` executes nonempty shared defaults
      through the normal runtime lint Action without AI rule selection. Retain
      the already adopted `DisableSyntax.noFinalize` rule in this baseline.
- [ ] SLP-A02: Baseline/team/project precedence, rule union/deduplication, explicit
      removal and leaf/array overrides have deterministic focused coverage.
- [ ] SLP-A03: `_wrapCommand`, unprefixed private `exact`/`reference`, wrong local
      helper names and misplaced version metadata are detected with rule/location.
- [ ] SLP-A04: Valid declarations, license comments and supported documented exceptions
      pass; fixtures cover nested/companion/multiple-definition Scala 3 source.
- [ ] SLP-A05: Handwritten Test sources are checked as well as Compile sources;
      generated code is excluded and reported with truthful applicability.
- [ ] SLP-A06: A scoped intentional suppression affects only its selected diagnostic;
      additions/removals and project-relative configuration remain reproducible.
- [ ] SLP-A07: Missing optional config succeeds; malformed selected config and missing
      rule artifacts remain explicit failures, never CLEAN or silently OFF.
- [ ] SLP-A08: Effective profile versions/configuration are retained with the actual
      validation. Later config changes do not alter issued work or historical results.
- [ ] SLP-A09: A detected violation follows ordinary FIX, passes affected re-validation
      and returns to admission in the same Session, preserving history and counters.
- [ ] SLP-A10: New/affected findings are distinguished from unrelated existing debt;
      accepted Phase 6.5 results and ongoing work remain intact. Delivery includes
      focused results, integrated configuration review and installation/adoption notes.

Use small fixtures and existing validation infrastructure. No product self-publication
test loop, blanket FULL reruns, new AI gate or extra monitoring is required.

- [ ] SLP-A11: `sm-workflow.source.style=goldenport` with absent history style
      enables Goldenport naming and inherited goldenport history, using
      `sm-workflow.source.history.rule`, without a project config file or second
      profile selection. Existing history rollover/normalization remains qualified.
- [ ] SLP-A12: All six source/history combinations in the specification pass;
      history=none leaves Goldenport naming enabled when selected, while history-only
      goldenport does not activate Goldenport naming. Effective none accesses no
      history rule and runs no history check/update/warning or version-placement lint.
      Generic baseline lint remains applicable.
- [ ] SLP-A13: Per-key CNCF precedence is applied before absent-only inheritance;
      explicit history override survives a higher-layer source default. Malformed
      selected styles/rules fail truthfully and do not silently select none.
- [ ] SLP-A14: Old source-history.style/rule keys have no execution alias, dual
      read or automatic conversion; old-only and mixed configurations report obsolete
      keys. Owned schemas, readers, templates, examples, fixtures and adoption settings
      use new keys. Historical evidence remains readable without becoming live config.
- [ ] SLP-A15: A small connected fixture qualifies new runtime/settings adoption and
      retained history behavior; already issued work, Sessions, counters and successful
      results remain unchanged. Delivery documents the breaking key rename and actual
      adoption limits without replaying successful checks merely to rename settings.
- [ ] SLP-A16: The generic baseline detects `scala.Option.get` using resolved
      method/type information, including optional worker identity and chained
      `toOption.get` calls in handwritten Compile and Test sources. It is active
      without project config and survives project rule additions under every
      source/history style. `Map.get`, unrelated `get` methods and total Option
      handling are not flagged. Scoped suppression affects only its selected
      diagnostic, and unavailable required analysis is a failure rather than CLEAN.
      Use the existing explicit removal, changed-scope admission, FIX and evidence
      contracts; add no blanket test exception or repository-wide cleanup gate.
- [ ] SLP-A17: The generic baseline detects resolved `scala.util.Try.get` calls;
      explicit success/failure handling and unrelated `get` methods pass. Reuse
      required semantic analysis and the existing resolution/exception/adoption
      contracts; missing analysis cannot produce CLEAN.
- [ ] SLP-A18: The generic baseline detects partial Either left/right projection
      `get` calls, while total Either handling passes. `toOption.get` remains an
      Option finding rather than a valid repair. Qualify supported Scala 3 APIs.
- [ ] SLP-A19: Each selected empty-sensitive standard collection operation in
      the specification is detected with its rule and location. Applicable
      Option-returning methods, folds, explicit empty handling, `drop`/`dropRight`
      and distinct nonempty APIs pass; unrelated same-named methods are not flagged.
      Cover supported immutable/mutable and inherited methods without pretending
      a name match proves partiality or requiring repository-wide cleanup.
- [ ] SLP-A20: Executable `scala.Predef.???` and aliased references are detected
      in handwritten Compile/Test sources; strings, comments and unrelated symbols
      are not. Standard scoped intentional exceptions remain effective, and no
      automatic rewrite fabricates an implementation or a successful result.
- [ ] SLP-A21: Shared baseline detects embedded credentials/private keys at
      supported typed APIs, including local constants. Dummy positive fixtures
      report no secret values; ordinary strings and external secret references
      are not misclassified. Preserve scoped exception and adoption rules.
- [ ] SLP-A22: Shared baseline detects dynamic data concatenation/interpolation
      at supported raw SQL sinks, including concatenation before preparation.
      Parameter binding, binding SQL interpolators and justified fixed structural
      choices are distinguished. Ordinary typed DataStore use reuses CNCF evidence.
- [ ] SLP-A23: Shared baseline detects explicit accept-all TLS trust, hostname
      verification bypass and supported disabling switches. Validating custom
      implementations and ordinary configured TLS are not blanket violations.
- [ ] SLP-A24: Shared baseline detects the specified weak cryptographic choices
      and evident IV/nonce misuse in supported security contexts. Non-security
      checksums, appropriate algorithms and correctly managed values are
      distinguished; no automatic replacement or artifact hashing is introduced.
- [ ] SLP-A25: Shared baseline detects unqualified Java object deserialization
      calls, while ordinary data-only decoding remains distinct. Filters or
      allow-lists alone do not establish safety of arbitrary untrusted input.
      Intentional qualified use follows existing scoped exception/review policy.
- [ ] SLP-A26: Shared baseline detects predictable randomness at supported
      security sinks, including fixed-seeded secure generators; simulation,
      backoff and deterministic fixtures are distinguished. Across A21–A26,
      qualify actual symbols/local flow, safe alternatives, aliases/constants,
      unresolved coverage and missing analysis without false security PASS.
      Reuse existing lint/FIX/security owners; add no private guard, blanket
      upstream retesting or extra review/AI cycle. Existing results remain intact.
- [ ] SLP-A27: Baseline detects discarded Either/Try in statements and
      Unit-adapted callbacks. Selected Goldenport profile extends coverage to
      Consequence/Program. Returning, composing, storing and handing off differ
      from dropping results; ordinary mutation APIs are not blanket violations.
      Repairs do not hide failure or execute a Program outside its managed owner.
- [ ] SLP-A28: Direct array universal equality/inequality and corresponding
      equals calls are detected. Explicit reference identity and correct element
      comparison are distinguished, including nested-array shallow-comparison
      limits; no automatic rewrite changes the intended equality relation.
- [ ] SLP-A29: Supported inferred Any widening reports its originating types
      as a warning. Explicit heterogeneous/interop Any, supported union types
      and valid numeric relationships are distinguished; reuse compiler findings.
- [ ] SLP-A30: Prioritize contains and inspect original element/argument types
      before generic widening. Detect proven incompatible equality in supported
      predicates, aliases and collection membership/comparison APIs, including
      indexOf/diff/intersect. Preserve permitted numeric/type relationships;
      strictEquality alone is not proof of precompiled library coverage.
- [ ] SLP-A31: Array element/key fixtures cover implicit equality in membership,
      distinct/distinctBy, grouping and Set/Map operations. Report unresolved
      value/reference intent honestly; distinguish actual intended identity and
      appropriate value keys. No method-name-only ban or assumed Eq consumption.
- [ ] SLP-A32: Key fixtures expose established mutation of equality/hash/ordering
      state, distinguish unrelated mutable fields and qualified stable keys,
      and cover actual sorted-collection behavior. Check declared domain identity
      without inventing missing intent, claiming all equality laws or choosing
      a business key/collision winner automatically.
- [ ] SLP-A33: NoStructuralIndentation rejects indentation-delimited structural
      definitions and Component/Service/Operation or other qualified structural
      DSL composition. Paired Scala 3 fixtures accept braced structures with
      terminal-expression indentation and distinguish ordinary lambdas, nested
      definitions, given/extension/enum/package bodies, type colons and trivia.
      Use AST context plus original tokens/positions; unresolved DSL structure
      is not silently accepted. Formatter settings retain structural braces;
      neither formatting nor compilation alone satisfies this criterion.

## Stage B: strict Scala 3 generation

- [ ] SLP-B01: Actual Cozy Scala 3 output and representative AI-authored source
      contain active `import scala.language.strictEquality` in the required scope.
      Common AI instructions/templates and distributed consumers agree; other
      language backends remain valid. Comments/strings do not satisfy the import.
      Generated and AI-authored output also follows A33's structural-braces policy,
      qualified at its owning generator/authoring boundary without relabeling
      managed output as handwritten lint input.
- [ ] SLP-B02: Intended same-type/bounded comparisons compile and distinct ID
      comparisons fail, including predicates in collection high-order functions.
      No universal CanEqual, Any/cast/equals bypass or disabling strictEquality
      is accepted merely to make a wrong comparison compile. Retain a fixture
      demonstrating the existing library contains limitation.
- [ ] SLP-B03: Record and qualify the strictest applicable supported compiler
      policy for null safety, unchecked operations and diagnostics, with exact
      Scala/backend/interop scope. Reuse lint/compiler responsibilities rather
      than treating strictEquality as complete strict type safety or silently
      breaking supported consumer contracts.
      Actively adopt provided explicit-null/initialization checks, appropriate
      discarded/unused-value and unchecked/deprecation/feature diagnostics and
      warning-as-error policy. Positive/negative cases establish support and
      enforcement for the exact version; record any concrete unadopted-feature
      reason. No blanket unsafeNulls, cast/`.nn` or warning-suppression bypass,
      duplicate lint, extra review cycle or live-setting change is implied.
- [ ] SLP-B04: Small generated and AI-authored consumer cases establish positive
      and compile-rejection behavior through existing runtime validation. Adopt
      qualified settings for subsequent work with attributable evidence; do not
      rewrite live Sessions, historical settings or successful results.

## Stage C: Core type-safe collection APIs

- [ ] SLP-C01: Bounded Core API delivery reuses/extends existing Eq and identity
      abstractions, with distinct value-equality versus identity extension names.
      Absent IDs never equate two unidentified entities; finalized public methods
      and scope are documented rather than assumed from example names.
- [ ] SLP-C02: Positive and compile-rejection fixtures prove receiver element/ID
      types cannot widen to accept an incompatible argument, including sibling
      IDs with a shared base/representation and available generic Eq instances.
      No unchecked universal equality or Any fallback defeats these guarantees.
- [ ] SLP-C03: Behavioral fixtures qualify actual Eq/hash/Ordering consumption,
      ordering and declared de-duplication/grouping/collision semantics. Ordinary
      Set/Map use is not mistaken for custom Eq consumption; no business policy
      is guessed or silently changed.
- [ ] SLP-C04: Representative Cozy-generated and AI-authored consumers use the
      actually delivered Core API and satisfy both strictEquality and applicable
      lint checks, with normal producer/publication/consumer evidence. Mere source
      presence, an import or a type-class signature is not consumer acceptance.
- [ ] SLP-C05: Migration/lint guidance names only available qualified APIs and
      preserves the declared comparison meaning. Missing Core delivery leaves
      only affected C acceptance open, without blocking independent A/B work,
      changing current Sessions or replaying applicable successful checks.
