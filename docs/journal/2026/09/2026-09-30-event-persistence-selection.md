# Selected-event persistence contract correction

The user clarified that EventStore persistence must be selective by event
kind, not implied by the existence of a Subsystem persistence declaration.
The existing S12 destination, owner/collection authority, lease and atomic
update work is retained. The Subsystem-owned service model is selected.

A pure versioned `EventPersistencePolicy` and Workflow-owned deterministic
`WorkflowEventSelection` candidate now separate policy selection from storage
admission. Exact kind rules determine Persist/Transient; unknown kinds fail
before storage preparation. Caller persistence hints grant no selection
authority. Nontransactional emit remains outside this transactional API.

The design note fixes delivery/rollback semantics: transient records have no
durable sequence/index/replay; both delivery subsets wait for verified commit
and readback, definite rollback drops both, uncertainty suppresses delivery
without automatic retry, and post-commit delivery failure cannot undo commit.
The runtime selected-only persistence/dispatch route is still pending.

Executable candidate examples were added for mixed selection, malformed and
unknown policy input, empty/all-transient selections, preissued/invalid record
batches, rollback/uncertainty suppression and revision retention. Validation
results are recorded below after the serialized focused run.
No S12/P2-U15 acceptance, artifact refresh, commit or publication is claimed.

## Validation

291 (`SMWF-P2-CNCF-P88-SELECTION-FOCUSED-20260930-291`) started SBT but
stopped before tests on the stale Cozy generator classpath, exit 1 and lock
released. Cozy prerequisite 292 (`compile`, `cozyExportRuntimeClasspath`)
passed, exit 0 and lock released. CNCF retest 293 passed **45/45 tests in five
suites**, exit 0, wrapper exit 0, lock released. Its verified execution identity
was `/root/sbt_receipt_482258f8f3efa574146b`, registered cncf_command_runner,
gpt-5.6-luna / medium; route healthy. The receipt is
`/tmp/skill.cncf.d/cncf-command-execution-4dd94129793b8b5f06b38697930330073b3c894a67cf047ba1bda2df0dffb700-74da58ef114dc9a5033c78fec6a3aa5d/command-execution-sha256-ed3582af30e8ee895b00b687be80761dee8435be73ff16c7534aa7bf8c584029`.
Validated tree SHA-256: `449d2f9898a05f0f541c3be15aaa402b98d290891fbf2ede667a69f8d102f1c5`.
Subsequent edits are evidence/documentation updates only, not new product code.

The Subsystem host examples additionally prove no implicit persist-all from
an installed declaration, fail-closed legacy/unknown grants, shared physical
managed-resource assembly, separate-resource rejection, escaped-view rejection,
ambiguous declarations and construction-scope restoration. Unsupported
multi-guard capability still rejects: no positive provider transaction is
advertised. Pure rollback/uncertainty delivery projection is tested; actual
rollback/dispatch integration remains pending and is explicitly tracked as
P2-U16. No full regression, S12 closure, publishLocal or Git mutation occurred.
