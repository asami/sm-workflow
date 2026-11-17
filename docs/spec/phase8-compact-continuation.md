# Compact results and portable continuation

P8-04 source contract, authored against issued `ordered-implementation:989`,
revision `990`, Session `a38c308c60a36de8c7b33336ca8641c7`. This document
records authored behavior, not validation or native acceptance.

## Control and selected detail

Use `scripts/native-goal-command.py --presentation summary` with the existing
selected action and arguments for compact caller exchanges. The compatibility
presentation when this option is omitted is `detail`; callers consuming complete
stdout JSON may explicitly select `--presentation detail`. This is a display
choice, not another execution or result protocol. `summary` keeps the actual
Session, kind, outcome/acceptance when supplied, message, available repair progress,
state stage/counts/warning, and the actual issued next operation. It includes the
existing `response.json` as `detailResource`, the validated existing
`workflow-session.json` as `sessionResource` when a Session route is selected,
and the outer Session identity. It preserves actual retained-work/import references
and their existing public read operation. Missing outcome or next operation is
not synthesized. Long presentation strings are explicitly marked previews;
semantic detail is never overwritten or truncated.

The raw response and stderr remain in their ordinary caller-owned artifacts.
Read the selected lossless response as JSON when semantic detail is needed.
For a retained-work reference, use its existing `readWorkflowRetainedWork`
operation, retaining the exact reference/revision. Semantic result projection
continues through [RSP-1's existing public operation](semantic-result-submission.md):
summary for control and `public-result` for complete DTO JSON text. A display
summary is never accepted as a work Request, public result or execution evidence.
The exact issued worker envelope and complete semantic fields remain authoritative.
No private codec, transport acknowledgement or display status proves success.

## Progress, wait and handoff

`complete` still owns a finite one-Submit/one-Advance sequence. Runtime Advance
owns consecutive deterministic transitions and selected validation; the caller
returns the actual Request, Human, authority refusal, Wait/UNKNOWN, Incomplete or
Terminal boundary. Compact output does not add an AI-controlled Advance loop,
executor, retry, accepted-result replay or semantic repair charge. A Wait summary
has no inferred retry operation. Use the existing runtime invocation's collection
owner or existing authorized cross-chat waiter; retain its actual result before
continuing the same Session. A notification is not successful Goal continuation.

At a meaningful boundary, carry the actual active Goal (when the caller has one),
Session route, issued continuation identity/revision, response/detail references,
accepted prefix, repair allowance, outstanding owner/request and actual next
operation in the existing handoff. Do not create a replacement Goal or Session,
reset counters, duplicate the waiter, or compact away unresolved effects. Restore
from those exact references before using the ordinary operation. The summary
cannot invent a Goal identity: it preserves the Session facts available to this
caller; the enclosing host retains its actual Goal and authorized waiting state.
Desktop automatic compaction/wakeup is best effort and unqualified here. No host
UI automation or new context/approval ledger is introduced.

## Terminal consistency

The bounded native owner saves terminal completion evidence before returning.
Every terminal-producing candidate/assessment recursion loads the saved payload
before composing its next public view. First inline state and later passive
Advance therefore use the same saved evidence references, Journal/storage warning,
receipts, counters and terminal identity. Missing Evidence or Journal support
remains a warning, without replaying successful checks or rewriting old history.
The existing observed-check and Task commit paths already load their retained
payloads and remain the same owners.

## Resource intake and owed qualification

`DelegateTaskDeliveryService` currently selects exactly one of `input` and
`inputResource`, reads Resource content through the existing authorized finite
UTF-8 reader, then applies observation admission. The CML parameters are optional
transport alternatives. The reported installed `namedinputrequired` refusal is
not reproduced or classified as a supported current-source regression by this
authorship assignment. Do not generalize it to all Resource routes or weaken
input authentication. The source-launcher delivery specification now includes
Resource dispatch, ambiguous transport refusal, crossed destination refusal,
unchanged passive recollection and the existing complete large result intake.

Authored executable specifications:

- `BoundedWorkNativeOutcomeSpec`: first nonterminal Assess-to-terminal response
  equals saved state and later collection; normal/missing Evidence/missing Journal;
  original checkpoint/history, receipts, counters and no staging/check/commit replay.
- `test_native_host_progression.py`: compact finite completion returns at actual
  Wait with full large JSON retained, exact Session and artifact links; actual
  issued next operation on Request; invalid presentation refuses before execution.
- `NativeDelegateTaskDeliveryCliSpec`: the Resource positive/negative intake cases
  above through the public source launcher.

No checks were executed by the implementation worker. Parent-owned normal Compile,
required tests, independent review and connected P8-05 qualification remain due.
RSP-1's existing projection/refusal fixtures are reused; no accepted Session is
replayed. P8-05 also retains both explicit-only P8-03 REMOTE cases. No global
installed caller adoption, desktop wakeup, Phase closure or savings is claimed.
