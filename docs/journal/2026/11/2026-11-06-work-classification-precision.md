# Classify the decision an assignment must deliver

Status: selected for [Phase 8](../../../phase/phase-8.md#work-classification-in-practice--selected-for-phase-8)
as an operational correction. [Phase 7](../../../phase/phase-7.md) remains
the priority and gains no prerequisite or acceptance condition from this item.
Implementation is pending.

GoalPhase already distinguishes implementation, repair and review, and carries
complexity, domain, footprint and optional nature. Today a review becomes Critical
when complex or large; Phase review also has its own dispatch rule. The bounded
Task/Fix classifier instead assigns STANDARD complexity and SMALL footprint to
every supported semantic stage. Neither identifies whether a reviewer must close
one named finding or decide that interacting contracts still hold together.

For new assignments, keep the existing role and record the **decision being
requested** separately from difficulty, placement and model. Suggested examples:

| Role | Decision | Evidence required to complete the assignment |
| --- | --- | --- |
| Analysis | Establish a cause | Distinguish plausible causes using the observed failure |
| Coding | Implement an agreed design | Owned changes satisfy the stated behavior and checks |
| Repair | Correct a known defect | Original failure addressed without weakening the acceptance condition |
| Review | Close a specific finding | Exact finding, delta and invariant; identify anything still unverified |
| Review | Assess a contract boundary | Trace the affected authority, state, serialization or ownership across participants |
| Review | Accept an integrated candidate | Reconcile the interacting contracts, independent findings and retained validation |

A second review is not necessarily a finding closure. A one-line change to Human
choice/target pairing still needs a boundary assessment. Conversely, verifying
a prescribed rename at its declaration and two call sites is a local closure.
A failing test with unknown cause needs analysis before a known-fix assignment.
Use the five-item [implementation handoff](../../../spec/native-host-execution.md#implementation-handoff)
to express the question, scope and evidence. Do not derive the decision from file
count, model name, chat length, or the ordinal of the review.

Start with the bounded Task/Fix producer, where the current
classification is uniform. Extend to **new** GoalPhase assignments after that
distinction is verified. Preserve existing review independence, runtime-owned checks and
captured mappings. If an older input lacks this distinction, retain its existing
routing; do not infer a historical decision or rewrite a Session. Only then tune
model selection against comparable decisions in the operating profile.

Acceptance examples: distinguish a rename closure from a one-line authority
change; distinguish root-cause investigation from implementing a settled fix;
retain integrated Phase review even when the final diff is small; restore an
issued legacy Request unchanged. Evaluate reopened findings and handoffs returned
for missing evidence alongside observed token usage; no model-quality conclusion
follows from a small sample of accepted work.

When evaluating rework, distinguish an actual correction from a new assignment
or continuation after waiting; leave unsupported attributions unknown. Phase 8
also selects [review handoffs](../../../phase/phase-8.md#review-handoff-between-assignments)
and [consumer completion reports](../../../phase/phase-8.md#completion-reports-for-consumers).
These use existing evidence; model comparison and token collection retain their
Phase 9 and Phase 13 ownership respectively.

November 6 rename pilot (one run per route; same Scala baseline and request,
independent Terra review): both outputs matched the historical fix byte for
byte with no rework. Luna implementation plus Terra review took 52.6 seconds
and 313,860 total tokens, versus 41.1 seconds and 225,816 for Terra plus a
different Terra. Cache conditions differed; token totals are not charges.
Phase 8 decision: use Terra by default for ordinary coding, including fixed
mechanical repairs. Do not add a Luna coding route or fallback. Keep Luna's
established non-coding role in cross-chat waiting. The classification still
distinguishes prescribed work so its scope and acceptance criteria are clear.
