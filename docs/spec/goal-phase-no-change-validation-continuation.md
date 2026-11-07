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
