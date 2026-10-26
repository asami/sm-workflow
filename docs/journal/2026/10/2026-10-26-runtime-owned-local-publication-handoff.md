# Runtime-owned local publication handoff

The architecture decision moves publishLocal necessity, execution, result
collection and return to the owning workflow into sm-workflow. AI answers only
an actually issued unresolved build-relevance question. This adds no blanket
publication approval, dependency-management prerequisite, new hash or process
runner. The ordinary CNCF typed SBT/UoW path remains the execution owner.

Implementation and qualification are complete under
[the additive correction plan](../../../phase/local-publication-ownership-correction.md)
and [the public contract](../../../spec/runtime-owned-local-publication.md).
Detailed execution evidence remains local, outside Git.

Existing producer checks and image 93ffa228-f7d2-49d1-a53d-87d1340896e3 retain
their original scope. Their completed publication and consumer checks were not
replayed to collect results.
The CC51 provider-policy refusal remains retained and is not retried through a
new prompt, model, runner or execution route.

Owners keep their current Goal, Session, base, accepted work, PLAN epoch,
history and repair counters. Do not re-Start, re-Submit accepted work, re-run a
successful publication, edit an old intent or operate another owner's Session.
Adopt the delivered runtime and fixed host publication targets at the next
ordinary boundary. Collect completed publication responses without execution;
let the runtime distinguish confirmed nonstart from rejection, actual failure
and uncertain effects before normal continuation.

## Actual delivery and verification

The normal source host called public
`LocalPublicationService.ensureLocalPublication`; the runtime executed
`publish-local` through the existing typed SBT/UoW interpreter. Its final
actual SBT process exited 0 after 97,777 ms. The normal composite receipt reports:

- publication: `Completed`;
- runtime update: `Activated`;
- installation: `f760b768-3fe5-4dc7-95a3-601d430afb90`;
- update: `0665879b-ff2c-4f3d-89e0-9cd8fcefb7a1`.

The ordinary selected installation now identifies that image. A whole-image
copy pinned the same identity only for isolated qualification; no runtime was
assembled by copying or replacing individual JARs. Normal installed CLI startup
exited 0 and exposed both publication operations through Help.

The fresh isolated installed-CLI case passed 1/1 against that image, with actual
SBT/wrapper exits 0 and process termination. It used a plain SBT producer without
sbt-cozy, published exactly once, collected the saved result from another CLI
process, reused the same immutable request, skipped a new same-source request,
and returned not-found for an absent collection without execution.

Final source decision tests passed 18/18. Packaged skill verification passed its
three unchanged cases and, after adding the actual contract links, the previously
failing link case passed 1/1. CNCF's selected DSL/codec/selection regressions have
76 successful cases across retained executions, including the corrected enum
expectation. Existing ABI, generated Help, launcher, direct-operation and native
validation results remain applicable. The independent integration review is
PASS: CB-LP-01 is resolved and no current-boundary blocker remains.

Shared guard, intake and distribution Python checks remain successful. Registered
runner revision39 is deployed; fresh nonpublication validation and installed-CLI
qualification succeeded through it. Local publication is not an independent
AI-runner task.

## Retained failure and source-host correction

The first new publisher-restoration request
`sm-workflow-publisher-restoration-20261026-01` actually failed with SBT exit 1.
The normal updater's preflight rejected a missing exact CNCF runtime root module
before publication. That terminal result remains immutable; it was not retried
under the same identity or relabelled as nonstart.

The source-only build connection now contributes the selected CNCF project's
ordinary `Compile / packageBin` JAR with verified cross-published
`moduleID`/artifact metadata to the application's normal dependency classpath,
retaining its resolved transitive dependencies. It removes only that project's
classes/archive entry, rejects duplicate CNCF identities, and preserves the
publisher's existing strict checks. The dedicated classpath assertion exited 0
and the focused independent review passed. No CNCF publication, artifact overlay,
cache isolation or generic execution fallback was introduced.

After that actual correction, new explicit request
`sm-workflow-publisher-restoration-20261026-02` produced the successful normal
publication above. The old failed result and all earlier test failures remain
retained outside Git alongside their raw logs.

## Adoption by owners

Use the new normally selected runtime at the next ordinary owner boundary.
Declare fixed publication targets, the existing managed collection, and exact
typed Git/SBT capabilities in the owning host as specified by the public
contract. The delivered validation prerequisite is opt-in per host target via
`beforeChecks`; this delivery does not claim that another owner's host was
silently configured or that its real checks were executed.

For a publication already completed by this delivery, collect its actual result
instead of publishing again. An owning workflow selects its real target and
request identity; callers provide no arbitrary command, executable, environment,
destination or permission grants. A missing host binding is a typed configuration
result and cannot authorize a standalone publication fallback.

No real consumer Session was operated. In particular, publication acceptance does
not repair the separately reported Phase6.7 partial selected commit or authorize
its recovery. That diagnosis remains a separate requested boundary. Goal, Session,
base, accepted results, history, repair counts and unrelated dirty work were kept;
CNCF's pending merge was not completed, aborted or committed. No commit or push
was performed by this delivery.
