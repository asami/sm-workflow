# Managed answerable-question retention

Previous turn progressed: 572 packaged and passed 191 tests/24 suites for the
answerable question/verifier interfaces. Current continuation uses cncf-goal-phase,
parent /root direct limited recovery, standard mode, model/effort unobservable.
Retain unchanged progressive reference snapshot and full Phase scope.

Owned implementation disclosed before edits: new human question policy, record
codec/retention and managed writer; extraction of the existing current-intent
guard from the stop writer; new record spec, actual managed public-scenario
extensions and this journal. No CML/public activation, upstream/skill/config
edit, agent, commit, push or publication. Preserve unrelated dirty work.

Require a separate exact resolved host human-questions store/collection declaration,
same logical Workflow store, disjoint from Workflow instance/Continuation,
existing selection/association/authorization/stop-question purposes and configured
artifact catalog. This policy is not a framework grant. Old host purpose lists
and stop metadata remain unchanged. Capture the actual fixed-key
ComponentRecordStorage Create capability for the Workflow-allocated question ID.
Do not use direct SQL, another pool, upsert, update, deletion or installation
during ordinary Factory construction/reopen.

Extract the already implemented retained accepted-review/current-instance/
participant/issued-candidate intent checks into withCurrentIntentC. Both writers
use the same checks and final full-record Workflow re-observation; their own
separate capability/destination checks remain outside the callback. The callback
is internal and does not issue storage authority.

Encode a closed, byte-bounded immutable record containing schema, exact question
schema, intent identity, typed canonical Decision wire and complete visible
choices. Decode reconstructs the typed intent/question and revalidates the
answerable result/evidence/context contract; stop-only wire, undeclared fields,
wrong schemas and malformed/unbounded choices fail. Codec validity alone is not
proof that this intent is current, a human answered, or a command is authorized.

Retention uses Create plus exact committed readback. Equal retry observes the
same record; different visible choices or scope cannot replace it. Initial read
failure does not infer absence. Post-Create/observation uncertainty returns
transaction-indeterminate and permits only same-row observation recovery.
Pure fault tests are not real SQLite driver-failure evidence.

Extend both actual SQLite/Blob/Service/CLI scenario variants with an internal
current-call probe of the production answerable writer: missing grant refuses;
granted first retention and second-host reopen return the same complete question;
conflicting visible choice and stale intent refuse; escaped call after dispatch
cannot reuse the writer. Preserve Workflow history and zero additional Blob/Git
effects. The probe is test-only, not an exposed human Operation; public activation
and actual human evidence remain unproven.

Validate package and prior accumulator plus new record suite: 25 suites, expected
197 tests. Question-to-public Workflow issue binding, actual host verifier,
independent answer collection/grants, verified immutable answer writer,
answer/resume, bounded command authority, production transports and complete
Phase validation/review/acceptance/release remain open.

## Validation result

Invocation `SMWF-HUMAN-QUESTION-DIRECT-20261001-573` completed packageBin and
the selected 25 suites: 197 tests succeeded, none failed or aborted.
Both sbt and wrapper exited 0; the shared serialization lock was released.
`git diff --check` was clean after validation.

- Log: `/tmp/skill.cncf.d/a-09192b1c-56fd-4841-9008-28cc08a4bc89/37451-20261001T101143Z.log`
- Summary: `/tmp/skill.cncf.d/a-09192b1c-56fd-4841-9008-28cc08a4bc89/37451-20261001T101143Z.summary.json`

This is focused implementation evidence, not complete Phase acceptance or proof
of a production human verifier/public answer path. Parent-direct recovery bypassed
the skill-only orchestration stop, not platform permission review or the shared
SBT lock. No agent, commit, push, publication or upstream change was performed.
