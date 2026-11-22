# Deterministic diagnostic repair: initial admission boundary

A producer supplies a diagnostic with its identity, source URI, candidate revision,
UTF-16 range and document version. Before source reaches a transport, the caller
must supply an exact owner-authorized URI selection and the current document text;
the adapter checks that selection against the diagnostic and validates its range.
A URI prefix alone does not authorize a file. The resulting patch retains the exact
snapshot, revision and diagnostic. The caller must recheck the current candidate
and authority at application and preserve normal admission, validation and rollback.

An explicitly registered rule must attest the diagnostic and proposed edit shape
against that snapshot. A title alone grants no authority; no production recipe is
registered here. Multiple admissible actions are ambiguous and yield no patch.
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
Actual recipes, runtime integration and installed-server qualification belong to
later steps.
