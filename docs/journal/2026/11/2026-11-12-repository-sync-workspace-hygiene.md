# RepositorySync workspace maintenance disposition

## HYG-RS-REFRESH-PRIVATE-NAMING-01 — pending, nonblocking

Owner: sm-workflow. Some older private values and helpers in
`RepositorySyncDirectoryListPurposeSpec.scala` retain plain names rather than
the project's private-member naming convention.

Keep that existing naming cleanup separate from the prepared-candidate setup
refresh and managed workspace repair. Normalize the fixture names in a later
bounded maintenance change; this item changes no acceptance rule, execution
authority, Session state or repair allowance.
