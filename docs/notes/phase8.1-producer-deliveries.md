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

## SLP-B3 representative consumer preparation — 2026-11-20

B2 is now accepted at `8502548d`. Its retained independent review
`slpb2-review1-result.json` cites native receipt
`34c1be2c560461e9905745f2e06dc533:ordered-validation:3611:validation` with
compile/test/fix-check exited 0 and seven strict-authoring tests executed once.
The preceding AUTHORED/NOT RUN paragraphs preserve the authorship snapshot;
they are not the current status of those unchanged seven cases. B3 reuses
their direct/bounded/predicate rejection, null-boundary and contains evidence.

The new tiny library CML fixture declares `CountryCode` and `RegionCode`.
The same `scalaStrictAuthoring` target enables explicit Cozy qualification and
retains all ten B2 compiler options. Its `sourceGenerators` boundary now selects
exactly those two generated library value sources, failing if either is absent
or duplicated. Library/Maven metadata selects `modeler-scala-value`. The
previous request also selected `CompositeStateMachineAbi.scala`; the actual
library generator emits only the two values and reserves that ABI for its CNCF
branch. Removing that unsupported assertion corrects this representative B04
consumer. B1's accepted CNCF ABI evidence retains its original scope; no fresh
ABI qualification is claimed.

`GeneratedStrictComparisonSpec` is selected alongside the unchanged authored
specification in qualification-mode Test discovery. Its three cases consume both actual
generated value types, preserve same-type and collection-predicate behavior,
require diagnostic-specific cross-value rejection, and retain the precompiled
contains limitation with one reasoned line-specific lint exception. Generated
files remain managed output excluded from handwritten lint. The value model
uses ordinary declared dependency coordinates; native validation must retain
its actual resolved classpath. No compiler exception is introduced before an
actual diagnostic.

The Cozy owner returned
`P81-SLPB3-COZY-GENERATOR-ACTIVATION-20261120` in the existing Cozy checkout at
`target/sm-workflow/p81-slpb3-cozy-generator-activation-20261120/slpb3-generator-activation-return.md`.
Native LocalPublication confirmed `published`, exit 0, for Cozy
`6feac3349d59092c609c85ab1f01cfd49057edd5`, tree
`7a873e78a580d9ee1ed34a008e25c437d2c186b6`, as
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT` in the task-private Ivy repository.
The original activation classpath selected that published JAR and matching SimpleModeler
classes from source `365814a9c7a71bb2a3c131de0eafd31c85e39d1b`, tree
`07d3dab1ddba38faa135e88b0973740734fc771c` / `1.1.27-SNAPSHOT`.
The actual launcher/bridge probe exited 0 and produced `CountryCode.scala` and
`RegionCode.scala` with strict-equality imports from this CML fixture. The
producer retains the private worktrees because the classpath references them.
Its `activation-component-verified.json` and `activation-output` retain that
probe. Subsequent native B3 compilation reached the generated values and failed
with 36 unused-import/private-member errors under the unchanged strict profile.

The Cozy destination accepted the semantic result but returned `Human` at generation
1 because the original consumer demanded three library outputs. It issued no
`ReturnReady`. Publication and bridge evidence can identify the proposed local
runtime; they do not establish native destination delivery adoption or consumer
validation. The consumer corrects its two-value source selection within B04,
without changing producer source or replaying accepted B1/B2 checks.

The bounded SimpleModeler repair
`P81-SLPB3-SIMPLEMODELER-STRICT-LIBRARY-20261120` returned `ReturnReady` at
generation 2. Source commit `df7d8b5f850b9d483c96fddf806295b92f470598`, tree
`3d518730cbaf7542a159cf9e85dd70f3fc4c9a65`, limits reduced import/helper
emission to plain single-string library values. Structured values and
create-purpose builders retain their used readers. Active strictEquality,
bounded CanEqual and existing structural contracts remain covered by the
focused generator specification. Retained native Compile and all five
`Scala3StrictStructureGenerationSpec` tests succeeded; independent re-review
accepted the repaired candidate. Runtime-owned local publication exited 0.
The requester received that return; its qualification remains distinct from
the B3 strict Scala 3 consumer result and the earlier Cozy return.

The selected task-private Ivy artifact is
`org.simplemodeling:simplemodeler_2.12:1.1.27-SNAPSHOT`, JAR
`simplemodeler_2.12.jar`, SHA-256
`44685bcd6f99fba422a2146fe1b1d2879a27f10c36d47812cc34ac6eb2da7ca8`.
The producer compared its matching sources JAR generator source byte-for-byte
with the commit. The retained `slpb3-simplemodeler-artifact-selection.json` and
`slpb3-simplemodeler-result-observation-004/response.json` identify the selection
and actual returned evidence. The task-private activation classpath now replaces
only the prior SimpleModeler source classes with this JAR, keeping Cozy
`6feac3349d59092c609c85ab1f01cfd49057edd5` /
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT` and its other entries selected.

The prior task-local qualification used the ignored module `.cozy/config.yaml`
to select that runtime and disable unchanged-generation caching. Native receipt
`34c1be2c560461e9905745f2e06dc533:ordered-validation:3676:validation`
records full compile/test/fix-check terminated with exit 0. It compiled exactly
the two generated values alongside `StrictComparisonExamples`; the generated
three-case suite and authored seven-case suite ran once each, with all ten tests
successful. The rules module's 58 tests and root's 3,024 tests in 289 suites also
passed. This retained success describes the prior candidate and its selected
local runtime. Review issue-17 identified the dependency of ordinary builds on
that ignored task-local selection.

### Explicit generated qualification after issue-17

Ordinary root compile/test defaults to `strictGeneratedQualification = false`.
It keeps the seven B2 authored cases and all ten compiler flags, while omitting
both generated values and their three-case specification. `Def.taskDyn` keeps
generation outside this default task graph even when a local Cozy selection is
present. The library module disables the plugin's unused CNCF ABI resource
generator, which would otherwise trigger generation independently.

To reproduce B04, prepare an explicit local Cozy project with its real
`build.sbt` from `6feac3349d59092c609c85ab1f01cfd49057edd5` and its existing
`target/cozy.d/runtime-classpath.txt` launcher input. Retain the matching Cozy
`0.3.3-SNAPSHOT` published JAR and other activation dependencies. Select exactly
the published SimpleModeler `1.1.27-SNAPSHOT` JAR from `df7d8b5f`, verified against
SHA-256 `44685bcd6f99fba422a2146fe1b1d2879a27f10c36d47812cc34ac6eb2da7ca8`,
in place of the earlier SimpleModeler source classes; no older source directory
or JAR may mask it. The SNAPSHOT coordinate alone does not identify this repair.
The retained selection and producer publication evidence above identify the
required artifact; preparing this local runtime uses the existing Cozy launcher
contract, with no additional resolver or runner in this consumer build.

Set the following in the existing module configuration
`scala-lint-rules/strict-authoring/.cozy/config.yaml`, substituting the prepared
local project location for the illustrative path:

```yaml
generation:
  delegate:
    project_dir: /absolute/path/to/qualified-cozy
qualification:
  strict_generated: true
```

The same keys can use Cozy's existing module shared configuration
`conf/cozy/config.yaml`; keep machine-specific locations in local configuration.
No task-local absolute path belongs in tracked inputs. Existing sbt-cozy resolves
`generation.delegate.project_dir` to `cozyDelegateProjectDir`, validates the
selected project and passes `--runtime-dev-dir`. Ambient `SBT_COZY_PROJECT_DIR`
must remain absent from the full root CAR build.

For the owning runtime's selected programs,
`SM_WORKFLOW_STRICT_GENERATED_QUALIFICATION=true` enables the same mode, and
`false` explicitly selects ordinary behavior; any other supplied value fails
build loading. With the variable absent, the module configuration Boolean
defaults to false. Explicit qualification requires a selected generator and
forces `cozySkipUnchangedGeneration = false` regardless of cache settings,
so changing selection cannot reuse the old generation-state cache. It compiles
exactly one existing `CountryCode.scala` and `RegionCode.scala` each and includes
the generated Test specification once. Missing selection, failed generation or
missing/duplicate source artifacts fails qualification rather than skipping it.

Current issue-17 candidate validation is **PENDING**. No build or tests were run
for this repair assignment. The owning runtime must demonstrate default compile
without generated qualification and explicit fresh generated test/fix-check with
all ten cases and the unchanged flags. Retain the selected JAR identity,
regenerated-source inventory/provenance and actual compiler/classpath evidence;
the earlier receipt remains historical evidence, not a pass for this candidate.
Any demonstrated compiler compatibility exception needs its exact
source/diagnostic scope and reason; none is claimed from inspection.
Full Workflow/FailureModel bodies retain the prior duplicate-context exclusions.
No current model representation, historical Session/result/count, successful
effect or Core stage C requirement changed, and B04 remains open.

## SLP-C1 accepted Core source delivery — 2026-11-21

C01 now records the adopted bounded Core delivery
`P81-SLPC1-CORE-EQUALITY-IDENTITY-API-20261120`. The producer's `ReturnReady`
result identifies canonical `goldenport-core` main commit
`e4972601207d4597f0ed3aa77fc0ed62fb286c75`, parent
`d959a576bc64a5480edbe3781eff624b7cae8698`, and validated tree
`b431c2b2a11946761912472f591b9faaf2efa19e`. The requester is `Adopted` for
that attributed result/selection attachment. Source selection is this exact
commit, not the ambient version coordinate. No local publication was performed;
`org.goldenport:goldenport-core_3:0.4.3-SNAPSHOT` identifies the producer version
but supplies no verified binary selection. Consumer activation remains pending.

### Accepted public surface and comparison meaning

The committed [TypeSafeCollections.scala](../../../goldenport-core/src/main/scala/org/goldenport/util/TypeSafeCollections.scala)
and [TypeSafeCollectionsSpec.scala](../../../goldenport-core/src/test/scala/org/goldenport/util/TypeSafeCollectionsSpec.scala)
at the selected commit define this immutable-Vector-only surface. Use
`import org.goldenport.util.TypeSafeCollections.*`, `cats.Eq`, and the existing
`org.goldenport.id.{Identified, Identifiable, UniversalId}` types. Both relation
traits are declared in
[Identified.scala](../../../goldenport-core/src/main/scala/org/goldenport/id/Identified.scala):
`Identified[T, I <: UniversalId].id(entity: T): I` and
`Identifiable[T, I <: UniversalId].id(entity: T): Option[I]`.

The exact public signatures below are transcribed from the selected source;
wrapper constructors are `private[TypeSafeCollections]`.

```scala
extension [A](values: Vector[A])
  def typeSafe: VectorValues[A]

// VectorValues[A]
def containsEq[B](value: B)(using equality: Eq[A])(using same: B =:= A): Boolean
def identifiedBy[I <: UniversalId](relation: Identified[A, I])(using equality: Eq[I]): VectorIds[A, I]
def identifiableBy[I <: UniversalId](relation: Identifiable[A, I])(using equality: Eq[I]): VectorIds[A, I]

// VectorIds[A, I <: UniversalId]
def containsId(id: I): Boolean
def findById(id: I): Option[A]
def distinctById: Vector[A]
```

`typeSafe` fixes receiver element type `A` before a comparison argument is
supplied. `containsEq` scans with the supplied `Eq[A]`; the separate `B =:= A`
evidence prevents argument-driven widening. The receiver's already declared
type remains significant: an explicitly widened `Vector[Any]` still has `A = Any`.
The API does not recover a narrower domain contract from that declaration.

Callers explicitly select `identifiedBy(relation)` for mandatory IDs or
`identifiableBy(relation)` for optional IDs. That selection captures `I` before
a lookup argument is supplied; no implicit adapter chooses between relations
when both exist. `containsEq` expresses value equality; the ID wrapper uses
`Eq[I]` over the selected relation, separately from value/reference equality.
Importing the extension does not change standard `Vector.contains` behavior.

Lookup returns the first entity with a present Eq-equivalent ID; absent IDs
never match. `distinctById` preserves input order, the first entity for each
Eq-equivalent present ID, and every absent-ID occurrence, including repeated
references. Conflicting payloads are not merged or resolved. Lookup is O(n);
distinct is O(n²) in the worst case. Both use Eq scans without Hash, Ordering,
Set or Map. This delivery adds no grouping API or other collection-family API.
Its distinct names are available at this selected source revision; earlier
provisional names did not establish their availability or direct Vector syntax.

### Reused producer evidence and remaining Phase work

The caller-retained `return-derived-snapshot.json` contains the actual
`ReturnReady` material, commit and native receipts: compile exited 0 and
test-only exited 0 with one completed suite / seven tests, associated with the
validated tree above. Its `adopted.json` records requester attachment adoption;
`adoption-observation.json` selects `canonical-source-commit` and records no
local publication or consumer activation. These are retained public delivery
inputs, separate from this Phase's validation receipts.

Producer `review3-result.json` accepts the repaired strict-compilation boundary
with no findings; `acceptance-result.json` accepts all three delivery conditions
and the actual commit. Those records describe strict compilation as an inference
from the successful test-only result and its build dependency because the
receipt then available to the reviewer lacked detailed compiler stdout. The
caller-retained `strict-test-stdout.log` additionally shows two Scala sources
compiled to `collectionStrict-classes`, one Test source compiled, and all seven
focused tests passed. This source record uses that actual log; it does not
attribute the later stdout inspection to the earlier reviewer.

The committed build selects only the API and its specification for
`CollectionStrict`, extends Test, reuses Test's dependency classpath and makes
`Test / compile` depend on `CollectionStrict / compile`. The producer toolchain
is Scala 3.3.8, Cats 2.7.0, sbt 1.9.8 and JDK 21.0.13.0.3. Its selected flags are:

```text
-release:17 -language:strictEquality -Yexplicit-nulls -Ysafe-init
-Wvalue-discard -Wnonunit-statement -Wunused:imports,privates,locals
-unchecked -deprecation -feature -Werror
```

The seven producer cases cover supplied lawful string Eq with property checks,
explicit nested-array Eq, stable first-ID/absent-ID behavior, mandatory-relation
Eq consumption, matching direct/captured calls with common-parent Eq instances,
and diagnostic-specific rejection of mismatched elements and sibling IDs in both
direct and captured calls. The latter establish `String =:= Entity` rejection
and `SpanId` versus required `TraceId` diagnostics at the tested calls, even with
generic `Eq[Any]` / `Eq[UniversalId]` and applicable common-parent instances in
scope. The positive case also deliberately retains an explicitly widened receiver.
These results concern the selected API/specification, not a strict conversion of
all legacy Core sources or proof of arbitrary caller-supplied Eq laws.

This C01 update records the accepted API and source evidence only. No Core work,
producer check or successful effect is repeated. No consumer build/dependency,
generated output or AI instruction is changed. Phase C02/C03 qualification stays
with SLP-C2; C04/C05 generated/AI adoption and guidance stay with SLP-C3. Runtime
Phase validation, independent review and acceptance of this record remain
pending; the existing checklist is unchanged.

## SLP-C2 bounded type and collection qualification — 2026-11-21

C02/C03 consume the seven successful producer cases from the C01 source
selection above, `goldenport-core` commit
`e4972601207d4597f0ed3aa77fc0ed62fb286c75`. This qualification compares the
committed specification with the retained native test-only receipt and its
`strict-test-stdout.log`; it adds no producer execution. The receipt associates
one completed suite and seven tests with tree
`b431c2b2a11946761912472f591b9faaf2efa19e`, exit 0. The log explicitly records
compilation of the two selected `CollectionStrict` sources under the Scala 3.3.8
strict options recorded above, then Test compilation and the successful suite.
This qualifies that bounded API/specification, not all legacy Core sources.

### C02: matching calls and attributed rejections

Table 1 compares the exact positive and negative expressions. Here `values` is
`Vector(Entity(None, "x")).typeSafe`; `ids` is
`Vector(Entity(Some(TraceId("major", "minor")), "x")).typeSafe.identifiableBy(optional)`.
`optional` is the fixture's `Identifiable[Entity, TraceId]`.

Table 1. Receiver and argument type qualification at the selected revision

| Operand form | Matching expression | Incompatible expression and actual diagnostic |
| --- | --- | --- |
| Captured element wrapper | `values.containsEq(Entity(None, "x"))` compiles | `values.containsEq("not an Entity")`: `Cannot prove that String =:= org.goldenport.util.TypeSafeCollectionsFixtures.Entity.`, column 42 |
| Inferred, chained element wrapper | `Vector(Entity(None, "x")).typeSafe.containsEq(Entity(None, "x"))` compiles | `Vector(Entity(None, "x")).typeSafe.containsEq("not an Entity")`: the same `String =:= Entity` diagnostic, column 70 |
| Captured ID wrapper | `ids.containsId(TraceId("major", "minor"))` compiles | `ids.containsId(SpanId("major", "minor"))`: found `org.goldenport.observation.SpanId`, required `org.goldenport.observation.TraceId`, column 29 |
| Inferred, chained ID wrapper | `Vector(Entity(Some(TraceId("major", "minor")), "x")).typeSafe.identifiableBy(optional).containsId(TraceId("major", "minor"))` compiles | The same chain ending in `containsId(SpanId("major", "minor"))`: found `SpanId`, required `TraceId`, column 112 |

The positive `typeCheckErrors` block asserts no errors. Each negative block
asserts exactly one error, its call text, a column within that call and the
specific type diagnostic above. The stdout prints all four actual diagnostics.
This attribution distinguishes the intended rejection from an unrelated missing
import, missing Eq or strict-warning failure.

Matching and mismatching element blocks have `Eq[Any]`, `Eq[Entity | String]`,
`Eq[UniversalId]`, `Eq[Entity]` and `Eq[TraceId]` in scope; ID-negative blocks
have the same givens except `Eq[Entity | String]`. The common/generic-parent
instances coexist with the narrow instances in these tests. Their availability
does not cause the incompatible argument to widen the captured receiver or ID
type. These are not parent-only implicit-selection tests, nor an inventory of
every possible generic Eq derivation.

The deliberate declared-receiver control is exactly
`val widened: Vector[Any] = Vector(Entity(None, "x"))`, followed by
`widened.typeSafe.containsEq[Any]("a valid Any")`; it compiles in the positive
block. Its explicit argument type is `Any`, matching its declared receiver.
This establishes the boundary between argument-driven widening and a caller
already choosing a broad contract. It does not demonstrate an inferred
`containsEq("a valid Any")` on that receiver, a widened ID-relation control,
or negative calls for every method. The source fixes `findById` to the same
captured `I`, but its separate negative call is not executed by this suite.

### C03: observed equality, absence and collision behavior

Table 2 maps the behavioral cases to the policy they actually exercise.

Table 2. Behavioral qualification and its limits

| Producer case | Observed result and scope |
| --- | --- |
| Supplied string value Eq | Case-insensitive Eq makes `Vector("Alpha").typeSafe.containsEq("ALPHA")` true. ScalaCheck checks reflexivity, symmetry and conditional transitivity for this Eq on generated strings. The empty string Vector reports false for membership. |
| Explicit nested-array Eq | A caller-supplied `Eq[Array[Array[Int]]]` checks outer length and each row with `sameElements`. Distinct array objects containing `Array(Array(1, 2))` match; `Array(Array(2, 1))` does not. No default deep-array Eq or arbitrary nesting law is established. |
| Optional identity relation | With TraceId Eq comparing `major` and `minor`, input `Vector(absent, first, conflict, absent, other)` retains `Vector(absent, first, absent, other)`. `first` and `conflict` have equivalent present IDs and different payloads. Lookup returns `Some(first)`, and membership is true. An absent-only input returns `None` for that ID; empty optional-ID input has empty distinct output. |
| Mandatory identity relation | With the deliberately all-equal `Eq[TraceId]`, differently represented IDs match, lookup returns the first entity and distinct keeps only it. This proves actual ID Eq consumption through `Identified`, independently of the optional relation's representation-based Eq. It does not select all-equal business identity. |

The optional case preserves relative order and every absent-ID occurrence,
including the repeated reference `absent`. Absence supplies no matchable ID;
the assertion does not infer that separate unidentified entities are equal.
First-wins means that the later conflicting payload is discarded, not merged,
validated or resolved by a business rule. The executable empty cases are value
membership and optional-ID distinct; empty mandatory lookup and every other
empty operation are not separately tested.

The source implements value membership and ID lookup as scans using `Eq.eqv`.
ID distinct scans previously seen IDs and appends representatives in input
order. This is the explicitly qualified linear-Eq alternative: membership and
lookup take O(n) Eq comparisons, and distinct takes O(n²) in the worst case,
with O(n) retained IDs/output. These bounds assume bounded Eq/projection cost;
they follow from the source, not a performance benchmark. The implementation
uses no Hash, hashCode, Ordering, Set or Map, so this alternative needs no Eq/hash or
Eq/Ordering coherence claim. It establishes no custom Eq consumption by ordinary
Set/Map, sorted collection, grouping API or another collection family.

The property checks concern the supplied string Eq only. They do not prove
laws for arbitrary caller instances, mutable keys, either identity fixture or
the nested-array comparator. The API relies on lawful, stable Eq and relation
behavior for equivalence-based results. The fixtures declare a first-wins
operation contract; whether a particular consumer intends value membership,
ID matching, first-wins conflict handling or another policy remains a domain
question for that consumer. No unresolved producer question is listed in the
adopted return; no consumer domain policy is inferred from these examples.

C02/C03 now have this bounded qualification record for runtime validation and
independent review. Checklist acceptance remains with the owning Phase runtime.
C04/C05 consumer activation and Cozy/AI changes remain SLP-C3. This addition
changes no build, producer, consumer, live Session, history or repair count and
requires no new validation machinery or repetition of successful producer checks.

## SLP-C3 consumer preparation — 2026-11-21

C3 connects authored consumers and actual Cozy-generated Core API call sites to
one explicit `scalaStrictAuthoring` qualification. The producer delivery below
is observed and adopted; the new consumer compile/test/lint checks are **NOT
RUN** by this implementation. C04/C05 remain open for native validation and
independent acceptance.

### Adopted producer output and binary

Cozy delivery `P81-SLPC3-COZY-CORE-VALUE-MEMBERSHIP-20261121` identifies committed
source `dd1b826e6ec55ff07edace688b8e6383a9eb5c40`, reviewed tree
`1f5c58b6965c538731695f2a3c2753f89e8969d9`. Its public generation path is
`cozy.Cozy.main` with `modeler-scala
src/test/resources/modeler/workflow-semantic-outcome.cml --save <fresh-output>`.
The admitted fixture belongs to Cozy. The generation JVM must receive
`-Dcozy.generation.logical-action-membership=core-c3`; a version label or a
property on only the consumer JVM does not select this emitter mode. Output
without the property retains the legacy emitter byte for byte. An owning runtime
may reuse the adopted generated-output evidence instead of repeating successful
producer generation, retaining its exact bytes and custody.

The selected `LogicalActionCompiler` imports
`org.goldenport.util.TypeSafeCollections.*` and supplies a private `cats.Eq[String]`
using the standard String equality. Its three `typeSafe.containsEq` sites select
occurrences, accumulate required action values and diagnose duplicate requested
occurrences. Existing filter/fold order and duplicate policy remain intact. These
are value comparisons of Strings; no identity relation or business-ID wrapper is
introduced. Only this compiler and its directly required
`CompositeStateMachineActionProgramAbi` enter the consumer Compile scope.

| Adopted generated source | SHA-256 |
| --- | --- |
| `LogicalActionCompiler.scala` | `f3daf8291539c06318829386d01c9c31aa58a5db13fcc9b2051bc320e85bec3a` |
| `CompositeStateMachineActionProgramAbi.scala` | `d216a82c581cbba69dcb3b73fc3a2de0b2e663f9bdaea3e68653dcfd8defad19` |

The producer retained native Compile and focused TestOnly success: ten tests in
`CompositeStateMachineActionProgramSpec` and `Scala3StrictOutputGenerationSpec`,
none canceled. Its actual selected compiler/ABI compiled with the ten strict
Scala 3.3.8 options and selected Core source classes. Legacy output compiled with
old Core; selected C3 with old Core failed for missing TypeSafeCollections; an
`Int` membership argument failed with `Int =:= String`. Its class inspection
observed the Core API link. These are producer checks, not the new consumer
suite or a separate Scalafix result. The producer ran no separate consumer
runtime-behavior fixture or repository Scalafix operation.

The accepted Core source remains `e4972601207d4597f0ed3aa77fc0ed62fb286c75`,
tree `b431c2b2a11946761912472f591b9faaf2efa19e`. The owning runtime has now
reported clean-source `publishLocal` of the normal coordinate
`org.goldenport:goldenport-core_3:0.4.3-SNAPSHOT`, JAR SHA-256
`dbd6b315e4b61beb193394491102e3467974316f83c10a4326875643cc4dd327`.
This replaces the earlier ambient JAR lacking the API. Consumer activation still
requires the runtime to retain publication termination, dependency eviction and
resolved Compile/Test artifact identity. No source-classpath replacement,
alternate resolver or Core source repair is added to this consumer build.

### Explicit consumer selection

Default B2 behavior and the existing explicit B04 generation route remain
usable. Select C with literal `SM_WORKFLOW_STRICT_CORE_QUALIFICATION=true` or
module `qualification.strict_core: true`; the default is false and invalid
environment values fail. Select an actual adopted-output directory with
`SM_WORKFLOW_STRICT_CORE_GENERATED_SOURCE_DIR` or module
`qualification.core_generated_source_dir`. Relative paths resolve from the
strict module; environment selection takes precedence. Keep machine locations
in local configuration, never in tracked build inputs. For example:

```yaml
qualification:
  strict_core: true
  core_generated_source_dir: /absolute/path/to/adopted-generated-output
```

The directory may be the producer's retained selected evidence resource or fresh
public output from the admitted fixture/mode. `strictCoreGeneratedSources`
requires exactly one copy of each of the two named sources, verifies their
SHA-256 against the adopted delivery, and stages their unchanged bytes under
managed `core-c3` sources. Missing/duplicate sources, legacy mode or changed
bytes fail with a source-selection diagnostic. This is adoption of producer
output, with no handwritten replacement or modification of generated source.
A missing Core API then fails ordinary compilation, without fallback.

| Core selected | B04 generated selected | Additional selected sources |
| --- | --- | --- |
| false | false | None; existing seven authored B2 cases |
| false | true | Existing fresh CountryCode/RegionCode generation and three B04 cases |
| true | false | Authored `src/core/scala` consumer, actual compiler/ABI managed sources, four `CoreCollectionComparisonSpec` cases and three `GeneratedLogicalActionCompilerSpec` cases |
| true | true | Both preceding selections plus authored `src/generated-core/scala` consumer and two `GeneratedCoreCollectionComparisonSpec` cases |

Paths are relative to `scala-lint-rules/strict-authoring`. Each suite is selected
once. When B04 is enabled, retain its existing explicit qualified Cozy project
and repaired SimpleModeler selection; the C3 evidence directory does not replace
that generator. All selected sources use the existing ten strict options and
SemanticDB. Ordinary handwritten Compile/Test lint includes the authored
consumers and new specification; managed producer output stays excluded from
handwritten lint. No legacy generated-source convention waiver is added to
handwritten code.

### Consumer behavior and remaining checks

The authored consumer uses complete SourceId value membership and separately
selects optional TraceId identity for `containsId`, `findById` and `distinctById`.
Its specifications preserve first payload conflicts and each unidentified
occurrence, empty receivers and attributed element/sibling-ID rejection.
Existing workflow models and their identity contracts are unchanged.

The generated-value consumer uses actual CountryCode/RegionCode companion Eq
instances. Matching/unmatched/empty values and an incompatible query with
`Eq[Any]` in scope are specified. Its call sites remain authored, while the
adopted logical-action compiler supplies the genuinely generated API calls.

`GeneratedLogicalActionCompilerSpec` calls the actual produced compiler with its
actual produced ABI and the normal CNCF `ExecProgram` type. Three cases cover
exact membership and definition order with reversed requests and empty input,
stable unique required actions despite repeated occurrences, and duplicate plus
case-sensitive unknown selection diagnostics. It neither executes effects nor
reimplements the compiler. These cases exercise all three emitted Core call
sites; compilation also binds the real producer output to the resolved Core API.

Native compile/test/fix-check must now establish actual selected sources, ten
strict flags, resolved Core JAR identity, applicable handwritten lint and each
suite's single execution. Independent integrated A/B/C review and English final
acceptance remain with the owning runtime. No tests, lint, workflow effects,
publication or commit were executed by this semantic implementation. It changes
no accepted prior results, Sessions, repair counts or conditional M01–M04 choices.
