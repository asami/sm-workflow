# Goal Phase selected-window repair progress

`GoalPhaseRepairProgress` is read-only business context captured from the owning
ordered controller: `automaticCount` is its selected-window `repairs`,
`ordinaryCount` is its `ordinaryRepairs`, and `limit` is the selected plan's
`repairLimit`. It is neither cumulative history nor an AI claim, approval,
guard, counter owner or permission to run another check or repair.

R1. Every present snapshot must satisfy
`0 <= ordinaryCount <= automaticCount <= limit <= 64`. All three fields are
required integers. A null object, missing field, fractional value, invalid
ordering or null internal Option fails typed admission. Limit zero permits
only `0/0/0`. Optional absence stays absent and means unknown; consumers must
not fabricate a zero snapshot or infer counts from prose.

R2. New native Work captures the actual current controller. Retained Work must
match its actual predecessor after the selected plan refresh; an older absent
snapshot remains unknown. Repeated Advance displays the issued capture.
Human captures current controller progress separately from its prior Work's
assignment capture. A legitimate minor ordinary-count exemption can therefore
leave prior assignment `1/1/limit` and current Human `1/0/limit`. Neither value
is rewritten to make them agree.

R3. Public Advance has one optional `repairProgress`: Request uses saved Work,
Human uses the saved question, and controller-owned Wait uses the current
controller. Context-free Wait, repository child and Terminal omit it. WorkView
and HumanQuestion carry no duplicate field. The additive Work and Decision
request codecs keep their existing versions and omit absent progress.

R4. Independent review selects the actual optional Work snapshot in its target;
Human selects current progress at the source root and prior assignment progress
inside `assignment`. Result verdicts carry no counts. Passive preparation uses
the normal declaration/wire convention (`repairProgress`/`repair_progress`,
`automaticCount`/`automatic_count`, `ordinaryCount`/`ordinary_count`). Present
malformed values and contradictory aliases fail. Changed current, assignment
or review progress cannot reuse the independent evidence row. Existing issuer,
participant, source authentication, Reader permissions and CAS still apply.

E1. In a limit-two window, initial Work is `0/0/2`; selected actual failures
issue batches `1/1/2` and `2/2/2`; another qualifying failure asks Human at
`2/2/2`. An admitted ContinueRepairs opens a window with its first batch
reserved at `1/1/2`. Only actual committed next-Step progression yields
`0/0/2` for that next Step. WIP and edits within a Step do not reset counts.

E2. Executable examples cover strict snapshots, absent/present internal and
generated-public roundtrips, retained predecessor mismatch, actual ordinary
exemption, selected independent applicability and passive wire normalization.
The existing normal CLI fixture proves failed Test -> Request `1/1/1` ->
genuine pending Human `1/1/1`, stable across repeated Advance with no extra
product checks. It supplies no minted Human answer or independent review.

Actual multi-batch/Step end-to-end completion remains pending the separate
P62-FIX-E live proof. P62-FIX-D confers no Phase acceptance.
