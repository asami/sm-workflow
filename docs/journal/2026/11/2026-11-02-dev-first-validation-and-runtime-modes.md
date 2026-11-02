# Dev-first validation and explicit deployment-mode qualification

Date: 2026-11-02
Status: adopted direction; no execution or Phase acceptance in this record

The developer selected dev mode as the normal profile for sm-workflow-managed
development tests. This keeps early work usable without requiring real account
providers or cloud configuration, while shared authorization still runs.
Test explicitly selects controlled fixtures or declared test-specific behavior,
not an automatic consequence of invoking tests. Demo/production scenarios are selected where the changed
boundary or actual deployment acceptance requires them.

Canonical [validation contract](../../../spec/development-mode-validation.md)
and [workstream](../../../phase/development-mode-validation-plan.md) connect the
[Textus common mode decision](https://github.com/asami/goldenport-cncf/blob/main/docs/journal/2026/11/2026-11-02-runtime-mode-security-defaults.md)
to ordinary validation and Phase 7 consumer work. Existing test-purpose metadata,
Slice selection, command ownership and evidence carry these distinctions; no
extra AI caller parameters, duplicate launcher or fourfold full-suite cycle.

The November 10 NICT participant demo still requires its actual authenticated
phone/Web configuration. Dev or fixture PASS must not be represented as that
acceptance. Closed Phases, Sessions, histories and applicable prior results stay
unchanged; this planning update does not resume development.

## Nightly validation decision

The subsequent user direction adds both dev and production to nightly automatic
testing. Daytime development remains dev-first. The declared nightly suite runs
against the same selected revision in two isolated configurations, with separate
results and durations. Production mode uses test-owned resources and the normal
authentication/authorization path, never customer data or development auth bypass.
The pair passes only when both required runs pass; failures/not-run stay visible.
Existing bounded execution, SBT serialization and explicit HEAVY/live-provider
selection remain. VM-05 owns this addition; no schedule has been activated by
this documentation change.

## Mode-dependent behavior and test-specific coverage

The user clarified that CNCF modes select parts of system behavior. Test can
enable selected real external access while using substitutes elsewhere, redirect
notifications or expose controlled fault/time facilities. Common configuration
selects the declared policy/adapter and effect scope; common authorization stays
active. This is broader than an environment composed only of deterministic stubs.

VM-06 and the validation specification now select focused test-specific cases
through existing feature metadata and explicit references. Cover intended
selection/recipient, allowed and denied effects, excluded modes and missing
bindings. Run affected checks during development and the declared lightweight
regression set at night beside dev/production; no third whole-suite pass.
Costly/live cases retain explicit scheduling and actual result attribution.
Required test-specific failure/not-run prevents overall nightly success even
when the baseline pair passes. No implementation, schedule activation or Phase
completion is claimed by this refinement.

## Per-test requirements, build binding and orchestration

The user adopted the declaration -> build -> workflow split. Each test declares
required modes and mode-specific expectations; CNCF normalizes that metadata.
build.sbt wires a shared mechanism to apply selected modes to actual tests and
forked target processes. sm-workflow selects tests/variants, invokes the normal
execution route and collects separate test/parameter/mode results.

The [specification](../../../spec/development-mode-validation.md#per-test-declarations-build-binding-and-workflow-ownership)
and [VM-07](../../../phase/development-mode-validation-plan.md) connect the
[CNCF producer follow-up](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/test-execution-mode-plan.md).
Current V1 does not already expose mode declarations; agree the versioned
extension and connect one real harmless dispatch before general adoption.

Mode-unspecified tests use dev daily and dev/production nightly. Explicit
requirements remain required when selected, including focused test-specific
and demo cases. No mode is silently dropped, no different-mode result collapses
to a single PASS, and no separate build/consumer table duplicates declarations.
Existing ADMISSION/Slow/DoNotDiscover migration, effect authority, Sessions and
prior evidence remain; this record changes planning, not runtime or schedules.
