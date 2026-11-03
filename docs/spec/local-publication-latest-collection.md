# Latest local publication collection

`LocalPublicationService.collectLatestLocalPublication` collects a retained
publication after a caller loses the original request identity, including after
a CLI interruption. Its required `LocalPublicationLatestCollectionInput` fields
are `targetIdentity` and `ownerSessionId`, both nonblank strings. Extra,
duplicate, null, missing and incorrectly typed fields are rejected. The operation
returns the existing `LocalPublicationResult` envelope; `publication` contains
the actual original `LocalPublication.Request` and retained result.

The supplied Session is resolved through the existing `retainedSessionC` owner
binding and must remain open. The caller participant comes from the invocation's
`SecuritySubject`. The target must be exactly declared by the host publication
policy. Collection reads that target's existing immutable attempt index in its
actual append order. It selects the latest attempt, then checks its exact target,
participant and owning Session before reading its intent or result. If a newer
attempt belongs to another owner or participant, collection rejects the request;
it never searches backward for a convenient matching older attempt.

The selected attempt's original saved input is passed to the existing `collectC`
collector, preserving retained preparation chains and their actual meaning.
The original saved request, selected intent when present, and collected result
must have the same binding. No target attempt returns `not-found`. An existing
intent without a terminal result remains `uncertain`; an actual saved request
without effect intent can be `prepared`. Actual failures, confirmed nonstarts
and successful results retain their existing statuses. Process logs never
manufacture a successful result.

Collection performs no source observation, record writes, identity allocation,
SBT execution, publication or workflow advancement. It reads no other Session
stores, accepts no storage selector, and leaves the existing request-identity
collector unchanged. A host without a publication policy returns the existing
`not-configured` envelope after checking the supplied Session.

`LocalPublicationSpec` covers append order, original identities, absence,
preparation, uncertainty, actual failure and success, preparation continuation,
ownership and participant refusal, crossed retained facts, strict input and
effect-free repeated reads. The existing isolated installed prerequisite recovery
scenario collects the recovered publication twice through the same real test
Session, compares it with the original saved-request collector, rejects a wrong
owner and checks unchanged publication counts and recovery stages. It uses only
its tiny disposable repositories and Session, never a real consumer store.
