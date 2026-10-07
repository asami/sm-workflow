# Selected-only event record preparation

WorkflowSelectedEventRecords now connects selected persistent events to
canonical Create proposals without borrowing a raw provider or bypassing
DataStoreSpace. Sequences are overflow-safe contiguous proposals, not allocated
ranges; the managed host still owns cursor revision admission and atomic commit.
Transient/empty batches create no event effects or cursor movement.

Complete ordered durable readback is compared with retained canonical bytes
before mixed-order delivery candidates are reconstructed. This pure function
does not prove commit or dispatch authorization. The codec helper preserves
private delivery keys through copying and still rejects unsupported keys.

## Validation

Focused-294 passed 30/30 tests in four suites. Final focused regression-295
passed **50/50 tests in six suites**, zero failures, SBT exit 0, wrapper exit 0,
lock released: WorkflowSelectedEventRecordsSpec, WorkflowEventSelectionSpec,
EventStorePersistenceHostSpec, EventRecordJsonV1Spec,
DataStoreConditionalMultiGuardPlanSpec and DataStoreConditionalUpdateSpec.
The codec example includes unsupported private-key preservation through both
Workflow selection and sequence preparation. This is not full-suite validation.

295 receipt:
`/tmp/skill.cncf.d/cncf-command-execution-395a1ee9cc383f160dd15941585434afc4732192720bb8b7b158e9838b056a95-6e4c7427c591e9fc72483387b3eec2fd/command-execution-sha256-e1cb2778f7bc7448f47863976f8e2fe0cc2fb7637e4261195b867f9380af3f78`.
Log:
`/tmp/skill.cncf.d/cncf-sbt-2de3920d251fea6aaf6fc69f9ac4ba2134c2bb14b976b71e88977df7cab4be0b-2de7fa5a5aac7438f2562d11d920e340/70508-20260930T002904Z.log`.
Runner: registered cncf_command_runner, gpt-5.6-luna / medium,
`/root/sbt_receipt_34889b98923a403ab819`, healthy route. Subsequent edits update
evidence/documentation only. No publication, Git mutation or runtime activation.
Actual provider, cursor/index allocation, selected commit/dispatch, S12/P2-U15,
P2-U16 and P2-U05 remain open.
