# Native host RepositoryEffect preparation

The owning host supplies its current selected ProfileSelection Record, actual
component instance and explicit physical repository target. Public passive
preparation encodes one effect input using the unchanged native scope. It does
not create a Workflow instance, read private Workflow storage, certify operator
origin or authorize a Git effect. The actual responsible operator independently
authorizes the selected effect; trusted provisioning and native admission remain
separate obligations.

## Public API and selected inputs

`org.simplemodeling.textus.smworkflow.host.NativeHostRepositoryEffectPreparation`
provides:

```scala
final case class Target(root: String, localBranch: String, remote: String,
  remoteBranch: String, endpoint: String)
final case class Prepared(recordIdentity: String, record: Record)
def repositoryC(componentInstance: String, selectionRow: Record, target: Target,
  effect: String, reference: WorkflowArtifactReference, participant: String,
  issuer: String, occurredAt: Instant): Consequence[Prepared]
```

`selectionRow.selectionPayload` must be an actual String encoded by the current
`ProfileSelectionJsonV1` storage codec. The selected operation is exactly
`RepositorySyncService.startRepositorySync`; `selectedInput` must have type
`RepositorySyncStartInput`. The existing native business decoder consumes its six
String fields: `repositoryIdentity`, `localBranch`, `trackingRemote`,
`trackingBranch`, `remoteEndpointIdentity` and `syncMode`. Its semantics remain
unchanged. Selection ID, idempotency key and selected participant supply the
original StartContext identity. No session or reconstructed execution is needed.

Physical `Target` fields come from the actual selected host Git configuration.
They are explicit nonblank exact Strings. `root` must be an absolute, already
normalized path; preparation does not resolve, normalize, discover or read it.
The current native host and Git owners retain business/physical target and
program correlation checks. Supplying an arbitrary target does not admit it.

The current component owner, evidence reference identity/revision, operator
participant, issuer and historical occurrence are explicit. No identity, clock,
configuration, principal or permission is inferred. Only exact case-sensitive
`CommitPending`, `Push` or `FinalFetch` is supported, one per call. Preparation
preserves current codec semantics and does not require `ExplicitHuman` origin,
re-admit a Workflow definition, certify a recommendation or compare full
selection bodies. Origin and extra annotations supply no consent.

## Unchanged scope and admission

Native `ThreeWorkflowHostEvidencePorts.repositoryEffectBindingC` and passive
preparation share `NativeHostRepositoryEffectScope.bindingFromInputsC`. The
projection retains exactly:

```text
componentInstance
scope.kind = repository-effect
scope.selection, invocation, participant, repository, endpointIdentity, mode, effect
scope.target.root, localBranch, remote, remoteBranch, endpoint
```

The separate lookup is `key(selectionId, idempotencyKey, exactEffect)` using the
existing JSON-array key function. The existing v7 `RepositoryEffect` purpose,
record identity and `nativeHostEvidencePayload` codec remain unchanged. Returned
`Prepared.record` contains only that payload; `recordIdentity` is separate.
No native handle, revision, private storage context, commit certificate or
proposal attestation is introduced.

Unlike Split Start and Step/Phase metadata, current RepositoryEffect observation
requires the evidence metadata participant to equal the selected scope
participant. A nonblank different operator may be prepared but the unchanged
native observer must refuse it. Encoding authenticates neither that operator nor
the issuer. Current configured Reader, native principal, managed read grants,
issuer, purpose, lookup, exact target and nonfuture occurrence retain their
respective admission responsibilities. Accepted review and Start give no Git
grant. Runtime-owned programs, effect grants, process limits, intent ownership,
CAS, commit, non-force push and final Git observation remain authoritative.

Preparation writes no row or file, invokes no Workflow/Git/process and performs
no automatic Start, Advance, retry or recovery. Existing trusted StartupImport
save/create or managed Create separately provisions input. It remains mutable;
there is no immutable or atomic import promise. A row alone supplies no read,
access or execution grant.

## Normal typed preparation Operation

Use `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryEffect` with one named `input` of generated type `WorkflowRepositoryEffectPreparationInput`. Select actual host values and absolute local file: URI references; the following values illustrate the concrete schema:

```sh
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryEffect \
  --input '{"componentInstance":"actual-component-instance","selection":"file:///absolute/host/selection.json","target":"file:///absolute/host/target.json","effect":"Push","reference":"file:///absolute/host/effect-reference.json","participant":"actual-operator","issuer":"configured-host","occurredAt":"2026-10-14T00:00:00Z","collection":"entity:smworkflow.cli.host_evidence"}' --json
```

Read the three selected UTF-8 records in order: selection, physical target and evidence reference. The target has the existing five consumed String fields root, localBranch, remote, remoteBranch and endpoint. Select exactly CommitPending, Push or FinalFetch. The unchanged NativeHostRepositoryEffectPreparation.repositoryC retains owner/participant/scope/identity and emits one row without executing Git. Generated reference String conversions remain available.

All fields above are required except participant is optional in the evidence DTO and governed by its selected kind. Metadata is exact, nonempty, untrimmed and control-free; collection addresses use literal `entity:` and three labels matching `[A-Za-z][A-Za-z0-9_]*`. Invalid metadata fails before acquisition where the existing read order guarantees it. Missing or invalid typed data and unavailable resources return ordinary Consequence/command errors without successful importer output. Normal CLI Help describes the generated service.

The passive helper `NativeHostRepositoryEffectInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists. See [normal preparation Operations](normal-host-preparation-operations.md).

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

## Rules and executable examples

R1: Consume actual current selected ProfileSelection repository input, actual
component owner and explicit physical host target without reconstructing a
Workflow execution or reading private control storage.

R2: Preserve current codecs and exact native effect scope/lookup; re-admit no
Workflow definition, origin, recommendation or body/commit certificate.

R3: Require explicit reference, operator, issuer, historical occurrence and one
exact effect. Preserve native RepositoryEffect metadata participant equality;
encoding authenticates no provenance.

R4: Keep passive input separate from trusted provisioning, current
Reader/principal/read permissions and independent effect/process/CAS/intent
authority. Review and Start supply no Git grant.

R5: Read exactly three explicit UTF-8 file URI references with nine typed fields and emit one
strict dotted EntityCollection importer row. Invalid input fails; sole help
performs no reads.

R6: Retain command-primary/light-client, original scope/history, existing review
owners and full Phase obligations. Fixture input proves no genuine consent,
installed-task completion or provider acceptance.

| Example | Rules | Observable contract |
| --- | --- | --- |
| E1 | R1,R2,R3 | All three exact effects match original literal scope and native port, lookup and metadata |
| E2 | R1,R2,R3,R6 | Malformed consumed inputs refuse; decoder semantics and inert annotations stay current |
| E3 | R2,R3,R4,R6 | Crossed scope, lookup, issuer, purpose, future time and metadata participant refuse |
| E4 | R1,R4,R6 | Separate managed Create enables exact current reads; missing grants/core/target and ended runtime refuse |
| E5 | R2,R3,R4,R6 | Actual convergence admission requires separately provisioned authority for each exact effect |
| E6 | R1,R2,R3,R4,R6 | Start without effects succeeds, Advance refuses, and a NEW separately provisioned execution actually converges |
| T1 | R1,R2,R3,R5 | Three selected files emit the same one-effect importer row as public preparation |
| T2 | R1,R3,R5,R6 | Invalid explicit arguments and file/typed inputs produce no successful JSON |
| T3 | R2,R4,R5,R6 | Exactly three ordered reads, dotted grammar, current reference conversion and no-read help |
| T4 | R1,R3,R5,R6 | Finite compiled launcher exports matching JSON, invalid effect exits two, help exits zero | Normal CncfMain command cases in NativeHostPreparationOperationSpec P4 preserve selected payloads and refuse invalid metadata |

`NativeHostRepositoryEffectPreparationSpec`, `NativeHostRepositoryEffectInputSpec`
and the actual managed `ThreeWorkflowRepositoryOperationSpec` E6 episode bind this
contract using canonical afterWord metadata, adjacent Given/When/Then and should
assertions. The shared managed repository fixture now consumes public preparation
for ordinary repository and Goal repository-child examples, preserving native
effects and existing assertions. These mappings record obligations, not executed
validation or genuine operator/provider provenance.

Under the [adopted security profile](../phase/phase-6-security-review-profile.md),
selected input/target, separate authentication/consent/effect authority,
least-authority provisioning and explicit file/process attribution require
relevant evidence through existing Phase 6.1/6.2/6.3 owners. Imported PLANNED
producer documents establish no availability or PASS. Missing required admission
remains Admission Gap/Unknown; no new scanner or review stage is introduced.
Genuinely attributable host/operator/provider evidence, all-three installed
command tasks, modes/provider/Boundary requirements and full original Step/Phase
closure remain mandatory. PHASE-6.3 retains primary aggregate full-suite ownership.

The normal public route and runtime acquisition cases are mapped to `NativeHostPreparationOperationSpec` P1–P4 in [normal preparation Operations](normal-host-preparation-operations.md). Retained E/T IDs in the InputSpec continue to cover payload, alias, metadata and reader failures; former subprocess T4 (or evidence T6) is owned by P4.
