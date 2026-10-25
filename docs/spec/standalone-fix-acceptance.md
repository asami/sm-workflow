# Standalone sm-fix acceptance

Date: 2026-10-24 (Asia/Tokyo)
Status: adopted specification; standalone entrypoints and acceptance pending
Plan: [standalone fix delivery](../notes/standalone-fix-delivery-plan.md)

Related: [sm-goal-task](goal-task.md) reuses this execution path for ad hoc
development from journals or other inputs, but adds integrated Task review and
actual local commit as completion requirements. Those requirements do not become
implicit obligations of a standalone sm-fix request.

## One request is one Step-equivalent unit

One standalone repair request is one bounded unit of implementation, validation
and acceptance. Related changes may form several repair attempts within that
unit; a file, finding or attempt does not create another Step or review ceremony.
Step-equivalent describes acceptance semantics, not a requirement to create a
Phase, fabricate a Step commit or drive the complete GoalPhase lifecycle.

Reuse Phase 6.5 FIX, deterministic assessment and conditional review, Phase 6.6
diagnosis/information retrieval, existing repair assignments and runtime validation.
Extract/reuse their shared behavior where necessary. Do not simulate a completed
GoalPhase Step to obtain standalone acceptance: GoalPhase's actual commit and
completion semantics remain unchanged.

## Thin entrypoints and scope

| Entry or selection | Contract |
| --- | --- |
| `sm-test-fix` (planned) | Run or consume attributed selected validation, diagnose failures, repair and validate within the requested scope |
| dirty selection | Select changed repositories/work within explicit user scope; dirty state is not permission to modify every uncommitted change |
| focused/full selection | Select validation scope through existing test metadata/policy; it is not a separate repair engine or a review level |
| `sm-diagnose` | Phase 14 remains investigation-only unless the user also authorizes repair; an authorized proposal can enter this same repair route |
| `sm-review-fix` (planned) | Consume identified review findings and source context, repair within scope, verify their disposition |

Dirty/focused variants do not require separate skills. A full-test request retains
its declared full-test obligation; intermediate repairs use affected focused
checks and reuse applicable results. Do not silently weaken an explicit request.
Multi-repository selection may consume Phase 7 capabilities when delivered; it
is not a prerequisite for the first single-repository route.

An existing Phase's repair remains inside that Phase and its current Session.
Standalone entrypoints must not be used to escape its validation, review, counters
or authority. The skill is a caller/semantic adapter; sm-workflow owns mechanical
test selection/execution/waiting/result capture and progression through CNCF.
No caller-built SBT runner is substituted for this product route.

## Test and review acceptance

Main path: bounded diagnosis/proposal -> related repair batch -> compilation,
required focused behavioral tests and applicable lint -> deterministic assessment
-> conditional independent semantic review -> request acceptance.

For code changes, selected focused behavioral tests must actually execute and
pass; compile-only or an empty selection is not success. Compilation failure
retains affected tests as not executed, not failed/passed. Preserve actual results
of tests that did run. Normal incremental compilation is sufficient for its scope.
Use applicable Scala/CAR lint policy; no unconditional extra full/heavy suite or
CAR lint in every inner repair loop. Documentation-only work explicitly selects
relevant checks without fictional Scala test results.

Every request receives a completion assessment against its stated purpose, actual
validation and unresolved obligations. Mechanical assessment adds no AI call.
Independent AI review occurs only for an explicit user requirement or remaining
semantic judgment that must be settled for acceptance. Examples include an
unjustified expected-value/test-selection/suppression change, unsettled public or
business behavior, or a combined effect not covered by accepted evidence. These
are signals, not universal review gates: prior settled decisions and applicable
evidence are reused. Do not call AI merely to approve review omission.

Ordinary local changes satisfying the conditions finish without independent AI
review. Record review as not required with the actual basis, never fabricated PASS,
AI CLEAN or a reviewer identity. When needed, review the bounded repair as a whole
once; subsequent review covers only findings, repair delta and affected meaning.
The repair author does not serve as its own independent reviewer.

For review-origin repairs, preserve each original finding and link it to the
change and verification. Mechanically established resolution needs no additional
AI review; unresolved meaning receives scoped review. Do not repeat the original
whole-repository review. A review failure returns to the same repair unit, not a
new request or a reset allowance.

No automatic Phase Review, Release Review, commit, push or release is added.
Explicitly requested commit/release uses its own existing authorization and
delivery route, reusing applicable acceptance evidence rather than restarting
review. Standalone accepted changes may remain uncommitted.

## Retention, counting and completion

Reuse common Journal/Evidence/Finding and existing WorkOrder/Continuation identity
and repair policy. Retain requested scope, selected checks and their attribution,
actual changes, finding dispositions, review requirement/outcome, unresolved items
and completion status. No new ledger, hash envelope or mandatory AI transcription.
Apply the existing counting-window/convergence policy; no reset on retry, provider
change, review or intermediate commit. Phase 9 enhances observed convergence and
routing; it is not a prerequisite for the initial bounded repair route.

Test success does not automatically resolve a causal defect Finding. Preserve
the distinction in [recovery observations](recovery-observations-and-cause-resolution.md).
Unknown execution or missing required checks cannot be accepted as success;
continue via existing observation/information/decision routes as appropriate.
An in-scope repair needs no new per-attempt approval; actual scope/authority
expansion retains its existing decision boundary.

Adoption preserves ongoing work, current Session/Plan/continuation, accepted work,
history, repair counts and applicable validation/review. No re-Start, accepted
Submit replay, private store edits or redundant successful checks solely to adopt
the new entrypoints. Historical evidence keeps its original meaning.
