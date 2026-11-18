# Shared Scala lint rules

Source candidate for SLP-1; executable qualification is part of the normal native
Test aggregate. Source authorship does not establish its execution, publication
or native runtime adoption.

The independently publishable `org.simplemodeling:scala-lint-rules_2.13:0.1.0-SNAPSHOT`
artifact uses Scala 2.13.14 and Scalafix 0.12.1. It depends on Scalafix's build-tool
API and Typesafe Config, and has no sm-workflow or CNCF runtime dependency.
Scalafix is provided by the consuming tool. The application consumes only the
portable resolver source and versioned profile data, not the rule engine.
The repository build loads rules through sbt-scalafix's `ScalafixConfig` dependency
configuration, as demonstrated by the upstream v0.12.1 local-rules fixture.

`scala-lint/profiles/v1/baseline.conf` enables `DisableSyntax.noFinalize` and
`NoOptionGet`. `goldenport.conf` adds `GoldenportPrivateMemberNaming`. The latter
checks explicit private def/val/var/lazy val names, including qualified visibility
and escaped names; explicit overrides are excluded. It distinguishes type/object
declarations. It does not implement the remaining local-helper, visibility,
flatcase or history-placement conventions.

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
compiler output and verifies diagnostics. The unavailable-analysis test supplies
a real source absent from the compiler's SemanticDB classpath and requires the
producer's missing-document failure. These are executable specifications, not
claims that the pinned combination has passed.

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
Test has one Option.get access. It logs the actual rule IDs, source locations and
messages in ScalaTest output. Exact diagnostic-set equality also checks paired
valid naming, Map.get, unrelated get and total Option handling as non-findings,
and CHECK must preserve all source bytes. Fresh native execution and independent
review remain necessary to qualify these authored assertions.

The fixture's separate `build.sbt` remains an optional artifact-based consumer
example requiring a normally published rules artifact; that separate build is
not the aggregate qualification route. Neither this bounded producer entry proof
nor testkit coverage establishes SLP-4's application runtime/FIX/admission route
or whole-source baseline activation.

Generic safety/security/equality rules beyond Option.get, full convention
coverage and structural indentation remain SLP-2 work. No absent rule is listed
as installed or represented by an empty implementation. Whole-source checking
alone supplies no changed-scope attribution or acceptance of historical debt;
SLP-4 must qualify the existing affected-scope/FIX route before broad adoption.
