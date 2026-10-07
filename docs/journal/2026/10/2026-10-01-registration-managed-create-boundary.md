# Registration managed Create admission and readback

Continue the existing Phase 2 goal and retained limited-recovery scope. Previous
turn made implementation and validation progress: private registration codec,
Start witness correlation, packageBin and 140 focused tests. This turn inspected
current source, not a live process; no SBT invocation is pending.

The next production dependency is an independently admitted Create-only managed
storage capability, not widening the current application read declaration.
Current evidence:

- `ManagedHostRecordRead.loadC` applies the application read policy and dispatch
  binding, then obtains a raw provider. It deliberately issues no storage grant.
- `GoalPhaseHostRecordReadPolicy` restricts exact purpose/store/collection from
  resolved host configuration and explicitly disclaims framework write authority.
- CNCF `DataStoreSpace.dataStore` routes by `isAccept`, with registered/default
  fallback; SQL `isAccept` returns true. Routing alone cannot prove owner or
  collection permission and must not become the writer capability.
- `Subsystem.admitWorkflowStorageC` captures exact instance/Continuation grants;
  `EventStorePersistenceDeclaration.policyC` permits event/index side Creates.
  Neither currently admits standalone submission registration.
- SQL `create` prepares schema, checks existence and inserts; `_with_connection`
  obtains/closes a connection without introducing an explicit transaction.
  A racing INSERT failure is not explicitly normalized there. Provider-level
  atomic record behavior, duplicate classification and readback remain unproven
  for the production registration path.

The installation design now fixes the capability and outcome protocol: captured
Subsystem authority and active managed lease before access; narrow load/create
only on the exact registration scope; retained-request comparison; committed
readback before publication; and no absence/rollback/success inferred from an
unclassified Create failure. Same-ID race recovery must not use a new identity,
upsert, compensation or fabricated authority. These requirements retain the
shared pool and existing Factory post-resolution initialization decision.

Owned delta is this journal and the managed-Create subsection of
`docs/notes/goal-phase-host-record-installation.md`. No source/API/Operation,
authentication, host grant or physical storage was changed or activated. No
new behavior scenario was authored, so executable-spec authoring is not
applicable to this documentation-only delta. No SBT was rerun; prior validation
is evidence only for the prior private implementation. Production capability,
Operation integration, actor admission, focused integration specs and full
Phase assurance remain unfinished. Preserve all unrelated dirty work and make
no commit/publication/completion claim.
