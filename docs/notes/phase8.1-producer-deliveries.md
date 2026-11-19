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
