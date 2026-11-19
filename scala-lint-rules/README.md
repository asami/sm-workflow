# Shared Scala lint rules

SLP-1 was locally accepted through native checks and independent review. SLP-2
extends that delivery as a source candidate; fresh native qualification remains
required. Source authorship establishes neither publication nor runtime adoption.

The independently publishable `org.simplemodeling:scala-lint-rules_2.13:0.1.0-SNAPSHOT`
artifact uses Scala 2.13.14 and Scalafix 0.12.1. It depends on Scalafix's build-tool
API and Typesafe Config, and has no sm-workflow or CNCF runtime dependency.
Scalafix is provided by the consuming tool. The application consumes only the
portable resolver source and versioned profile data, not the rule engine.
The repository build loads rules through sbt-scalafix's `ScalafixConfig` dependency
configuration, as demonstrated by the upstream v0.12.1 local-rules fixture.

`scala-lint/profiles/v1/baseline.conf` retains `DisableSyntax.noFinalize` and
`NoOptionGet` and adds the authored safety, equality, security and structural
rules below. `goldenport.conf` selects `GoldenportPrivateMemberNaming`,
`GoldenportLocalHelperNaming`, `GoldenportFlatcaseNaming`,
`GoldenportVisibilityNaming` and `GoldenportNoDiscardedFailureResult`.
`history.conf` selects `GoldenportHistoryCommentPlacement` only when effective
history is Goldenport; effective none does not access that resource. These
concrete IDs implement the specification's provisional convention-rule names.

| Authored rule family | Bounded analysis and qualification inputs |
| --- | --- |
| Partial operations | Resolved Try/Either projection get, standard collection empty-sensitive operations and executable/aliased Predef placeholders; unrelated methods and total handling are paired non-findings. |
| Discarded results | Either/Try statement and Unit-callback adaptation; the team extension recognizes actual Consequence and Program/Free over UnitOfWorkOp. Compiler inputs reuse the project's selected published dependencies; the rules artifact has no dependency on them. |
| Equality and inference | Direct array comparison, inferred Any alternatives, receiver element/key types before collection widening, default array collection equality and local insertion/mutation of equality keys. Opaque/open types, callbacks or Ordering contracts remain explicitly limited; this is no proof of all equality laws. |
| Security | Known JDK credential/key, JDBC, TLS, cryptographic, ObjectInputStream and security-randomness APIs with bounded local values/aliases/constants. No cross-file taint engine, third-party sink catalog or arbitrary type-alias expansion is claimed. Unresolved flow is distinct from a known unsafe use and missing required analysis. |
| Structure | Parsed Scala 3 tree context and original delimiters for templates, packages, given/extension/enum and nested regions. DSL classification uses exact configured semantic structural/terminal symbols and explicit relevant owner prefixes, never a method-name ban. |

The actual CNCF OperationDsl.operation lambda is a terminal request handler, so
there is no blanket CNCF DSL structural-owner default. The fixture declares its
own qualified hierarchy-builder contract. Additional genuine production DSL
contracts require their own attributable mapping and qualification. Ordinary
expression leaves remain permitted. Scoped exceptions retain Scalafix's existing
suppression mechanism rather than a new registry or blanket Test exemption.

Security messages never interpolate credential values. The consumer diagnostic
callback additionally checks that its dummy key value is absent from messages.
This does not establish redaction of stock console source excerpts or the future
application output route; that normal runtime qualification remains SLP-4 work.
The bounded clean result cannot establish general security assurance.

`NoOptionGet` uses SemanticDB symbols for Option/Some/None getters. It does not
classify methods by their textual name. An unresolved `get` reference produces an
analysis-unavailable diagnostic; a missing SemanticDB document is a Scalafix
failure. No automatic rewrite or guessed default is supplied. Map and unrelated
get methods and total Option handling have paired compiler input fixtures.

The portable `project/ScalaLintProfiles.scala` source is compiled into both the
meta-build and artifact; the application compiles that same source directly.
It loads versioned resources, uses ordered rule union/deduplication and HOCON
fallback for settings. Project rule additions retain the inherited baseline.
Explicit removals and complete exception/effective-evidence semantics remain
SLP-3 work. Missing resources and malformed selected configuration fail resolution.
Resource includes use their resource origin and project includes their file origin.

The root application preserves its existing project configuration selection;
it does not activate the shared baseline across its current whole-source lint.
The public consumer-entry specification and testkit cover the shared source candidate.
The application adapter consumes normal resolved parameters; connecting its
effective data to the typed lint invocation and retaining it belongs to SLP-4.
Full A01 normal-route adoption remains owed until actual native qualification;
neither fixture authorship nor partial fixture proof establishes that criterion.
Current history
keys stay unchanged until the SLP-3 migration. Explicit history none disables
the history driver without disabling source naming or baseline rules.

The qualification input project compiles actual Scala 3.3.8 sources with
SemanticDB enabled. The independent rule module's testkit suite consumes that
compiler output and checks authored positive/negative convention, structural,
safety, equality and security fixtures. The unavailable-analysis test supplies
a real source absent from the compiler's SemanticDB classpath and requires the
producer's missing-document failure. SLP-1 qualified the pinned combination;
the new SLP-2 cases remain unqualified until fresh native results are observed.

`NoProjectConfigConsumerSpec` exercises Scalafix 0.12.1's public
`Scalafix.classloadInstance(...).newArguments().run()` consumer entry in CHECK
mode. The normal Test aggregate first compiles the handwritten Main and Test
sources from `fixtures/no-project-config` using the Scala 3.3.8
`scalaLintConsumerInput` project with SemanticDB enabled. The specification uses
that actual full classpath, sourceroot and compiler options, plus the local rules
class directory; it does not call `Rule.fix`, the testkit, a nested build or an
artifact fetcher. This supported local-classpath route requires no publication.

The fixture has no `.scalafix.conf`. The shared resolver reads that absent project
layer, selects Goldenport naming with history none, and loads `baseline@1` and
`goldenport@1` from the versioned resources. Its rendered effective configuration
is passed to the real consumer entry from an isolated temporary working directory
that also has no `.scalafix.conf`. The specification requires the consumer's
actual `LinterError` outcome and complete diagnostic/location set: Main has
`_wrapCommand`, two Option.get accesses and `DisableSyntax.noFinalize`; handwritten
Test adds Try/Either/collection/placeholder/discard/array cases, a naming
violation and a dummy embedded key. It logs actual rule IDs, source locations and
value-free messages in ScalaTest output. Exact diagnostic-set equality also checks paired
valid naming, Map.get, unrelated get and total Option handling as non-findings,
and CHECK must preserve all source bytes. Fresh native execution and independent
review remain necessary to qualify these authored assertions.

The fixture's separate `build.sbt` remains an optional artifact-based consumer
example requiring a normally published rules artifact; that separate build is
not the aggregate qualification route. Neither this bounded producer entry proof
nor testkit coverage establishes SLP-4's application runtime/FIX/admission route
or whole-source baseline activation.

The new SLP-2 rules are authored, not yet accepted. Missing required analysis is
not a successful placeholder. Whole-source checking alone supplies no
changed-scope attribution or acceptance of historical debt; SLP-4 must qualify
the existing affected-scope/FIX route before broad adoption. SLP-3 retains
breaking key migration, explicit rule removals and complete effective evidence.
