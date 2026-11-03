# Default publication and final RepositorySync ordering

The normal CNCF runtime already resolves the SBT executable from PATH. The
Component now supplies the missing ordinary local-publication target, record
grant and typed PublishLocal program. Hosts overriding storage or process plans
must still admit their selected resources; no separate publisher host is needed
by the standard declaration.

Publication owns artifacts and publication metadata, never Git commits. An
explicitly configured `beforeSync` target runs at RepositorySync's final clean,
integrated source frontier. The same native Session retains publication, commits
its resulting ordinary catalog files, pushes and observes final clean/equal
local, tracking and remote facts. Unfinished source changes, crossed outputs and
unknown effects prevent completion. Retained successful effects are collected
without replay. Existing hosts omit `beforeSync` and retain their old behavior.

Verification on 2026-10-30:

- Normal LocalPublicationService generated 386 sources, compiled the production
  changes, completed publishLocal with Exited(0), and updated the installed
  runtime. Earlier generation/codec failures remain in the task-local records.
- Normal RepositorySync typed SBT/UoW executed three focused suites, ten tests:
  RepositorySyncPublicationSpec, RepositorySyncPublicationCliSpec and
  Phase69CleanRepositorySyncSpec. All ten passed, with actual Exited(0).
- The installed-CLI case used a disposable plain SBT project and local bare
  remote. It published once, committed actual catalog output, pushed and closed
  clean/equal. A new process collected terminal completion without republishing.
- Existing equal, local-ahead and fast-forward behavior and preservation of
  unrelated dirty work passed. Unsafe output kinds and lifecycle order were
  checked separately. This is focused verification, not a full-suite result or
  an independent review.

Evidence is retained under
`target/sm-workflow/default-publication-sync-20261030/`, including build05 and
qualification03 typed results and decoded logs. Qualification03's outer
disposable merge correctly stopped at its issued independent triage after the
selected tests passed; it was only the test-execution host. The inner installed
CLI publication scenario completed its own full synchronization.

No existing consumer Goal, Session, accepted result, history or repair counter
was changed. Final publication metadata is committed after the source commit
and before the final push/clean observation.
