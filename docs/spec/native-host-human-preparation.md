# Native host Human preparation

The normal Human output carries a selected context from its actual owning
Decision. `org.simplemodeling.textus.smworkflow.host.NativeHostHumanPreparation`
uses that context and an explicitly supplied native Human answer to prepare one
purpose-specific record input through the existing
`sm-workflow.native-host-evidence.v7` codec. The host obtains the genuine Human
direction and owns its provenance and provisioning. Preparation supplies no
consent, independent review or workflow authority.

## Issued context and passive API

Goal returns `human.hostContext`. Repository returns `human.hostContext`; Goal
forwards its repository child's context unchanged in `repositoryHuman.hostContext`.
Split returns `scopeHuman.hostContext` or `reasoningHuman.hostContext` for the
actual selected question. Retained pending display uses the same issued question
and current owning core. The optional field is last in each nested business DTO;
normal Start, Submit, answer types and top-level Advance outputs keep their
existing signatures and routing.

`WorkflowHostHumanContext(purpose, componentInstance, question)` contains the
selected structured Record question: actual handle, run, continuation slot,
required capability, operation, input/result type, completion operation and
native Goal assignment, Repository conflict, or Split Scope/Reasoning source.
It copies no expected revision, model snapshot, presentation or grant body.

The public methods return `Consequence[Prepared]`, where
`Prepared(recordIdentity: String, record: Record)` is a passive current-v7
record input containing `nativeHostEvidencePayload`:

- `goalC(context, GoalPhaseOrderedDecisionAnswer, reference, execution, submittingParticipant, issuer, occurredAt)`
- `repositoryC(context, RepositorySyncHumanConflictAnswer, reference, execution, submittingParticipant, issuer, occurredAt)`
- `splitC(context, SplitPhaseHumanDirectionAnswer, reference, execution, submittingParticipant, issuer, occurredAt)`

The caller supplies the actual `WorkflowArtifactReference`,
`WorkflowExecutionEvidence.workerIdentity`, exact submitting participant, issuer
and `java.time.Instant`. No identity or occurrence comes from defaults,
environment, configuration, clock, session-ID reconstruction or an extra store
read. The finite [host evidence tool](native-host-evidence-tool.md) adapts ordinary
saved command JSON and explicit metadata files to these APIs.

## Rules

R1: Only the actual owning core and selected typed Decision issue context.
Missing owner, question, Decision, typed input/result, actual handle, run or slot
fails. Goal, Repository, Split Scope and Split Reasoning must use their matching
native input and answer codecs. No placeholder source or identity is minted.

R2: Shared production question/source/answer helpers preserve the existing
purpose-specific v7 binding. Goal selects its supplied choice. Repository
ProvideResolution selects literal conflict identities and text; other choices
ignore resolutions. Split ProvideResolution selects its typed resolution
reference; Defer and Cancel ignore resolution annotations. Split attestation
adds no worker to its existing question/answer binding.

R3: Preparation requires the supplied actual answer, result reference, worker,
submitting participant, issuer and occurrence. Ordinary generated readers and
native codecs own typed payload meaning. The host establishes genuine Human
origin separately; encoding and an issuer string do not establish that origin.
No reviewer, answer, participant or next action is chosen by preparation.

R4: Preparation writes no record and performs no Workflow call, clock/configuration
read, enrollment, grant or progression. The trusted caller saves standard bootstrap
input in a configuration-selected file; existing CNCF StartupImport owns mutable
save/create provisioning. A returned row is neither a committed receipt nor an
immutable or atomic import guarantee. Specifications explicitly use existing
managed Create permissions for fixture input only.

R5: Existing configured Reader, current issuer, selected native binding, managed
current-call read permission, participant admission and native completion/CAS
remain the admission boundary. Different owner, submitter, worker, reference,
choice, slot, source, issuer or future occurrence fails. Human preparation supplies
no review, Step/Phase acceptance, Git effect or Split Start authority. Normal
Submit acknowledges the answer; subsequent progression still requires explicit
Advance.

R6: Only consumed selected fields affect meaning. Unused rationale, resolution
annotations, old expected revision/model/presentation and grant bodies are inert.
Missing or mistyped consumed fields and unsupported or crossed purpose/type fail
without compatibility fallback or consent renewal. Historical records retain
their dates and meaning.

R7: Keys present in the supplied question Record, or in the finite question JSON
passed directly to `NativeHostHumanQuestionWire.normalize`, accept the existing
framework's declaration/camel, snake and kebab spellings only at finite declared
paths. Selection takes the first present key in that order before decoding: a
present canonical key wins over a differing alias, and a present null, wrongly
typed or blank canonical value is never rescued by a valid alias.

The existing general `RecordDecoder` constructs objects with `Record.create`,
which drops JSON null fields before this API receives a Record. Preparation
cannot recover that removed presence. An actually absent canonical key may
therefore resolve a declared alias; a missing consumed required value without
an alias still fails. This contract makes no raw-text null-preserving parsing
promise and changes neither the shared decoder nor strict native decoding.

The question schema covers runId, continuationId,
requiredOperation, inputType, resultType, handle, operation, completionOperation
and source. Handle selects componentIdentity, workflowIdentity, workflowRevision,
instanceIdentity and protocolVersion. Goal source selects requestingResult and
assignment; its assignment selects instanceIdentity, sourceRevision, kind,
stepIdentity, generation, selectedPlan, originalBase, candidate and checksReceipt.
Repository source selects requestingResult and conflict; its conflict selects
instanceIdentity, conflictRevision, localTip, remoteTip and conflicts. Split
source selects kind, context, checkpoint, scopeCheckpoint, result and work;
work selects continuationId, source, authority, demand and items. One-word
reference/operation/snapshot fields need no aliases. Traversal never renames
arbitrary conflict data, items, values or annotation objects. String values,
including literal paths and resolutions, remain exact. Unknown annotations
remain inert; unchanged strict native scopes project the canonical v7 binding.
A structurally valid different supplied identity can be prepared but fails
native observation against the original target. Wire conversion establishes
no Human provenance or authentication rule.

## Executable examples

`NativeHostHumanPreparationSpec` labels fixture inputs with canonical spec/example/
rule/phase metadata and adjacent Given/When/Then. All examples use should matchers.
The public Goal, Repository and Direct Operation consumers exercise normal
Submit acknowledgement, unchanged missing-record refusal and explicit Advance
with the existing native state/effect assertions.

| Example | Rules | Observable obligation |
| --- | --- | --- |
| E1 | R1,R2,R3 | Actual Goal fixture Decision projected through production output/core and strict generated JSON intake prepares a row matching the native owner/slot/source/submitter/reference/worker/choice |
| E2 | R1,R2,R3 | Repository selected conflict crosses strict nested Record intake; literal newline path and resolution text survive exactly |
| E3 | R1,R2,R3 | Passive Scope and Reasoning source fixtures prepare distinct targets; complete public Human outputs come from the managed Direct consumers |
| E4 | R2,R3,R6 | All declared Goal Resume/Defer/Cancel, Repository ProvideResolution/Defer/Cancel and Split ProvideResolution/Defer/Cancel retain native meaning and ignore unused annotations |
| E5 | R1,R3,R6 | Missing identities/types/metadata and mistyped selected fields fail; unrelated copied annotations remain inert |
| E6 | R4,R5 | Preparation alone creates no row; fixture managed Create permits only live current-call reading, exact-scope Submit and separate Advance; foreign facts and effect aliases fail |
| E7 | R1,R2,R3,R5,R6,R7 | Goal, Repository, Split Scope and Split Reasoning canonical and actual framework snake/kebab questions retain identical native rows; scenario-local recursive `Record.createFull` construction proves exact canonical `Field` keys with null `value.single` and serialized JSON nulls remain supplied beside valid aliases, and direct finite normalization retains those nulls; malformed nested values, crossed codecs and changed-target observation preserve strict admission and literal values |

E7 uses the null-preserving conversion only to construct malformed supplied
question Records. Presence is inspected through `Record.fields`: `keySet` and
`asMap` omit explicit-null fields even when `createFull` retained them. The
serialized `Json.Null`, direct finite normalizer and actual preparation refusal
remain required; a field-presence assertion alone proves no preparation behavior.
Normal `record()`/`wire()` input and actual framework alias
positive cases retain the ordinary decoder. Per-purpose and per-field failure
clues identify each refusal; the fixture supplies no live Human output or
provenance.

These are specification obligations, not an execution report or acceptance claim.
Distinct typed Split Start input, native Step/Phase/Git contexts and provisioning,
and genuine independently attributable installed tasks for all three families
remain mandatory later D2 work. See [normal host ownership](normal-three-workflow-host.md)
and [native installation](native-client-installation.md).
