# Phase 8.1 bounded lint integration

SLP-4 source authorship, 2026-11-19. The changes and executable specifications
below are **NOT RUN** by the semantic worker. They do not close acceptance items,
install a runtime, publish an artifact or admit a requester result. Native
validation, independent review, publication and result adoption remain with their
existing owners.

## Producer capability and consumer status

CNCF supplied the bounded SLP-EXEC capability from canonical `goldenport-cncf`
main, commit `bf1de998d2f3a89fc68adf138708007499f8e13c`, tree
`37a04bd6cb729960f654cb060ebd0c425a23b989`. Its actual producer handoff reports
Compile, four selected suites/112 tests and FixCheck passing, independent review,
and normal local publication of
`org.goldenport:goldenport-cncf_3:0.5.6-SNAPSHOT`. Those producer results are reused;
no producer check or publication is repeated by this source work.

The public API is `SbtLintInput(identity, revision, configJson)` on the existing
`SbtTarget.copy(lintInput = Some(input))` and typed FixCheck/UoW path. A trusted
fixed FixCheck declaration must opt in with `lintInput: true`. CNCF validates the
object/UTF-8/65,536-byte bounds, transports the fixed
`cncf.sbt.lint.input` envelope and retains the actual admitted input in
`SbtExecutionResult.lintInput`. sm-workflow merges profiles; the selected build
runs the existing pinned compiler/Scalafix engine. No additional executor, profile
resolver, AI gate or command override is introduced.

The genuine producer remains `ReturnReady`. Its original requester is still
`AwaitResult`: the first delivery observation was refused because a genuine
same-Session scope correction added one test path while the legacy returned input
omitted the correction reference. Producer success/publication and formal consumer
acceptance are distinct. Neither source authorship nor the projection below is
requester adoption.

## Authored normal Action connection

`WorkflowScalaLintAction` resolves current source/history keys through CNCF's
already resolved parameters and reads optional root `.scalafix.conf` through the
normal bounded Resource owner. Supported explicit file includes use the same
reader; URL/classpath includes, quoted Git paths, binary deltas and range-less
Scala changes fail explicitly. They do not select an empty profile or CLEAN.
Selected malformed configuration or missing capability/base fails before SBT
invocation. The existing admission-rejection alternative retains that failure.

The immutable actual input contains effective configuration, profile versions,
source/history selections, ordered rules, removals, project overrides, exact native
comparison base and finite changed paths/lines. The existing typed Git
`PendingDiff(capturedBase)` supplies that delta. No raw Git subprocess, caller
command, external source text or new evidence registry supplies it. Step uses the
captured candidate base; whole-Phase lint uses the original cumulative Phase base;
repository validation uses its original retained local base. The base is not
adjusted to hide canonical integration changes.

`WorkflowSbtValidation.Facts` and its public projection retain the actual CNCF
input and reject target-only echoes, missing/crossed result provenance and input
on another operation. Old receipts omit the optional field and remain readable.
A later config change does not alter a retained receipt or replay completed checks.

The root build decodes selected input without ambient fallback, then runs the
existing fixed check-only Scalafix tasks over captured changed handwritten Compile
and Test sources. Generated sources remain excluded. `validation.sharedScalaLint:
true` in `project.yaml` advertises this build contract to ordinary source-derived
validation. It requires an already trusted input-enabled FixCheck for that source
root; initialization selects no grant or command and rewrites no host plan.
An unavailable capability is an explicit setup failure, **not activation success**.
Legacy hosts retain their original unselected fixed-task behavior and old results.

## Bounded affected-scope reporting

The build uses Scalafix 0.12.1's public callback and typed `ScalafixFailed.errors`.
Only a pure `LinterError` failure can be classified by the reporter. Parser,
configuration, unavailable SemanticDB, rule-loading and other errors still fail.
The engine parses/analyzes full source context; only handwritten changed files
are selected. The pinned ScalaMeta 4.9.3 tree supplies attribution ranges.

An edit strictly inside a method body with an explicit result type expands to the
whole declaration. Signature/name/parameter/type changes, inferred-result methods,
fields and type headers conservatively expand to the enclosing type; import or
unattributed structural edits expand to the source. A line shared by header and
body conservatively uses the enclosing type. This policy makes no arbitrary
cross-file semantic-impact claim.

Affected error diagnostics block admission and follow ordinary FIX. Other located
diagnostics are reported separately as `OUTSIDE_AFFECTED_SCOPE age=UNKNOWN`.
Being outside changed lines never proves historical age. No unsupported historical
proof is invented. Reports retain rule/path/range/disposition and redacted content;
original source is used only in memory for attribution. This is stronger than the
earlier experimental `--diff` form, which is no longer the selected shared Action.
Full A10 acceptance still requires actual qualification of these bounded semantics
and the existing review's treatment of impact outside that support.

## Delivery provenance reconstruction

Scope-correction host composition and ordinary resume share the same projection
of original ordered paths/references plus the exact already admitted correction
reference. The original checkpoint and successful/failed/UNKNOWN effects remain
unchanged.

The existing retained-snapshot read supports the distinct
`bounded-derived-delivery` material kind for DelegateTask. It authenticates the
exact source prefix, native owner/definition/participant/Session and original
scope supersession receipt/WorkOrder/adjacent revisions, then derives only the
missing genuine correction source references. The old `bounded-state` resource
continues returning its literal source. Repeated derived reads append no history,
execute no work and create no acceptance. Foreign/missing/crossed provenance and
scope changes refuse. The existing requester result-received and independently
evidenced adoption retain their guards. The normal delivery inputResource
frontdoor now uses ordinary authorized Resource acquisition during session
composition as well as operation decoding.

## Qualification and installation limits

The authored connected native fixture includes Main naming/Option violations,
independent Test violations, ordinary FIX, one scoped suppression, generated
exclusion, separate reporting of an unchanged same-file occurrence and native
admission without successful-check replay. It selects a normally packaged rule
JAR directly as an explicit test-owned build dependency; it simulates no publish.
Attribution/data tests pair typed-body and signature/field/import scope changes,
and reject unsupported deltas. Native scope fixtures cover original Implement
and Repair correction provenance; a specification-owned legacy delivery shape
qualifies the derived read without inventing successful business checks/commit.

Retain accepted SLP-1 (`a84b42f6`), SLP-2 (`0c300ad9`), SLP-3 (`3e999f48`) and
Phase 6.5 Q3. Q3 remains applicable finalizer FIX/admission evidence, not proof of
all shared semantic profiles. The earlier current-key runtime fixture remains
part of this assignment. The selected rule host/input matrix remains Scala
2.13.14 / Scalafix 0.12.1 with analyzed Scala 3.3.8 compiler-produced SemanticDB.
Stock `DisableSyntax.noFinalize=false` remains unsupported; baseline true stays.

The parent reconciled canonical committed runtime source `c2016da9` into the
Phase candidate while preserving SLP-1–3. That is source reconciliation only.
No consumer runtime installation/publication or genuine requester acceptance is
claimed here. The breaking current keys are `sm-workflow.source.history.style`
and `sm-workflow.source.history.rule`, with absent-only inheritance and no old-key
aliases, dual reads or conversion. Already issued work and historical records are
not renamed. All unqualified A criteria and all B/C acceptance remain open.

## SLP-B2 compiler-policy adoption — 2026-11-20

The actual BUILD-01/02 delivery is requester-adopted from sbt-cozy commit
`c978b1a1ae878edbdc6fa2d588228c6c4ecdf2de` (validated source tree
`cfcfaa12c1b0d048af2d8a2d52ff2e1805e3686c`). The current Phase task's retained
`slpb2-sbt-cozy-adoption-001/response.json` records the attributed attachment;
its terminal message explicitly supplies no consumer activation/check proof.
The producer's [exact settings and receipts](/Users/asami/src/dev2026/sbt-cozy/target/sm-workflow/slpb2-compiler-policy-20261120/slpb2-qualified-settings.md)
and [retained return detail](/Users/asami/src/dev2026/sbt-cozy/target/sm-workflow/slpb2-compiler-policy-20261120/return-detail.json)
are the evidence inputs, rather than option-name availability alone.

The producer qualified an ordinary isolated Scala 3.3.8 JVM fixture on JDK
21.0.13.0.3 / fixture sbt 1.11.7. Compile/Test positive cases handle Java nullable
input and resolve Test's Compile dependency. Both scopes independently reject
wrong equality/unhandled null, and separately establish diagnostic emission and
Werror enforcement for safe initialization, discard, nonunit statements, unused
imports/private/local declarations, unchecked patterns, deprecation and feature
warnings. Its typed Compile and one-test focused suite exited 0; independent
review is retained in those receipts. No compatibility exception or plugin
change was needed for that isolated fixture. The Scala 2.12.20 plugin host is
separate. This evidence establishes the reusable settings, not this consumer's
result or the producer's remaining BUILD-03/04 criteria.

The product build now selects all supplied options on `scalaStrictAuthoring`:

```scala
scalaVersion := "3.3.8"
scalacOptions ++= Seq(
  "-language:strictEquality", "-Yexplicit-nulls", "-Ysafe-init",
  "-Wvalue-discard", "-Wnonunit-statement", "-Wunused:imports,privates,locals",
  "-unchecked", "-deprecation", "-feature", "-Werror")
```

Both Compile and Test inherit this project-level policy. Its exact Compile
source is `scala-lint-rules/strict-authoring/src/main/scala/org/simplemodeling/textus/smworkflow/admission/StrictComparisonExamples.scala`;
its exact Test source is
`src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowStrictComparisonSpec.scala`.
The latter is explicitly excluded from root/Test and selected only in this
module. Its root-only LocalTestDeclaration annotation is removed because the
module uses normal SBT ScalaTest discovery. Root's existing canonical metadata
inventory and selection retain the same root boundary; this new suite runs once
through root `test` aggregation. There is no root/Test dependency or second
compiler/test runner. Normal fix-check compiles the strict Test scope and sends
its handwritten sources through the same effective config/reporter and ordinary
Scalafix, alongside root's existing sources. The rules artifact's Scala 2.13.14
host and deliberate violating Scala 3 lint inputs retain their own options.

The extracted B02 model retains derived same-ID equality and the explicitly
value-bounded Revision instance. The same actual suite retains positive direct
and predicate behavior, diagnostic-specific distinct-ID rejection (direct,
bounded and higher-order), and successful compilation/false behavior for the
precompiled Vector.contains gap. Its deliberate mismatched runtime call has a
single-line NoIncompatibleEquality exception with the documentary reason; all
other diagnostics remain active. A small Java Properties adapter normalizes
nullable input to Option[SourceId]; present/empty/absent behavior and unchecked
nullable-access rejection exercise that foreign boundary under the adopted
profile. Assertion results in Unit contexts are explicitly consumed, with no
warning suppression or change to their checked behavior.

Consumer status is **AUTHORED, NOT RUN**. Runtime compile, test and fix-check must
retain the strict module's actual Compile/Test scalacOptions and sources and
confirm the suite executes once, alongside the existing selected checks. No
consumer compatibility exception has been established yet. These bounded sources
supply no package-wide compatibility claim. Legacy mixed application scopes,
deliberate violating lint fixtures and real generated outputs remain outside
this selected target and require their own qualification in B3.

Accepted B1 evidence is reused: SimpleModeler `365814a9` and Cozy `6feac334`
qualify imports/structural braces in 16 selected Scala 3.3.8 Compile/Test scopes,
with full Workflow/FailureModel bodies excluded for pre-existing duplicate
contexts. Their B01 result supplies no B03 warning/null-policy or B04 complete
consumer proof. Actual generated plus authored consumer adoption remains B3;
Core stage C and all unqualified criteria retain their existing boundaries.
No producer source, live Session, historical result, publication, Git history or
successful effect was changed by this authorship.
