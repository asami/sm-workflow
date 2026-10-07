# SplitPhase durable intent: verified recovery dependencies

Phase 2 IMPLEMENT under retained parent-direct limited recovery. Previous turn
was progress: invocation 710 passed 251 tests / 35 suites and verified managed
seeded semantic completion. Full scope, original bases, reviews and release
remain incomplete; no new goal or narrowed acceptance boundary.

This unit owns only this journal and
docs/notes/split-phase-semantic-intent-recovery.md. It inspects current application
preparation/completion/record readers and current producer public issue API,
without changing source, public codecs, producer repositories or grants.

Source inspection found a concrete dependency absent from the earlier storage
binding work: the managed public suspension method enlists no application
intent record, while completion currently requires an in-memory Retained
containing a private admitted Intent. Persisting that object or treating its
independent write as an atomic issue would not satisfy the intended contract.
The new note separates immutable application facts from canonical publication,
defines the required recovery provenance, preserves refusing missing/current
authority behavior, and identifies hash-free explicit-reference production
preparation as a prerequisite rather than promoting fixture hashes.

Next implementation must freeze the reference/revision representation and
source/test frontier, then implement data projection plus admitted reconstruction
and the public issuer. Either an explicitly non-atomic inert preparatory record
protocol needs interruption/retry proof, or joint publication needs an admitted
producer composition change first. Existing lower-level prepared facilities
do not establish that public managed capability.

No SBT invocation was needed for source inspection and documentation only.
No runtime validation, acceptance review, semantic admission, Git mutation,
publish/install, new pool/provider or agent delegation is claimed. Retained
251/35 evidence belongs to the preceding code tree and seeded scenario only.
git diff --check completed successfully after the document edit.
Goal stays active. This unit changes the next action using source evidence;
it is not a repeated genuine-blocker audit or a completion claim.
