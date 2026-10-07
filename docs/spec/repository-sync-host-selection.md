# RepositorySync host selection

Status: verified by fresh1038 all-three/common-guard validation:1274/1274 tests
in146 completed suites, zero failures/aborts. Parent admitted complete current
typed result/consumed permission/SBT0/wrapper0/released shared lock and original
log/summary.1037 compile failure remains unchanged history. This is not standard
three-profile ComponentFactory installation or Phase acceptance.

`RepositorySyncManagedServiceAssembly.bindHostC` composes the native service with
the normal read-only `ProfileSelectionDataStorePort.RepositorySync` rather than
an injected selection callback. The owning Subsystem's resolved configuration
declares `sm-workflow.repository-sync.host-records.store-name` and
`sm-workflow.repository-sync.host-records.selection-collection`. The collection
is separate from native instances/continuations and uses the same logical store.
Flat and nested resolved declarations mean the same thing; another profile's
settings, live system properties and client arguments are not substitutes.

On Start, `ManagedHostRecordRead` borrows the incoming ActionCall's
DataStoreSpace, binds the component's declared logical store, reads the selected
typed `ProfileSelectionJsonV1` row and restores the prior binding. It opens no
physical provider/pool, performs no write and retains no call context. Host read
configuration is a restriction, not a framework storage grant, Human consent,
review admission or command authority. Existing Start admission still binds the
exact selected input, Operation, Workflow/revision, participant and invocation.
Advance continues from the original managed native Start, not a new selection.

Executable specifications use actual public Operations, owned Git repositories
and the canonical Subsystem SQLite pool. Only private fixture preparation writes
selection rows; production readers never install/upsert them. A physically
reopened host reads the row and retained native result without installer or
refetch. Duplicate Start observes the original instance at its current native
revision, not the stale initial response text. Missing/malformed/crossed-input
rows refuse Start before Workflow Git
observations. All earlier native progression/conflict/check/review/convergence
fixtures now exercise this normal host reader. Fixture selection installation
is not production Human ingress; physical pool reopen is not separate JVM proof.

Normal all-three ComponentFactory and trusted process-admission bootstrap are
still unfinished. CNCF ScopeContext currently has explicit driver/admission SPI
but ordinary CLI construction does not install them from runtime configuration.
Do not compensate by creating LocalProcessExecutionDriver inside the component
or calling a process/SQLite provider directly. That remaining runtime assembly
must precede genuine ordinary installed command execution.
