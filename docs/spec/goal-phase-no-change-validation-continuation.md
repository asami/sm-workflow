# No-change Step selected-check continuation

Date: 2026-11-07

An ordinary Step can retain a candidate with no changed paths and an empty diff.
If its selected check actually exits with a repairable failure, its repair
context carries those unchanged source facts and the complete typed validation
payload. The ordinary continuation still requires the retained candidate to match
the validation candidate and requires genuine selected receipts. Empty paths
with a nonempty diff, a nonempty path list with an empty diff, and malformed or
non-repairable validation are refused. This representation neither retries an
unknown effect nor changes a Session, candidate, accepted Submit or counters.

An independently admitted Step review may also find an original-purpose defect
in an unchanged candidate. Its resulting RepairStep retains that review's empty
path list and empty diff, prior review, selected Step, findings and acceptance
boundary. The semantic return accepts this paired empty source without requiring
invented edits to the reviewed candidate. Existing finding dispositions and
required quality observations still apply. Unpaired paths/diff and crossed Step
or missing finding accounting remain refused; this does not extend empty-source
admission to Phase-wide or publication repair.
