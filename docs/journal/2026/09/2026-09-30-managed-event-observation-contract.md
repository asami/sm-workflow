# Host-admitted event observation contract

Date: 2026-09-30
Status: design boundary fixed; implementation and executable specs pending

Continued from focused-335's pure duplicate classifier. Inspected actual
DataStoreSpace host admission, EventStorePersistenceDeclaration, Subsystem lease
assembly, EntityVerifiedMultiGuardDataStore and SqlDataStore native readback.

The existing snapshot reads exact postcommit plan targets, all required present.
It does not prove incoming lookup absence. The existing host policy authorizes
guard updates and side Creates, not arbitrary reads. Ordinary SQL loading can
represent a missing table as no row, which must not become absence authority.

Recorded the separate managed observation contract in
docs/notes/managed-event-observation-snapshot-contract.md. It fixes host-only
read admission, frozen batch/policy provenance, one native read transaction,
explicit present/absent roles, a nonrecursive referenced-event/intrinsic-index
closure, physical-ID validation and fail-closed lease/thread/provider checks.
Worst-case bounds are 512 event probes and 768 index probes for 256 inputs,
not an unbounded collection scan. Incoming retained payload admission is deferred
until classification identifies genuinely new facts.

The later publication route must validate exact observations in the write
transaction. All-retained durable input needs validation without cursor mutation;
all-transient input needs no cursor work. Current guarded-update ABI cannot
express validation-only completion; dummy updates are expressly excluded.
No classifier-to-commit adapter or new authority has been implemented.

This turn changed documentation only. No SBT invocation, source change, new test
result, commit, publication or skill/agent modification. Focused-335 remains
the last implementation validation (212 tests, 24 suites); it does not validate
the new observation contract. S12/P2-U15/P2-U16/P2-U05 remain open.

Next bounded implementation: host-derived observation request/capability and
real managed native snapshot executable specs, without activating publication.
