# Native human completion query and pure closing selection

## Boundary and scope

The prior goal turn made progress through managed native completion and Factory
composition (620: 195/195). This turn continues the same Phase 2 implementation
in the parent under the user's limited-recovery direction. No agents, shared
skill edits, new mutation roots, commits, push or acceptance are introduced.
Cozy is a validation-only prerequisite; only its runtime classpath was refreshed.

The native completion retains the answer and independently issued evidence
reference, not the original full verification receipt. Recovery must not invent
that receipt, renew its validity, resubmit a human choice or consume another
continuation. A separate read-only observation Operation is added within Phase
2's explicit status/history/result and CLI scope.

## Defined Operation and implementation

CML declares `GoalPhaseService.getGoalPhaseHumanDecisionResult` as QUERY, with
`GoalPhaseHumanCompletionQuery` explicitly marked `input-kind :: QUERY`.
The closed bounded query carries only schema and native question identity.
Generated Service declarations provide its public interface; it is not a new
progression action, and the existing GoalPhase Workflow/Provided ABI remains v3.

The configured product Factory binds the query even without a human verifier.
The actual QueryAction loads the co-located issued Decision, native completion
and current instance through canonical managed bindings. It reads host selection
under the actual current-call lease and admits its participant against genuine
ingress provenance. Copied authentication and missing completion are refused.

`GoalPhaseNativeHumanCompletionObservation` performs only pure structural
projection. It reconstructs validated read-only history prefixes, re-prepares
the exact HumanAnswered successor from the retained question/result, and checks
its complete canonical bytes against the stored completed prefix. Later valid
progression may coexist; completingRevision and currentRevision stay distinct.
No prefix is persisted, no history is reset, and no verifier is called.

The response names the original question, choice, human evidence reference,
completing/current revisions, and exact native Decision/completion JSON bytes.
It is a historical observation, not a new receipt, human permission or command
grant. Original-receipt recovery remains unimplemented where that separate
assurance is needed.

Human completion admission is also moved out of ClosingProgram.selectC. The
per-call codec performs host reads and human verification before selecting a
closing program. Selection then checks the exact admitted result, computes the
successor purely and compares canonical next-state bytes. CNCF owns the later
guarded atomic mutation. This preserves independent call/actor admission while
removing provider reads and external verifier calls from closing selection.

## Executable evidence and limits

The pure scenario checks exact historical completion, later revision, mismatched
closing type/revision/history, absent completed prefix and closed query decoding.
The integration scenario queries actual Start/review/native question/completion,
checks no extra verifier call or state write, rejects authentication copies and
missing native identity, then reopens the actual shared SQLite store without any
human verifier. Normal CncfRuntime CLI selector/`--json` dispatch resolves real
configured local ingress and returns the same result as Subsystem dispatch.
No server is required in this in-process CLI test.

Selection installation and human issuance remain explicit test-only fixtures.
This is not a separate-process launcher/CAR acceptance or MCP/server test.
Production human issuer/verifier, automatic native question follow-up, uncertainty
fault injection, command authorization and complete Phase assurance remain open.

## Validation ledger

- 621 stopped before compile/tests because the existing Cozy development
  classpath was stale; terminal exit and shared lock release were observed.
  `/tmp/skill.cncf.d/a-d8b006d5-e167-4df3-9b67-9810531491b8/52534-20261001T125940Z.summary.json`.
- 622: Cozy `cozyExportRuntimeClasspath` succeeded with exit zero and lock
  released. No Cozy source edits or test claim.
  `/tmp/skill.cncf.d/a-0a2f3a29-86cd-4076-b145-c5c85fe38fe7/52981-20261001T130036Z.summary.json`.
- 623 stopped on missing CML Query input-kind. Added the required declaration;
  empty-provenance-output diagnostics were subordinate to failed generation.
  `/tmp/skill.cncf.d/a-c1e2d956-f66a-4367-b44c-7e830c703ef4/53681-20261001T130155Z.summary.json`.
- 624 generated 88 sources and compiled product code; test-only counter access
  failed due to widening to the verifier interface. Moved counter outside that
  anonymous interface; no permission or validation weakening.
  `/tmp/skill.cncf.d/a-8c051839-f309-47bb-9826-5ac5506df7a6/54156-20261001T130259Z.summary.json`.
- 625: 33/34 passed. Normal CLI correctly refused a host lacking explicit
  standalone user-mode. Declared that mode only on the CLI reopen fixture,
  retaining genuine configured ingress and all native participant checks.
  `/tmp/skill.cncf.d/a-16c5f6c9-4fbc-4327-baec-00cc8217274c/54716-20261001T130418Z.summary.json`.
- 626: focused 34/34 in three suites; exit zero and lock released.
  `/tmp/skill.cncf.d/a-739d7d61-0858-44e6-9444-88301db4c5bf/55343-20261001T130550Z.summary.json`.
- 627: GoalPhase accumulator, including pure closing selection, passed 196/196
  in 27 suites, exit zero and lock released.
  `/tmp/skill.cncf.d/a-a67916bf-89a3-4c92-a77d-56c76c6db1aa/56228-20261001T130750Z.summary.json`.

Every SBT attempt uses a fresh permission identity and the shared serial wrapper.
No active SBT was restarted merely to recover truncated output.
