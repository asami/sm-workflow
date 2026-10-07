# SplitPhase canonical managed Start composition

Goal State: IMPLEMENT — sm-workflow Phase 2. Previous turn fixed current-call
selection access and passed 185 tests / 26 suites: progress. User-authorized
parent limited recovery, no agents; parent model/effort not directly observable.

## Frozen implementation

Own new SplitPhaseManagedStartAssembly.scala, its executable spec and this
journal only. Read the entire existing GoalPhase managed Start composition,
configured Factory and CNCF WorkflowComponentStorage contract before adapting.
Use the same canonical storage composition rather than SQLite, raw providers,
new pools or a private Workflow engine. Catalog is the official opaque admitted
SplitPhase catalog; selection dependency must be current-call capable.

Declaration specifies the exact host configuration/instance/continuation
collections. WorkflowComponentStorage checks actual Subsystem grants before
binding its instance provider. The small persistence facade delegates every
operation to that binding and rejects a different configuration. Host entry
evidence remains injected explicitly; no caller can mint it or select storage.

Two Given/When/Then scenarios use real SQLite host bootstrap. Canonical
configured Factory construction invokes the binder with resolved component
scope; actual Subsystem dispatch supplies current call capsules. Missing
selection stops both calls before source/proposal execution. Foreign granted
collections and incomplete dependencies reject before any Operation is
returned. Existing factory is reused as a host-only composition vehicle;
no new standard bootstrap/MCP surface or standalone tool is advertised.

Validation: official preparation/packageBin, SplitPhase*/GoalPhase catalog/
shared-carrier/ProfileStartAdmission and configured Factory regression suites.
No edits during the live serial command. No acceptance, source snapshot hashing,
positive human consent, document mutation, Git, publication or installation.

This is canonical Factory/storage binding plus negative dispatch evidence, not
durable positive Start, source/proposal managed records, semantic completion,
reopen/concurrency or normal server/CLI acceptance. P2-R04 remains unchecked;
those obligations must be realized rather than replaced by this small seam.

## Bounded pre-review repair

692 completed main compile/preparation/packageBin; Test/compile stopped at a
matcher parse error before running tests. Stable M1 blocker SMSTART-MATCHER-01:
the dotted expression composition.get requires parentheses after
theSameInstanceAs. Full wrapper summary read: exits 1/1, lock=released.
Freeze only the already owned spec, preserve the same identity assertion,
and explicitly annotate refusal-port result types using the existing
Consequence.stateConflict style. No product, scope, expectation or host
behavior changes. Orchestrated cncf-test-fix, one bounded correction in this
slice; full review/release counters remain untouched.

Failed receipt: /tmp/skill.cncf.d/a-7b38157a-6054-496e-a601-35294f17f710/72734-20261001T174431Z.summary.json

## Settled observed validation

693 completed the frozen official preparation, packageBin and focused
accumulator: 192 succeeded tests / 28 completed suites, no failures, aborted,
canceled, ignored or pending cases. Full summary was read; exits 0/0 and
lock=released. Both managed-composition scenarios ran successfully against
owned SQLite host bootstrap, canonical Factory initialization and actual
Subsystem dispatch. SMSTART-MATCHER-01 closes after one bounded correction.

Receipt: /tmp/skill.cncf.d/a-dc11ad58-b18a-4594-a113-03878cbfc443/73165-20261001T174526Z.summary.json

git diff --check passed. The test demonstrates host-grant admission and exact
component/current-call ownership, not a physical Workflow create/append:
missing selection deliberately stops before those effects. The injected
refusal-only selection probe is not a real DataStore reader or human issuer.
Positive persistence/reopen and real managed evidence ports remain acceptance
work. No installed skills/config, Git, publish/release or Phase status was
changed; full validation and independent review remain at their original gates.
