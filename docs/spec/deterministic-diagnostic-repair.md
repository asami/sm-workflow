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
Runtime integration and installed-server qualification belong to later steps.

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
establish live execution of this recipe. Metals, formatter and Scalafix rewrite
actions remain unqualified; D5 owns runtime application and D6 its qualification.

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
