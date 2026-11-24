# Deterministic diagnostic repair: initial admission boundary

A producer supplies a diagnostic with its identity, source URI, candidate revision,
UTF-16 range and document version. Before source reaches a transport, the caller
must supply an exact owner-authorized URI selection and the current document text;
the adapter checks that selection against the diagnostic and validates its range.
A URI prefix alone does not authorize a file. The resulting patch retains the exact
snapshot, revision and diagnostic. The caller must recheck the current candidate
and authority at application and preserve normal admission, validation and rollback.

An explicitly registered rule must attest the diagnostic and proposed edit shape
against that snapshot. A title alone grants no authority. Multiple admissible
actions are ambiguous and yield no patch.
Edit count, action count, replacement size, UTF-16 positions and overlapping edits
are bounded. Unsupported command, disabled or unresolved alternatives are ignored;
malformed protocol responses are distinct from no usable action. A fixture rule
in the executable specification demonstrates the boundary only.

The Scala adapter negotiates literal code actions, versioned document changes and
UTF-16 over an injected transport. It accepts one matching TextDocumentEdit or a
single-document legacy change bound to the opened snapshot. It rejects resource
operations, mixed representations, wrong document versions, annotations, snippets,
commands and unresolved actions. Host transport owns deadlines and frame-size
limits. The adapter does not launch or install Metals, import a build, apply edits,
or authorize a process. Disabled, unavailable and unsupported services return a
fallback outcome. The fixture is not evidence of an installed Metals server.
Other language adapters are deferred. See the [Metals editor integration guide](https://scalameta.org/metals/docs/integrations/new-editor/)
and [LSP 3.17 specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/).

`validation.metals` in `project.yaml` is an optional mapping; unknown and null
fields are invalid. `enabled` defaults to true; `timeoutMillis` defaults to 10000
(1–60000), `maxEdits` to 32 (1–256), `maxActions` to 64 (1–256), and
`maxReplacementChars` to 65536 (0–1048576). Optional `serverIdentity` is a logical
registration name, not a path or execution authority. Enabling policy grants no
execution or workspace authority. An explicit `enabled: false` disables the route.
The ordinary Phase repair route consumes this policy as described below. Host
registration remains separate from project configuration.

## Fixed Metals process and ordinary repair

After the existing native compiler recipe has declined a failed Compile or
TestCompile candidate, ordinary repair can request a Metals code action. It
authenticates the retained typed failed check, one compiler diagnostic, the
selected Step's literal source ownership, staged UTF-8 blob, regular file mode,
repository identity and identical worktree bytes through `compilerMaterialC`.
Only the supported diagnostic shape proceeds to the provider. Existing compiler
repair, rollback, retry accounting, candidate inspection and selected validation
continue through their normal paths.

The production rule selects the Scala action introduced in
[Metals 1.5.3](https://scalameta.org/metals/blog/2025/05/13/strontium/#remove-invalid-imports-code-action)
and implemented by [RemoveInvalidImportQuickFix](https://github.com/scalameta/metals/pull/7416).
The admitted subset is one unindented, simple qualified import, such as
`import scala.DoesNotExist`, followed immediately by LF. A complete Scala 2
diagnostic must identify `object DoesNotExist is not a member of package scala`,
echo that exact line and point its caret at either the package owner start or
the missing final name start. The original diagnostic range must match that
observed caret; the LSP range separately covers the missing final name. Only blank,
simple package, simple import and closed header comments can precede it. Aliases, selector groups,
wildcards, comments, CRLF and other source shapes use ordinary repair. The action
must have kind `quickfix`, title `Remove invalid import of 'DoesNotExist'`, one
edit deleting exactly that import line including LF, and no command, unresolved
data, annotation, resource operation or other edit. Scala 3.3.8 also admits the literal missing qualifier form
`import MissingPackage.Member` only when the E006 header, URI, source echo,
caret span and missing-name message authenticate that exact qualifier. Its
normalized Metals selection covers the missing qualifier; the same exact
whole-line deletion and candidate-bound transaction apply. Competing eligible actions
yield no proposal. The diagnostic, title and edit are checked together; the
title alone is insufficient. Java-only missing-override actions are excluded.

`serverIdentity: scala-metals-1-5-3` selects the supported logical provider.
The host must separately register capability `sm-workflow-metals` for this
Component with one fixed executable recipe, the shipped
`sm-workflow-metals-bridge.py` companion itself as the absolute `executable`,
with `fixedArguments: []`, `argumentTemplates: [[]]`, no caller working
directory, input files, resource trees or output artifacts. The companion's
explicit `#!/usr/bin/python3` shebang and installed mode 0755 are part of this
fixed payload contract. Registering a generic Python interpreter with the
companion as an argument is rejected by CNCF's direct-effect policy. The host
fixes `SM_WORKFLOW_METALS_SERVER` to an absolute executable wrapper for its
chosen provider. The wrapper receives no caller arguments. The companion is
included in the component's `META-INF/sm-workflow` source distribution; this does
not install it or change a shared CLI. Host registration and deployment are
explicit prerequisites, and no direct process fallback bypasses admission.

The component submits one finite JSON input through protected CNCF
`process_exec`. Input and stdout are limited to 1 MiB each, stderr to 64 KiB and
the WorkArea to 4 MiB. The whole LSP exchange shares `timeoutMillis`; CNCF adds a
bounded launch and termination allowance. The companion starts the host-fixed
provider with an isolated temporary directory inside the CNCF WorkArea. It writes
only the selected source as `Main.scala`, opens that URI, negotiates UTF-16 and
literal code actions, and requests only quick fixes. It never copies a build or
imports a repository. Server requests, including `workspace/applyEdit` and command
execution, receive an unsupported-operation response. Frame size, cumulative
provider output, frame count and JSON fields are bounded. Provider edits must
name the isolated opened URI before translation; an edit naming the original
candidate URI directly is incompatible. Scala then applies its exact rule and
native source admission to the translated proposal.

The companion closes the document, sends shutdown and exit within the same
deadline, terminates and waits for the provider process group, closes pipes and
removes its isolated directory. CNCF retains ownership of the outer process
lifecycle and WorkArea. A missing provider, deadline, incompatible encoding or
framing, unsupported action or disabled configuration produces no patch.
Admission rejection is distinct from an uncertain process effect. Native intent
and result receipts retain the candidate/check relationship, typed outcome and
bounded actual captures. An intent without a result, or unconfirmed termination,
pauses the original attempt and cannot redispatch it.

An eligible proposal passes the existing native file update, candidate admission
and revalidation. Replay checks the same pure allowlisted transformation against
the original authenticated diagnostic and bytes without contacting the provider.
Failed revalidation uses the existing exact rollback and ordinary repair route.
`DeterministicRepairSpec` contains controlled-LSP subprocess cases using the
production companion and CNCF admission, including normal lifecycle, timeout,
encoding and framing failures, direct candidate URI, unowned edits, commands,
ambiguity and unavailable provider. These fixtures do not establish a deployed
Metals build connection; qualification against real projects is post-release.

## Native terminal LF proposal

The `terminalLf` recipe proposes removal of extra terminal LF characters in a
handwritten, owned Scala document, preserving one final LF and every preceding
character. Nonempty content, no NUL, a matching range and an exact revision and
version are required. This narrowly follows the byte-level correction described
in [the normal typed Git workflow](normal-typed-git-workflow.md); application
still requires its own byte, file-mode, ownership and validation checks.

`phase-review` and `terminal-lf` are internal normalized identities, not external
compiler or Git diagnostic codes. A trusted caller must project an actual review
finding, supply its retained evidence reference, determine handwritten ownership,
and bind the exact range to the snapshot. The data constructor does not verify
those claims. The returned action separately records the native recipe and evidence
reference and passes the existing admission boundary; it neither applies a patch
nor proves a check succeeded. No runtime producer is wired to this projection yet.
## Scala 3 Circe error member

`circeErrorMessage` proposes replacing only the member `message` with
`getMessage` on the exact line `.fold(error => fail(error.message), identity)`.
The trusted caller must bind a Scala 3 `E008`, its exact diagnostic text and
`io.circe.Error` receiver type to the same owned handwritten snapshot and member
range, together with the actual compiler receipt. The implementation rejects
different line shapes and a line inside a comment or string. Its conservative
lexical scan can reject valid Scala containing uncertain character literals.
The diagnostic retains the compiler identity; separate native provenance records
the recipe, compiler major version, receiver type and evidence reference. A caller
must authenticate the receipt and type: constructing the projection is not proof.

The retained Phase 6.11 evidence contains the actual [E008 diagnostic](../../target/sm-workflow/runtime-phase-6.11-restart-20261106/target/sm-workflow/tasks/phase-6.11-restart-20261106-01a1aee5/298-b5-advance-after-artifact296/response.json)
and the [authored replacement](../../target/sm-workflow/runtime-phase-6.11-restart-20261106/target/sm-workflow/tasks/phase-6.11-restart-20261106-01a1aee5/302-b5-circe-diagnostic-compile-repair/result.json).
The [subsequent run](../../target/sm-workflow/runtime-phase-6.11-restart-20261106/target/sm-workflow/tasks/phase-6.11-restart-20261106-01a1aee5/309-b5-advance-after-artifact307/response.json)
passed compilation and ran 11 tests, with 10 successes and one separate fixture
failure. A [later candidate](../../target/sm-workflow/runtime-phase-6.11-restart-20261106/target/sm-workflow/tasks/phase-6.11-restart-20261106-01a1aee5/314-b5-advance-after-artifact312/response.json)
also included another repair and passed all 11 tests. These observations do not
establish live execution of this recipe.

### Ordered Phase Step repair

After the normal repair reservation, the Phase attempts the initial compiler
recipe before issuing semantic repair. It requires a completed typed failing
SBT receipt, both complete output streams, a single matching diagnostic and
compilation failure, and exact agreement between the compiler's echoed line,
the staged UTF-8 blob and the same file in the worktree. The file must be an
owned regular Scala source in the selected Step, with the checkout, index and
validation bound to the retained candidate. Missing evidence leaves the normal
RepairStep available.

The runtime records the file-update intent before invoking the managed File
compare-and-set and records the returned outcome separately. An applied edit
goes through normal candidate inspection and the selected validation chain. If
that revalidation formally fails, rollback checks that the single changed blob
is precisely the applied edit. It restores the original bytes, stages that file
through its selected Git program and verifies the original index. The failed
diagnostic and validation remain available to the normal RepairStep or Human
decision. A missing effect result or a rollback conflict retains the actual
state for resolution. The runtime makes at most one automatic compiler
correction attempt per Step in this initial route and preserves the normal
repair budget and independent review.

The Phase route supports the narrow Circe correction above and the configured
Metals route. The Scala terminal-LF proposal and formatter/Scalafix actions have
separate producer and qualification requirements. Installed tools, deployed
project behavior and operational savings are checked after release.

## Source-local nullable boundary evidence

The baseline standalone Scalafix profile includes `NullableBoundarySemantics` and
`NullableBoundarySuppression`. The former recognizes a deliberately narrow
method boundary: one direct method-body `if` testing a stable parameter against
`null`, or a semantically resolved `java.util.Objects.nonNull(parameter)`, an
explicit `T | Null` parameter and a null branch that throws. The marker must be
the immediately preceding source line, for example:

```scala
// nullable-boundary: rule=NullableBoundarySemantics target=value boundary=interop normalization=reject-null justification=java-api
def length(value: String | Null): Int = if (value == null) throw new IllegalArgumentException() else value.length
```

`justification` is a nonempty author-supplied token; no tool creates or assesses
its truth. A recognized shape emits `VerifiedBoundary` at Info severity. This is
only evidence of the bounded syntax and resolved parameter/predicate, not proof
that the producer is nullable, the reason is correct, or the behavior meets the
contract. Unsupported, invalid, stale and misplaced cases emit warnings for
judgment. Other normalization shapes and nullable types are unresolved, not
accepted. The suppression audit reports a rule-specific suppression as a warning;
it uses the same Scalafix engine, which still owns its ordinary suppression
mechanism. A disabled rule, global suppression, missing SemanticDB or an absent
`VerifiedBoundary` record is not affirmative evidence. Workflow exception admission
must fail closed on absent, stale or unbound evidence; no runtime exception
admission is implemented by this step.

The existing changed-scope reporter emits a redacted structured diagnostic record
with rule, severity, exact one-based start and end positions, disposition, unknown
age and Scalafix provenance. It retains the previous text fields and blocking
policy. It does not itself establish historical age or prove that an exception is
admissible.

### Local qualification boundary

`DeterministicRepairSpec` defines disposable native CLI scenarios using Scala
3.3.8 and the current test classpath's Circe error class location. Both executed
typed SBT Test and observed an initial E008 failure. The pass fixture adopted
`getMessage`, revalidated Test with exit 0 and reached ReviewStep. In the rollback
fixture the same correction was followed by an independent ProductCheck failure
(exit 1), restoration of the original bytes and ordinary RepairStep. Retained
typed stdout and stderr evidence is under
`target/sm-workflow/work/native-cli/circe-native-pass-5034042719722502189/logs`
and `target/sm-workflow/work/native-cli/circe-native-rollback-4631855445124332056/logs`
(`10.stdout.json`, `10.stderr`, and `11.stdout.json`). Child SBT elapsed times
were 3290/2434 ms for pass and 3248/2443 ms for rollback; they describe only
these fixtures. Selected validation 1022 on tree
`7034b3bdf01efb2b48720dc54a5b7c0813f0cf3c` passed Compile, TestOnly (8 tests),
and FixCheck.

The standalone terminal-LF proposal test does not establish runtime application
of that action. A fixed, admitted provider seam is implemented above; the controlled
transport fixture does not establish actual deployed server behavior. The adopted
Plan defers qualification against deployed tools and real Scala 2.12/3 projects,
and production latency and AI-avoidance measurements. No Phase completion is
claimed by this local qualification.
