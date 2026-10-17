# Repository synchronization native execution

This finite adapter binds newly issued native conflict work, independent native
merge review and affected-file native merge repair. It consumes the existing Goal work
classification semantics and CNCF execution mapping contracts. It performs no
execution, provider IO, tool invocation, Submit, Advance or repository effect.

## R1 — Explicit paired semantic input and optional conflict

The optional Component parameter `sm-workflow.execution.repository-work` accepts
an ordinary structured object or a JSON string containing that object:

```json
{
  "schemaVersion": "sm-workflow.repository-execution-work.v1",
  "review": {
    "kind": "REVIEW",
    "complexity": "STANDARD",
    "domain": "PROGRAMMING",
    "footprint": "SMALL"
  },
  "repair": {
    "kind": "CODING",
    "complexity": "STANDARD",
    "domain": "PROGRAMMING",
    "footprint": "SMALL"
  },
  "conflict": {
    "kind": "CODING",
    "complexity": "STANDARD",
    "domain": "PROGRAMMING",
    "footprint": "SMALL"
  }
}
```

`schemaVersion`, `review`, `repair` and every classification field are explicit
and required when the key is present. Missing/null values, a different schema,
unsupported enum spelling, non-REVIEW review or non-CODING repair reject. The
additive `conflict` field is optional; omission remains `None`, including old
two-field definitions and constructors. When supplied it must be a complete
CODING classification. Explicit null, malformed fields/enums, another kind,
null Option or `Some(null)` reject. Repair never supplies a default conflict
classification. Structured and string forms consume the same shape, under the
unchanged Component key and schema version. No public ABI or persisted workflow
schema is added. The
existing [work classification](goal-phase-work-classification.md) decoder owns
the finite kind/complexity/domain/footprint vocabulary and reasoning rules.
Non-consumed diagnostic fields are inert; they confer no authority. Filenames,
file counts, mode, provider names and unused diagnostics never infer a class.
Classification supplies no concrete model, provider or effort selection.

## R2 — Semantic requirement and concrete ownership

`RepositorySyncNativeExecution.Classifications` contains the explicit review
and repair classifications and optional conflict classification.
`Work.Review` selects only review; `Work.Repair` selects only repair;
`Work.Conflict` selects only the explicitly supplied conflict member.
Its strict `requirementC(base)` preserves the
assigned capabilities, risk and reviewRequired distinction. A contradictory
review boundary rejects. The two classifications above produce:

| Assigned work | ReasoningClass | Reasoning | Review required |
| --- | --- | --- | --- |
| Native merge review | `ReviewStandard` | STANDARD | true |
| Native merge repair | `CodingProgrammingStandard` | STANDARD | false |
| Native conflict | `CodingProgrammingStandard` | STANDARD | false |

Explicit alternate semantic inputs retain the existing classifier rules, such
as large-footprint review producing `ReviewCritical` or complex engineering
repair producing `CodingEngineeringDeep`. None selects a concrete executor.

The same existing optional canonical `workflow.execution.profiles` and
`sm-workflow.execution.origin` pair used by Goal is passed to the Repository
declaration. Both must be present together or both absent. Null Options or
members reject. CNCF owns profile lookup, placement, independence and preference
admission through its existing resolver. A class missing from the mapping
rejects fresh issuance; there is no default profile or arbitrary fallback.
For a mapping whose coding profile matches the current descriptor, with neither
forceInline nor isolateContext set, the example repair or conflict selects INLINE with
OPTIONAL independence. Review selects DELEGATED with REQUIRED independence and
requires an observed separately scoped executor. Explicit isolation and forced
inline preferences retain CNCF's existing rules and refusal conditions.

## R3 — Optional configuration and shared declaration

The repository host declaration appends optional executionProfiles,
executionOrigin and executionClassifications fields, each defaulting to None.
Existing storage/target checks and business payload schemas remain the owners.
Ordinary ThreeWorkflow Component decoding resolves the repository key once and
passes it, with the canonical mapping and origin, into the same declaration
used by the ordinary Repository service and Goal-owned Repository child.
Host Definition, Start input, Goal business Plan, Split declaration and
persisted storage codecs do not gain execution configuration fields.

All three absent preserves the original unmapped interaction. Explicit
classifications with both mapping and origin absent produce logical semantic
classes with no disposition. A configured mapping/origin pair without explicit
repository classifications may be installed, but a fresh selected review or
repair issue rejects. A fresh mapped conflict specifically requires its explicit
conflict CODING classification; the old review/repair pair is insufficient.
With profiles/origin absent, omitted conflict leaves the original conflict
requirement unchanged even when the old pair is present. Explicit logical-only
conflict uses the same strict classifier without a disposition.
Existing pending work is read using its saved requirement;
later configuration is not a migration instruction.

## R4 — First issue and retained pending work

Only the genuine missing-issued-row branch binds work. Review's shared
`prepareC` is a pure projection that constructs the original unselected
interaction and Plan. Only `advanceC`'s missing-issued-row branch classifies
and maps that projected work, replaces Plan.work with the selected interaction,
then suspends and returns that exact selected Plan.work. Semantic Submit's
`closing.selectC` uses the same pure projection and replaces its work with the
actual saved issue before the existing evidence, CAS and completion admission;
it never selects from current configuration. Repair classifies and maps its
newly constructed interaction before suspension and returns exactly the saved
bound interaction. The request, native handle, presentation, context, result
type and completion route survive. Already selected work is refused by the
fresh-work adapter.

Conflict's owning `advanceC` binds only in `native.loadC`'s `None` branch:
capture actual input, run pure `prepareC`, bind `Work.Conflict`, then suspend the
original intent with the selected interaction and return that same interaction.
The `Some(saved)` branch, pure projection, Human source reconstruction, retained
completion and managed completion/afterCommit never resolve or reclassify from
current configuration. Human remains a separate decision with no issued work
context.

An existing issued row, saved review, retained completion, semantic Submit and
readback do not classify or resolve again. Pending Advance returns the original
class/disposition/public issued context even after a valid new configuration
changes classification, origin and mapping or omits the old class. The host
retains the original execution Session and restores it against the actual
public issued context using the [native host execution](native-host-execution.md)
bridge. It never prepares pending work using the replacement configuration.
Missing original host artifacts remain a gap.

## R5 — Evidence, independent review and effects

Classification and mapping are operational input, not authenticated provider or
reviewer evidence, permission, Human consent or effect authority. Host
operational completion must supply the existing generic execution observation
matching the actual saved disposition. Missing or crossed observations reject.
Independent review additionally requires its existing separately prepared host
evidence and native result/revision/CAS admission. The normal Submit result
controls acceptance; operational COMPLETED alone does not.

Review Submit saves the semantic decision. Conflict and repair Submit save only
their assigned proposal. Missing execution observation or an observation crossing
the original saved origin refuses before native progression or file/SBT effects.
Separate explicit Advance applies/stages the
proposal, freshly validates the changed candidate through ordinary typed UoW
SBT and issues a new independently scoped review with its own fresh binding.
Original issue/completion records remain readable. Existing affected-file
capture, unknown-effect/no-replay behavior, Human and convergence authority
remain with their current owners. Goal child routing retains the child's
original submission session and enclosing Goal owner.

## Executable specifications

All observations and concrete profiles in these examples are specification-owned;
they establish no actual installed provider or genuine separate chat acceptance.

| Example | Executable behavior | Rules |
| --- | --- | --- |
| E1 | `RepositorySyncNativeExecutionSpec`: absent/structured/string input, inert diagnostics, malformed fields/enums/null options | R1–R3 |
| E2 | Same suite: explicit semantic review/repair classes and alternate classifier rules preserve assignment | R1–R2 |
| E3 | Same suite: canonical INLINE repair and independent DELEGATED review, mapping identity/preferences and refusal | R2–R3 |
| E4 | Same suite: omitted and logical-only configuration, selected-work rebinding refusal | R2,R4–R5 |
| E5 | `ThreeWorkflowRepositoryOperationSpec`, name includes `mapped repository execution`: real Diverged Git/SQLite/ordinary SBT review; close/reopen with changed configuration; original Session restoration; independent evidence and normal Submit followed by separate Advance | R1–R5 |
| E6 | `RepositorySyncNativeMergeRepairContinuationSpec`, name includes `mapped repository execution`: real conflict and original SBT; independently evidenced mapped review; actual mapped repair; observation refusal; proposal-only Submit; separate application/fresh SBT/new mapped review with retained original records | R1–R5 |
| E7 | `RepositorySyncNativeExecutionSpec`: optional conflict object/string and old pair, malformed/null/wrong-kind refusal, strict CODING derivation, legacy unchanged conflict, mapped missing-input/class refusal, INLINE/isolated DELEGATED and rebinding refusal | R1–R5 |
| E8 | Existing Human-preparation E6 in `ThreeWorkflowRepositoryOperationSpec`, two additional names include `mapped conflict execution`: real resolved and decision-needed conflict, original retained Session and pending redisplay after changed configuration omitting its class, observation refusal, normal proposal Submit, separate effects or evidenced Human, fresh independent ReviewCritical Session and normal convergence | R1–R5 |

These three Repository work kinds have fresh binding. The original three
conflict/Human directions and their assertions remain alongside the two mapped
variants. Human handling and other unbound Repository operations retain their
original obligations; Split has its separate explicit input contract. Each fresh
review uses its own later mapping/origin and retained Session, completed observation
and separately prepared independent host review evidence; conflict execution or
model strings cannot establish reviewer provenance. The examples make no successful-run or Phase
acceptance claim; installed host/provider routing, replacement/fallback, actual
independence, all-family work and bounded CBD integration remain Phase work.
