# sm-workflow: single-user execution and typed control data

Date: 2026-10-02 (Asia/Tokyo)
Status: explicit user design direction; not implementation or acceptance evidence.

## Fundamental operating assumption

sm-workflow runs for one user in an environment where mutual exclusion is
unnecessary. This is the design premise, not an optimization to add after a
multi-user design. Do not design application workflow control around competing
users, concurrent writers or conflicting claims.

Application locks, claim protocols and contention retries are therefore not
required by default. Add an exceptional mechanism only for an actual, identified
requirement, rather than a hypothetical concurrency scenario. This direction
does not itself remove storage transactions, external-command safety rules or
the workstation's independent SBT serialization policy.

## Typed specifications instead of management hashes

Do not use hashes as substitutes for workflow management or control data:
identities, state, references, provenance, receipts or consistency decisions.
Use explicit identities and revisions, with dedicated types for state,
transitions, ownership and reference relationships. A revision denotes the
selected data version; it does not imply an optimistic-locking protocol.

Replacing a digest with an unconstrained string is insufficient. Express the
specification in the types and their construction rules so that incomplete,
contradictory or invalid executable states cannot be freely constructed. Where
comparison is needed, compare the relevant complete typed data, not a digest.
Do not provide an adapter or fallback to a hash-backed control route.

## Minimal, coherent checking

Do not repeatedly recheck invariants already guaranteed by types and validated
construction. Checks with incomplete coverage can contradict each other as the
workflow becomes more complex; their presence is not evidence of correctness.
If a partial check introduces complexity or bugs instead of enforcing a clear
specification, remove the check rather than adding another defensive layer.

Continuous, exhaustive consistency monitoring is not required. Detect
contradictions at natural boundaries, such as loading stored data, when useful
and well-defined. If a contradiction is found, stop with a clear explanation;
do not invent automatic repair, contention handling or retry machinery.
This does not require complete-data comparison at every call boundary.

Keep external-input validation, execution permissions and destructive-operation
safety separate from internal consistency checking. Retain necessary checks at
those external boundaries without using them to justify excessive internal
defensive mechanisms.

## Implications for ongoing development

Existing hash-dependent code and defensive protocols are not grandfathered in
merely because focused tests pass. Evaluate their necessity against this
single-user premise and the typed specification, and simplify or remove them
where they conflict with this direction. Preserve required workflow behavior;
do not substitute concurrency assurance for the intended specification.

The governing direction is: specify the workflow strongly in types, avoid
management hashes, and keep the single-user execution path simple.

## Record scope

This journal records the user's agreement and subsequent clarification in the
side conversation. Only this new document was added. No runtime source,
specification tests, Git state, permissions, shared skills or configuration were
changed; no tests or commands with external effects were run. Implementation
conformance and Phase completion remain unverified by this record.
