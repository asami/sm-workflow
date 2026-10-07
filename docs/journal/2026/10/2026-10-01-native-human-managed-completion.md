# Native human managed completion and Factory composition

## Scope

This continues the user-directed parent limited recovery, without agents,
shared-skill edits, commit, push or release. Existing observation human
Operations remain unchanged. No verifier is synthesized from configuration,
principal attributes, a decoded receipt or a skill response.

## Managed native Operation

`GoalPhaseManagedNativeHumanCompletion` binds generated
`GoalPhaseService.submitGoalPhaseHumanDecision` through the CNCF native
Decision completion Operation. Input is the existing typed ContinuationResult
JSON carrying the exact answer. Its resultReference identity names independently
issued human evidence; it is not a request-provided receipt or authority claim.

Each actual call obtains its own binding, issued native question and current
instance. Selection is read through the host's current-call DataStoreSpace
reader, and its participant is admitted independently against actual ingress
provenance. The explicit host verifier checks exact Component instance,
question, choice, submitting participant, issuer, purpose and validity.
The pure selected closing program grants no command permission or Step closure.

Native managed completion atomically retains the typed answer/result reference,
consumed continuation and adjacent Active HumanAnswered history through the
existing shared physical provider. There is no independent history append,
WorkOrder claim fallback, cursor initialization or shadow answer collection.
After-commit response encoding requires the observed instance to equal the
selected successor and returns the verified structural receipt under the
declared GoalPhaseHumanAnswerResult shape.

The Factory's host-only `nativeHumanVerifier` optional dependency opts into this
Operation. Missing verifier leaves it unadvertised; null dependencies are
refused. The configured Factory does not require the observation question/answer
collections to enable the native binding. The existing observation opt-in is
independent, and supplying a native verifier enables no human issuer or MCP.

## Executable evidence

The integration scenario now exercises both the original competing-writer path
and a separate answer-completion path. Both derive their question from actual
Start, issued review, retained accepted review and canonical shared SQLite.
The native answer path uses an explicitly test-only independently retained
receipt. Unissued reference and copied authentication refuse without advancing
revision four. Successful Subsystem dispatch returns that exact receipt,
retains the typed result and revision-five HumanAnswered history, rejects replay,
and preserves native completion after physical pool shutdown/reopen. One pool,
zero remaining leases and zero unexpected command callbacks are asserted.

618/619 used a scoped test Component Factory for the new binding. 620 replaces
that installer with the actual configured product Factory host-record path.
Selection installation and human issuance remain honest test-only fixtures.
This is not evidence of a production issuer/verifier, CLI/server/MCP transport,
automatic native follow-up or full CML constituent lifecycle execution.

The native completion stores the typed answer and evidence reference, not a
second copy of the entire verified receipt. Original receipt availability,
issuer stability, uncertain response recovery and an authorized receipt-query
Operation still require explicit completion/recovery design and tests. Current
duplicate submission refuses rather than claiming response recovery. No Phase
acceptance or checklist promotion is made.

## Validation

- 616: compile stopped on generated/CNCF ABI type mismatch; fixed by the same
  source-correlated typed projection already used by review/repair bindings.
  `/tmp/skill.cncf.d/a-8b91126b-bd17-460a-8644-f85949cd7c89/46496-20261001T124636Z.summary.json`.
- 617: product compile passed; test compile stopped on Scalar extractor used
  as a type. Replaced with exact result/receipt decoding, without weaker checks.
  `/tmp/skill.cncf.d/a-46309beb-17de-4302-8dfb-1be4b61b27c5/46848-20261001T124718Z.summary.json`.
- 618: focused 30/30 in two suites; exit zero, lock released.
  `/tmp/skill.cncf.d/a-2714c939-0908-45de-bdce-a32d58d39dea/47270-20261001T124759Z.summary.json`.
- 619: accumulator 195/195 in 27 suites; exit zero, lock released.
  `/tmp/skill.cncf.d/a-303eed69-a753-4df0-9729-6d0eb6675d06/47656-20261001T124848Z.summary.json`.
- 620: accumulator with configured product Factory integration passed 195/195
  in 27 suites; product/test compile passed, exit zero and lock released.
  `/tmp/skill.cncf.d/a-482ce7cb-4109-4661-b712-7a2ce7253ce4/48454-20261001T125038Z.summary.json`.

All invocations used fresh permission identities and the common serial SBT
wrapper. Compilation failures were terminal with lock release before repair.
