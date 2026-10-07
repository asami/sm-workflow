# Typed partition semantic Operation registration

## Frozen implementation unit

Phase 2 IMPLEMENT; retained user-authorized limited parent-direct recovery,
no agents. Previous 746 passed 375 tests / 51 suites with no repair. Governing
single-user typed-control policy remains unchanged; original Phase scope and
acceptance gates remain incomplete.

Register the converted partition request/result purpose types, a distinct
Required semantic Judgment and its Admission action, and a distinct Provided
completion Operation in the existing SplitPhaseWorkflow CML. Use a separate
typed constituent, not an alias or adapter to the historical hash payload.
Increment the Workflow definition revision to typed-partition v3; do not reuse
the old revision for changed declarations. Preserve unrelated and historical
declarations as incomplete migration work, never as a fallback for the new SPI.

Own only:

- `src/main/cozy/sm-workflow.cml`
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseDefinitionBinding.scala`
- `src/test/scala/org/simplemodeling/textus/smworkflow/SplitPhaseGeneratedAbiSpec.scala`
- `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartCurrentCallSpec.scala`
- `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseRuntimeCatalogSpec.scala`
- `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartExecutionSpec.scala`
- `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartCommandAdapterSpec.scala`
- new `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitPartitionDefinitionSpec.scala`
- this journal.

Binding validates the complete generated action/Required/Provided set, retains
all Required SPI source correlations in the CNCF ABI projection and exposes a
converted Operation selected by its exact capability, not list position or a
handwritten caller SPI. No decoder, factory dispatch, provider, pool, grant,
upstream/shared-skill, Git, publish/install or old-schema compatibility changes.
Existing Start fixtures update only their exact current Workflow revision;
generated ABI assertions update to the complete new declarations.

Fresh 747: serial SBT cozyPrepareRuntime/packageBin and focused accumulator,
also explicitly run SplitPhaseGeneratedAbiSpec. Current typed permission and
native review; at most three converging same-unit mechanical pre-review repairs,
zero used. No source edits during live SBT. This proves component declarations
and packaged admission only, not actual canonical publication or completion.

The actual managed issue primitive can commit instance and issued WorkOrder
together without calling claim APIs. It still needs a converted host-owned
stored planning context, source/instance/Operation bindings and a current
authorized Operation call. Do not claim the registration alone supplies those.

## Bounded pre-review repair 1

747 actually terminated with SBT/wrapper exit 1 and `lock=released`. Generation
and source/test compilation succeeded; all four new declaration scenarios and
both GeneratedAbiSpec scenarios passed. 381 tests / 53 suites ran: 356 passed,
25 failed. All failures are the same fixture-assembly intake error, before its
historical behavior executes: `SplitPhase fixture Service differs from generated
ABI` (24 SemanticWorkflow scenarios and one StartExecution scenario).

Freeze the additional exact path
`src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseFixtureServiceAssembly.scala`.
The complete expected declaration vector must now include the new Provided
completion Operation; do not loosen it to a contains/subset check. Its fixture
still assembles only its explicitly supported four/five historical Operations,
not the newly registered but unconnected completion. Add an assertion to the
already-owned StartExecution scenario that this operation is absent from that
fixture service. No fake callback, old-payload decoder alias or activation.
This is one mechanical same-root registration-consumer repair, 1/3 consumed.
Revalidate through fresh 748 with the same command scope after terminal 747.

Evidence: `/tmp/skill.cncf.d/a-adbca0d1-62c2-4327-a69f-d34b14ef3e58/24560-20261001T231701Z.summary.json`
and its sibling `.log`.

## Validation outcome

748 actually terminated with SBT/wrapper exit 0 and `lock=released` (native
session 20859). 381 tests / 53 suites passed, zero failures/aborts/cancelations/
pending. The four new declaration scenarios and two GeneratedAbiSpec scenarios
ran in this accumulator. The one bounded repair converged (1/3); no further
repair, source edits during live SBT, acceptance commit or release was performed.

Evidence: `/tmp/skill.cncf.d/a-1da1f628-633d-433f-a4f9-487b4d88860b/25715-20261001T231956Z.summary.json`
and its sibling `.log`. Read the invocation summary and new scenario output,
not only a global success banner. CML generation produced 104 Scala sources.
The actual packageBin JAR was read through its isolated resource loader.

Registered types match the converted application payloads field-for-field,
including nested explicit source/context/authority/evidence references and
closure/adjacency data. Results contain no authority replacement, host record
allocation or execution selector. The converted SPI is selected from the fully
admitted generated declaration; all Required source correlations are retained.
Old-type substitution and receiver-valid foreign semantic provenance are
refused by the actual runtime catalog. The historical fixture's exact four
assembled Operations are asserted; new completion is not falsely advertised.

The component's new declarations are not live Factory/CLI/MCP callbacks or
actual persisted semantic work. The v3 binding does not adapt a v2 stored
instance; no migration or old-type alias was added. Legacy regression success
does not establish single-user/typed-control conformance of those old routes.

## Remaining canonical completion dependency

Read-only primary-source investigation found that the managed issue primitive
`WorkflowManagedIssuedWork.suspendC` can prepare and jointly commit instance
and issued WorkOrder without calling a runtime claim API. This is a usable
publication seam once current host planning context and instance authorization
are properly connected; no issue was attempted in this unit.

Completion is different: CNCF `WorkflowCompletionDataStorePersistence`
`executeSelectedClosingHostC` calls `continuations.claimC` (current source line
104), and `executeOwnedClaimC` calls `projectClaimedWorkOrderC`, uses a claim token
in Intent and handles claim release. `ContinuationRecordJsonV1` also requires a
claim identity for every non-Available status (current lines 155–157). Merely
calling WorkflowManagedCompletion would therefore silently retain the protocol
the user removed. Do not use it as a fallback or recreate completion by directly
accessing SQLite from sm-workflow.

The next connected work needs host-owned stored planning/context records and
the admitted CML-derived operation, then CNCF-owned single-user completion that
retains one joint transaction and typed result/history/evidence while removing
the claim/token/release mechanics. Phase 2 already records explicit CNCF Phase
88 mutation scope; preserve the governing authority and exact upstream paths
before any such implementation. No upstream edit, artifact refresh or production
completion acceptance is claimed here. Full original Phase 2 still requires
canonical issue/consumption, real actor/grant and human Start, both transports,
restart/management/reference-data/command execution, independent reviews,
original-base admission, full validation and release.

Post-outcome `git diff --check` passed with exit 0.
