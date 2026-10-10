# RepositorySync merge-review applicability — Phase 5

Status: connected source correction; executable cases UNVERIFIED. Normal Cozy
generation, registered-runner execution and assembled independent read-only
review are pending. This is the existing Phase 5 implementation-deviation
correction, not new policy/approval, a repair frame or Phase acceptance.

## R1 — Event time, not local expiry

CML RepositorySyncMergeReviewResult, application Review and their producers
contain reviewedAt as a fact, but no validUntil or evidenceDigest. ReviewPolicy
has no evaluation clock. A full review remains applicable before its recorded
event time and after former expiry when the actual target, coverage and judgment
are applicable. Repair, DecisionNeeded and Reject are not Accept. Findings still
refer to actual merge paths; wrong target/coverage and malformed current inputs
remain errors. Actual operation-call lifetime and external credentials are not
changed by this migration.

The shared Result payload reader accepts old validUntil/evidenceDigest fields
and ignores them without parsing, rewriting, renewal or hashing. New payloads
omit them. The existing generic Result/retention serializers use that reader;
generated fields must be rebuilt from the corrected CML, not edited manually.

E1: RepositorySyncMergeValidationAssessmentSpec consumes full review at earlier
and later observations with no product tests selected. Existing target/type/
scope/coverage/disposition cases remain. E4:
RepositorySyncMergeValidationAdmissionExecutionSpec covers absent, expired,
malformed, null and non-string obsolete fields, current output shape and one-shot
public completion of a legacy Result envelope before its review event time.
Its actual completion cannot replay. These cases have not run.

## R2 — Select saved input without a no-change certificate

Evidence identity and typed judgment use the actual Result reference, not a
digest of the review's rationale/body. Completion history records that reference
without a full-Result hash. Old history may retain its obsolete suffix; the
reader checks actual reference/state/correlation but neither recomputes nor
compares that suffix. The history is not rewritten or newly authorized.

Review receipt retention selects the existing record at its actual reference
without comparing it to a complete later proposal or replacing it. Projection
assesses the selected review and current scope once. It does not reload the
entire review/candidate after host lookup to certify unchanged bodies. The
current state/revision remains checked. Stop-only Decision carries the actual
review reference, not reviewResultDigest; legacy Decision input ignores that
obsolete field. New question and commit addresses are actual domain references,
not hashes of regenerated review/request JSON. Diagnostic context is not a
restart gate; actual run/continuation/revision, execution routes, real target and
review policy remain relevant scope/compatibility checks.

New candidate addresses use the selected submission, not validation JSON or
policy-body hashing. Admission and request projection preserve the actual saved
candidate address, including a historical SHA-shaped address, without rebuilding
it from its payload. New review Continuation addresses identify the instance's
review slot; recovery resolves its saved suspension address rather than
regenerating a key from validation facts. Diagnostic evidenceRevision equality
is not a Provided-request gate. Actual candidate repository/type, issued target,
policy, Git references, declared operation and state remain checked. This does
not remove the upstream validation-assessment digest gates described below.

The semantic worker receives authoritative retained paths/checks/parents, not
caller-owned copies. Contradictory repository/target/Git reference/policy still
fails. Commit consumes the already assessed review judgment without repeating
the semantic Result admission; only Accept permits the separate commit boundary.
Actual Git OIDs/ordered parents/index CAS, permission and execution outcomes are
not management hashes and remain checked.

E2: the review-store retry selects its saved rationale without a full proposed
body comparison. Applicable diagnostic rationale also reaches the actual commit
fixture without a reviewReceiptDigest. E3: actual concurrent revision fails,
while later selected receipt/retained metadata observations need no repeated
immutability proof. E5: stop-only projection reads an old history suffix without
rehashing, accepts diagnostic context changes and executes no commit/resume.
All are pending cases in RepositorySyncMergeValidationAdmissionExecutionSpec.
E6: RepositorySyncMergeValidationAssessmentSpec preserves a selected old
candidate reference, accepts diagnostic receipt changes at the same target,
and rejects actual wrong repository/type/evidence target. E7: the public
operation resolves an explicitly saved legacy candidate and Continuation
address, issues and completes once without reproducing its old key. These
additional cases are UNVERIFIED.
The receipt store is fixture-only and declares no old physical serializer;
these cases do not prove SQLite historical commit/address migration.

## Remaining connected removal

Validation/candidate/pending-tree/command-chain and commit/execution/receipt
management digests, other content-derived continuation identities, full reconstructed
history/suspension assertions, generic continuation checks and native-route
unchanged-input controls remain open. No new address producer silently discovers
old SHA keys; actual recorded references must be resolved, never guessed or
replaced with new approval. The explicit legacy envelope/history cases above
do not prove all historical address resolution. Removal must continue through
the remaining producer/consumer/storage/read/spec paths, not be deferred to
Phase 6 or declared complete by these pending cases. Provider/Workflow result
connection, validation/FIX/cost integration and assembled review remain open.
