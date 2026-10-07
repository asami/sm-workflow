# GoalPhase participant admission

Continue the same unfinished Phase 2 under retained parent-direct recovery.
Previous turn made concrete progress: authentication provenance compiled and
67 tests passed in four suites. No live SBT session remains. The direct user
"回避して" permits avoiding the skill-only conversion stop while preserving
typed allocation/permission, serial lock, scope and platform escalation for
required local validation. It does not admit unconverted hash-backed intake,
commit or publication routes. General migration admission is not claimed.

Freeze IMPLEMENT-PARTICIPANT-1 to these new sm-workflow files:

- src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseParticipantAdmission.scala
- src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseParticipantAdmissionSpec.scala

Own this journal. Read CNCF provenance/ingress and current ProfileStartAdmission,
registration codec and CML before selecting this boundary. Existing CML uses an
opaque participantIdentity string, not a role identifier; no alias mapping is
declared. For the new internal registration admission, explicitly define it as
the canonical authenticated principal ID and compare exact case-sensitive
strings. No trimming, role normalization or participant attribute mapping.
Existing selection/Start provenance still supplies the exact participant and
scope; this guard never creates or replaces that provenance.

The private guard consumes the current ExecutionContext, derives its subject,
requires authenticated provider OR trusted configured-local origin, admits
only User/Service subject kinds, and requires a nonempty canonical exact ID
match. It returns Unit: no reusable authority token, consent evidence, record,
lease, collection grant or command permission. Null/incomplete/foreign inputs
are structured securityPermissionDenied. No public Operation is exposed.

Executable spec gate: AnyWordSpec/Matchers/GivenWhenThen/table properties with
adjacent clauses. Positive scenarios must use actual IngressSecurityResolver
with configured provider/local wiring, not inaccessible mark fixtures.
Negative scenarios cover exact ID/case/whitespace mismatch, spoofed public
attributes, controlled-test fallback and copied security contexts. No SQLite
or storage write is needed for these actor-only scenarios. Preserve all existing
source changes; no CNCF production change is required this turn.

Validation prerequisites: CNCF's changed 0.5.3-SNAPSHOT supplies the new public
read-only trusted-local predicate and record capability; refresh its unchanged
development artifact with publishLocal before compiling sm-workflow. This is
local dependency-artifact refresh, not public release. Then package/execute
the existing Start accumulator plus the new participant suite. Use the current
serial wrapper directly under the admitted recovery/exception, normal caches,
fresh IDs and complete terminal summaries. Do not edit while SBT is live.

This is bounded-settled internal consumer work, using the retained parent lease,
not completion of whole Phase PLAN or a commit gate. Parent model/effort are
not directly observable, standard mode, /root is the explicit direct owner.
Independent review, public registration ABI/reader/writer integration, full
validation and Phase release remain required. Stop on a needed alias policy,
unadmitted root or new semantic change rather than infer one.

## Implementation and focused verification

The two frozen files were added. The guard returns Unit only after the current
subject has admitted provider/trusted-local origin, authenticated state,
User/Service kind and an exact canonical principal/participant match. No origin
marking or authority issuance API was added to the component. The four scenarios
use actual configured ingress and reject hostile participant attributes,
case/whitespace/role aliases, controlled-test claimed authentication, equal
security copies and missing context/participant. Clause placement was checked
before validation; git diff --check passed.

Invocation `SMWF-AUTH-DEPENDENCY-REFRESH-DIRECT-20261001-528` refreshed CNCF's
unchanged 0.5.3-SNAPSHOT through publishLocal. Both exits zero, lock released;
the complete summary was read at
`/tmp/skill.cncf.d/a-07a658c5-4f0a-41be-a7c3-159f4d33514c/16792-20261001T060032Z.summary.json`.
Only the existing local development artifact was updated, not remotely released.

Invocation `SMWF-PARTICIPANT-ADMISSION-DIRECT-20261001-529` compiled and packaged
sm-workflow, then passed all 144 tests in nineteen suites. The new participant
suite's four scenarios passed alongside the unchanged eighteen-suite managed
Start/review/repair/authority/Factory accumulator. Zero suites aborted and zero
tests failed/canceled/ignored/pending. Both exits zero and lock released. Full
summary and new-suite log section were read:
`/tmp/skill.cncf.d/a-94daf9e6-7a69-429d-bb3f-4590e18fdd83/17230-20261001T060109Z.summary.json`.
Existing unchecked generic-pattern warnings appeared in previously written
review/repair sources; no new participant compilation warnings were reported.
No source edits occurred while SBT was live. Both invocation identities were
appended to the retained ledger and both sessions are terminal.

This establishes the internal actor prerequisite, not a dispatched registration
Operation. Its future consumer must obtain ExecutionContext from that current
ActionCall and run this guard before record/resource effects; a Factory-time
context or stored claimed actor cannot substitute. Selection/Phase/repository,
retained Start and collection write admission remain separate checks. The new
registration reader, CML/Operation/writer and committed success readback remain
unimplemented. No fixture or 144-test accumulator is claimed as their evidence.
Independent review, full Phase assurance and release remain outstanding.
