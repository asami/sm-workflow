# Review evidence compatibility for historical SBT receipts

A historical typed SBT receipt can encode an absent optional estimate as `"estimate": null`; a current public projection may omit that field. Both decode to the same absent estimate. Independent review evidence compares this typed absence consistently at the review scope boundary. The retained receipt and the storage codec preserve the historical representation. Actual estimate values remain part of the selected review target.

This equivalence applies only to the estimate in typed SBT facts of the selected review receipts. It does not normalize arbitrary nulls or alter worker, issuer, occurrence time, purpose, result reference, component identity, candidate, receipt identity, or other review authority checks. It does not authorize another review result or replay a successful check.

The regression specification covers selection and independent evidence boundaries. Its managed fixture seeds a specification-owned historical prefix and specifies public review preparation, repair Submit, ordinary Repair, fresh typed verification and independent accept while retaining the historical checkpoint. Actual native and installed results require separate execution; the fixture itself is not evidence of those results.
