# Managed human answer retention

Previous goal turn progressed: invocation 573 packaged and passed 197 tests in
25 suites, and recorded managed answerable-question retention. Continue the same
Phase 2, parent `/root`, standard mode, model/effort not directly observable,
under the explicit parent-direct limited-recovery direction. No new goal/base,
agent, upstream/skill/config edit, commit, publication or Phase acceptance.

## Frozen implementation boundary

Ownership was disclosed before edits: new `GoalPhaseHumanAnswerStoragePolicy`,
`GoalPhaseHumanAnswerRecord`, `GoalPhaseManagedHumanAnswerRegistration`, new
answer record spec, the existing actual managed suspension scenario, and this
journal. `ManagedHostRecordRead` was explicitly added before its edit because
no separate fixed-key read API existed for the new question purpose.

The answer declaration requires the Workflow/question store and an independently
declared collection, disjoint from questions, Workflow records, existing host
record purposes and the configured artifact catalog. Pure restriction checking
does not issue a grant. The production writer captures the actual independent
answer Create capability first and reads the question through the canonical
managed-call DataStoreSpace route, without requiring question Create authority.
No pool, direct SQL, provider substitute, overwrite, delete or reopen installer.

The writer derives the complete question and submitting actor from retained
records/current ingress, checks the accepted review and exact current intent
through the shared guard, and invokes the host human verifier only for an
absent answer. A caller does not supply a question body, receipt body, storage
destination, verifier or progression instruction. The internal function's
verifier argument is a host dependency; no public entry is enabled yet.

One immutable bounded record holds the full visible question, typed choice,
Component instance, human/relay attribution, interaction reference, issuer,
purpose and original validity interval. Structural decoding is not an Admitted
capability or proof of independent issuance. Create must be followed by exact
committed readback; equal retry observes the original receipt without another
verification or renewal, including after its original expiry. A conflicting
choice/reference cannot replace it. Initial read failure never means absence;
post-Create uncertainty withholds success and recovers only the same row.

Answer retention neither advances history nor grants/executes Git commands.
Current-intent re-observation remains the existing read-observation guard, not
a new linearizable joint answer/history transaction.

## Executable evidence and repairs

New behavior-oriented record examples have Given/When/Then clauses for closed
round-trip/refusals, verify-on-absence/equal retry, rejected verification/initial
observation and post-Create uncertainty. Fault examples use a pure storage port;
they are not SQLite driver-failure or concurrent-race evidence.

Both existing actual SQLite/Blob/Service/CLI scenario variants now include a
private managed-call probe of the production answer writer. Missing independent
answer grant refuses before verification; absent verifier refuses before Create;
first verified fixture answer is retained, conflicting answers/references refuse,
exact retry and physical pool shutdown/reopen recover that same receipt without
another verifier call. Escaped calls cannot write; Workflow history, Blob read
counts and unexpected-effect counts remain unchanged. The verifier is explicitly
a fixture, not an actual production human channel or publicly exposed Operation.

- 574: package succeeded; Test compilation failed on one extra closing
  parenthesis in the new spec. Both exits 1, shared lock released.
  Log: `/tmp/skill.cncf.d/a-c2e9305e-7006-4238-b98a-f279b20d2130/44195-20261001T102223Z.log`.
  A disclosed bounded repair fixed syntax and checked incomplete question rejection.
- 575: package succeeded; 199/201 tests passed, two real managed cases failed
  committed readback. Both exits 1, lock released.
  Log: `/tmp/skill.cncf.d/a-344311dd-f66c-4e3e-9acd-599d7894154f/45059-20261001T102340Z.log`.
  Read-only inspection of canonical SqlDataStore showed JSON-shaped SQL text is
  decoded into Record. A disclosed repair added the same explicit textual storage
  prefix pattern used by question records; exact comparison was not weakened.
- 576 (`SMWF-HUMAN-ANSWER-DIRECT-20261001-576`): package succeeded;
  201/201 tests passed across 26 suites, zero aborted/pending. SBT and wrapper
  exits 0, shared lock released.
  Log: `/tmp/skill.cncf.d/a-34291df2-c374-4d6c-bb53-da7c77deaa86/46906-20261001T102615Z.log`.
  Summary: `/tmp/skill.cncf.d/a-34291df2-c374-4d6c-bb53-da7c77deaa86/46906-20261001T102615Z.summary.json`.

## Remaining boundary

This is focused internal writer evidence, not complete Phase validation/review,
acceptance, release, production human verification or public Service/CLI/MCP
answer acceptance. Next connect the answerable question/answer to typed public
Operations and host-only verifier assembly without exposing caller-supplied
authority. Actual human issuer/verifier, native pending-question publication,
answer/resume recovery, bounded command authority and effects, remaining runtime
transports and full Phase closure obligations remain open.
