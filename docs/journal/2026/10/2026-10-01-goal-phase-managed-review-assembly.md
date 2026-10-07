# GoalPhase application managed review assembly

Continue Phase 2 after exact local refresh 435 and consumer 436. Freeze new
application source GoalPhaseManagedReviewAssembly.scala and the complete existing
GoalPhaseManagedSuspensionOperationSpec.scala (read before editing). Extract the
proven factory-time review Operation composition into application main code.
The helper consumes ComponentCreate and explicit application declaration,
producer ABI, clock and ordinary event-record callback. It delegates storage
admission to WorkflowComponentStorage, binds the typed generated submitStepReview
Operation and returns only its OperationDefinition.

Admit the ABI and opaque completion/issued bindings before probing instance
storage. The instance SPI is read-only for this completion route; configuration
drift, independent create and append reject, and the legacy UoW supplier throws.
No in-memory store/runtime, implicit declaration, raw provider/pool, request-
supplied authority or no-op events callback default. The completion response
continues to WAIT for deterministic closure rather than executing a Git command.
Host event policy still chooses persistence, not this assembly or the sender.

Use the helper from the downstream create_Core executable spec and preserve
typed completion/reopen/replay assertions. Add admission examples for missing
ports and foreign declaration. This is the production-code review composition
building block, not a complete GoalPhase service or default launcher factory.
Start selection, invocation/closure authority storage, repair and deterministic
commands remain separate implementation obligations, not optional acceptance.

## Pre-review mechanical fixture repair (437)

Invocation 437 compiled the new main source but test compilation failed at the
new foreign-declaration assertion: placing shouldBe on a new line after the
closed call creates a separate Scala expression. No tests ran. SBT/wrapper 1/1;
serial lock released. Freeze M1 repair attempt 1 on this spec only: move the
infix shouldBe to the expression line and retain the same failure matcher. No
product, authority, assertion or scope change. Re-run the same five suites.
Log and summary are under `/tmp/skill.cncf.d/cncf-sbt-adc95568eb26982c07a89dd316d85255eb8f812f3a4098f647a24e89986d094c-8c2cd7047a058561a92e25b1568225d3/6903-20260930T221440Z`.

## Focused consumer closure (438)

Invocation `SMWF-GOAL-MANAGED-REVIEW-ASSEMBLY-DIRECT-20261001-438` compiled
the repaired spec and passed 42 tests in five suites. SBT/wrapper 0/0, serial
lock released and git diff --check passed. The single M1 syntax repair is
resolved. Actual create_Core now calls application main-code assembly rather
than recreating opaque-binding wiring in the spec. Generated review completion,
different-candidate rejection, retained revision/history, host reopen and replay
rejection still run through Subsystem dispatch. New admission checks reject
missing ports/event callback and foreign collections. Successful assembly calls
no events callback and creates no Workflow instance.

- log: `/tmp/skill.cncf.d/cncf-sbt-69820323be70a4744b1812ab54493490a66ef150cc94b81bb86851ce2f203642-5526b0b7e643832b7c2940314d1105ff/7664-20260930T221542Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-69820323be70a4744b1812ab54493490a66ef150cc94b81bb86851ce2f203642-5526b0b7e643832b7c2940314d1105ff/7664-20260930T221542Z.summary.json`

The default application factory remains unchanged and therefore still does not
provide this managed Operation. Next integrate Start and managed issue with
explicit host selection/invocation authority, and retain the automatic closure
authority requirement. Do not replace those authorities with test maps or
infer human selection from request bytes. Full service/launcher/repair/command
implementation and all Phase acceptance gates remain open.
