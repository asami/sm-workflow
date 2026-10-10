# Workflow definition reference comparison

Status: earlier corrected Phase 5 definition path had focused producer/consumer
verification on 2026-10-08. The subsequent R4 source/spec correction is UNVERIFIED;
the historical results do not accept the changed inputs. Integrated Phase 5
acceptance and read-only review remain open.

## R1 — Separately admitted references, not content certificates

The native Workflow resolves the execution definition's identity/revision.
Neither this reference nor the runtime declaration is a proof of unchanged
content. Source lines/resources, generator identity/revision, ABI provenance,
JSON formatting, hashes and object identity are not reopening gates. New V1
records write only the reference; old provenance/digest metadata is ignored.
There is no replacement full-declaration equality or integrity ledger.

E1 uses actual generated SplitPhase ABI and producer resources admitted through
the existing CNCF parser. The second resource changes every Workflow source
line to 999, relocates the resource and changes JSON formatting, while retaining
the executable contract. Native record comparison accepts those bindings,
while checking the actual current state. This is not a full-declaration
comparison or a proof that only presentation changed.

## R2 — Actual state and revision contradictions remain rejected

This change does not migrate a saved Workflow revision, erase history, grant
execution permission, or let semantic answers select definitions. E2 admits
mutually consistent old-revision resources through the parser and confirms they
are not the current compiled definition. E1 separately changes a record's
causation and confirms that actual saved state cannot be treated as equal.
Existing persistence CAS and owner/collection authorization are unchanged.

## R3 — Executable compatibility, not declaration equality

Compiled runtime adapters validate the actual states, action kinds,
Operation/input/result types, capability bindings and Judgment/Admission
policies they execute. They do not compare whole ABI declarations. E3 in
GoalPhaseRuntimeCatalogSpec accepts moved diagnostic sources/display labels;
its policy/state cases still reject incompatible executable contracts. CNCF
E3 admits entry definitions with moved sources and different generators.
Repeated binding re-admission and raw-copy/fingerprint producers are removed.

## R4 — Resolve actual state frontiers, not a complete-history certificate

Current-record comparison checks the actual instance/definition reference,
revision, lifecycle, progression, correlation/causation, composite/predecessor
and suspension state. It does not compare a complete historical body. A later
record resolves the selected prior revision and its actual frontier; it does
not prove that every preceding entry is identical to a retained copy. History
is not erased, rewritten or repaired by these read-only comparisons.

E4 constructs valid native records with three contiguous transitions. A change
to an earlier, unselected history entry does not refuse current-state or
selected-revision resolution. A contradictory selected frontier, foreign
instance, later-than-current selected revision, changed current revision or
missing record still rejects. GoalPhase native and RepositorySync native
conflict/merge-review/Human prefix consumers use this frontier resolution.
The E4 case and changed callers have not been executed. Other reconstructed
history/body assertions remain removal work, not implicit coverage by R4.

These cases verify the consumer reference comparison, not actual AI execution,
skill integration, all unnecessary-check removal or Phase 5 acceptance.

The corrected CNCF producer suites passed 51 tests; after the normal local
SNAPSHOT refresh, the eight affected sm-workflow definition/native/direct
Operation suites passed 176 tests on their earlier inputs, before R4. All actual failures/interruptions and prior
superseded evidence remain historical. See the
[implementation record](../journal/2026/10/2026-10-08-phase-5-entry-contract-audit.md).
