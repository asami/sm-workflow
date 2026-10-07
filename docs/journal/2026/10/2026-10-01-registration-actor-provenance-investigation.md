# Registration actor provenance investigation

Continue the same unfinished sm-workflow Phase 2 goal. The preceding goal turn
made no implementation progress: it requested clarification of an unavailable
two-option context. No pending typed control record exists at
`.codex-workflow/phases/PHASE-2/control.json`; do not invent its options, infer
the meaning of "the latter", or treat a scheduler continuation as consent.
This investigation is independent of that missing selection.

## Current source evidence

Inspected CNCF SecuritySubject, SecurityContext, AuthenticationProvider,
IngressSecurityResolver, OperationAuthorizationSpec and the fixed-profile
scenarios of IngressSecurityResolverSpec, plus the current sm-workflow CML and
host-record installation note. No product source changes were made.

`SecuritySubject.current` derives the subject from the call ExecutionContext.
`AuthenticationResult.toSecurityContext` obtains the principal identity from
the provider result, and IngressSecurityResolver marks that resolved context
through internal SecurityAuthenticationProvenance. Plain `isAuthenticated`
is insufficient: it can also derive from public token/session/attribute facts.

The existing fixed/local ingress route obtains a configured local principal
from the owning Subsystem and sets it authenticated, but does not mark provider
provenance. Existing fixed-profile spec source explicitly expects
`isProviderAuthenticated == false`. Therefore provider-only admission is not
a complete implementation of the required local CLI path. Comparing a claimed
participant string to a configured principal without verified ingress origin
would also not establish caller authentication. Participant identity mapping
must be explicit, rather than inferred from arbitrary principal attributes.

## Newly exposed prerequisite

SecurityAuthenticationProvenance stores marks in a synchronized
`java.util.WeakHashMap[AnyRef, Boolean]`. That map compares keys by equality,
not object identity. Both SecurityContext and SecuritySubject are public case
classes. A distinct equal copy can consequently match a marked key without
passing through the internal marking operation. This contradicts treating a
mark as evidence attached exclusively to an admitted object.

A bounded standalone Java 21 diagnostic used the same synchronized WeakHashMap
construction and two separately allocated equal record objects. The command
`/usr/bin/java /private/tmp/SmWorkflowProvenanceEqualityProbe20261001.java`
exited zero and printed:

```text
distinctObjects=true
equalValues=true
unmarkedEqualObjectRecognized=true
```

This verifies the map comparison behavior, not a CNCF transport exploit or a
passing CNCF executable spec. No network, SBT, publication or product mutation
was performed by the probe. A regression through the actual CNCF ingress and
SecuritySubject path remains necessary.

## Required next boundary

Do not activate registration using this mark or solve CLI support by accepting
request-level `authenticated`, `principal_id`, role or provenance fields.
First fix identity-scoped provenance and distinguish trusted configured-local
origin from provider origin and controlled-test/fallback facts. Preserve the
existing meaning of provider-only authorization. Neither origin supplies
explicit human consent, collection write authority or command authorization.

This is a newly exposed common security prerequisite, not a same-lineage
storage test repair. Its production repair changes a protected CNCF security
boundary and needs attributable user direction before implementation. Candidate
scope is the provenance group in SecuritySubject, trusted-local/provider ingress
marking, and executable regression specs for equal copies, forged attributes,
fixed local dispatch and authenticated provider dispatch. Do not automatically
expand the previously frozen storage implementation frontier to those files.

Current CML has no registerGoalPhaseSubmission Operation. Its public ABI and
actor admission remain unimplemented; storage-focused 51-test evidence from
the preceding implementation does not prove these contracts. Phase acceptance,
independent review, final validation and release remain unfinished. No commit
or completion is claimed.

## Unaffected consumer-path inspection

On the next scheduler continuation, preserve the pending request for security
repair authority; no actual human answer was received. The previous turn made
progress by obtaining the map-behavior diagnostic, not by completing admission.
Inspect the downstream registration consumer without altering security or ABI.

The current configured Factory composition, `GoalPhaseHostRecordReaders.bindC`,
constructs `GoalPhaseSubmissionAssociation.DataStorePort` for the invocation
port. That port loads `associationPayload` and accepts only
`sm-workflow.goal-phase-submission-association.v1`. The prepared registration
codec instead stores `registrationPayload` and accepts only
`sm-workflow.goal-phase-submission-registration.v1`. No main-source consumer
of GoalPhaseSubmissionRegistration exists outside its own codec.

Consequently, adding the registration writer alone cannot establish the real
register-submission -> requestStepClose path: the current configured consumer
would reject a newly registered record for lack of associationPayload. This is
an integration gap, not evidence that the existing v1 fixture reader is broken.
Keep its existing format tests as historical/explicit-fixture evidence; do not
make the new decoder silently accept v1 or write a second compatibility row.

The implementation frontier after actor authority is resolved must include a
call-scoped registration reader which decodes the complete new record before
projecting its originalStart.handle, the configured invocation-port composition,
and real dispatch specs with registered bytes. Required negative cases include
wrong submission identity, altered receipt, malformed record, old association
bytes on the new path, unconfigured collection and escaped call lease. Required
positive proof is committed registration followed by requestStepClose through
the same configured host, including reopen; an isolated codec roundtrip cannot
replace it. No reader, Factory, CML or product source was changed this turn.

Documentation whitespace check passed. No SBT process was started and no new
test count or completion evidence is claimed. Security authority remains pending;
the scheduler continuation does not answer the protected-boundary question.

## Attributable repair direction received

The owning conversation's direct user message is:

> CNCFの認証由来管理と対応specも修正して

This exact text occurs once in the available owning conversation and was
received from the human user, not a scheduler continuation or tool result.
Task identity: `01a0a6d7-7043-73a2-a808-1367fe5fdb1f`. The parent admits it as
direction to include CNCF authentication provenance management and its
corresponding executable specs in the same unfinished sm-workflow Phase 2.
The previously requested scope addition is no longer awaiting a human answer.

Retain the exact CNCF product root
`/Users/asami/src/dev2025/cloud-native-component-framework` and all existing
original bases, ownership and repair ledgers. This direction permits the
bounded security prerequisite repair; it grants no release, publication, push,
commit, human-consent substitution or unrelated security redesign. Preserve
provider-only authorization semantics while making admitted origin
identity-scoped and distinguishing trusted local ingress for the required CLI.
Freeze exact production/spec paths during the next parent planning gate.

The converted control record is still absent. Do not invent an old decision,
option selection, repair counts or authority/base reference to construct it.
Record the actual new direction here rather than recreating a hash-backed
decision. The next workflow state is PARENT_CAPABILITY_CHECK, then PLAN for
the newly admitted protected repair. This answer-recording turn performs no
product edit, SBT, review or commit. The product goal was observed blocked;
the goal API offers no active/resume status operation, and this record does
not falsely claim a product status change. Resume the same goal via the
product's user-controlled continuation; never create a replacement goal.
