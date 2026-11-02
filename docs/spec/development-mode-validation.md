# Development-mode defaults for managed validation

Decision date: 2026-11-02
Status: adopted policy; runtime integration/qualification pending
Related: [normal typed SBT validation](normal-typed-sbt-validation.md)

## Default and explicit scenario selection

Routine sm-workflow development validation targets the application's **dev**
profile (canonical CNCF operation mode `develop`). Running tests does not imply
switching the target application to `test`. The workflow host's own operation
mode, the child application's profile, SBT `Test` configuration and validation
classification/run scope are separate dimensions. Follow the
[standard-annotation contract](label-driven-test-selection.md): ADMISSION is the
custom classification, Slow/DoNotDiscover retain their framework meanings, and
full/focused describe run scope. Mode declarations do not revive retired labels.

Use [shared runtime-mode defaults](https://github.com/asami/goldenport-cncf/blob/main/docs/spec/runtime-mode-security-defaults.md)
and the project's ordinary supported validation binding. Do not invent per-suite
environment parsing, a second launcher or a public Operation parameter through
which an AI result can choose arbitrary execution configuration.

- Dev is the default for routine focused/admission/full checks where no selected
  scenario declares another profile. It keeps authentication/provider setup light
  with isolated fixtures while exercising the common authorization implementation.
- Test is selected for controlled fixture scenarios and declared test-specific
  behavior, including selectively enabled real external integrations.
  Scenario-declared production/demo/test settings
  take precedence over the ordinary dev default; never overwrite them globally.
- Changes to mode resolution, identity fixtures, provider authentication,
  authorization, credential/storage scoping or security-related deployment
  settings select the affected demo/production boundary scenarios. Unrelated
  changes do not trigger four full test runs.
- Demo/deployment acceptance runs the actual intended configuration when required
  by that acceptance contract. A dev PASS cannot replace real provider, participant
  demo or production qualification. Unavailable credentials/services are reported
  as unavailable/not run through the existing result/decision route, not silently
  replaced by dev success. Continue independent eligible local validation.

## Per-test declarations, build binding and workflow ownership

Consume CNCF's [per-test mode declaration contract](https://github.com/asami/goldenport-cncf/blob/main/docs/spec/test-execution-mode-declarations.md)
through normalized metadata discovery. Test Operations own their required modes
and mode-specific assertions. A class default can be replaced by a per-test
declaration; no declaration means the run policy supplies modes (dev normally,
dev/production at night). CNCF owns normalization, inheritance and ABI admission.
sm-workflow does not reinterpret annotations or maintain another mode table.

Select the actual tests through existing metadata/framework filters and Slice
references, then expand required mode variants before preparing resources.
Preserve explicit declarations even when the run's default differs. A conflicting
requested mode or unsupported required metadata is reported, not silently dropped
or interpreted as dev. The Slice owns acceptance and selection, not a duplicate
of each test's declaration. Ordinary user/AI completion inputs gain no executable,
mode-override or arbitrary environment parameters.

build.sbt wires the shared SBT integration to the project's supported target
configuration. sm-workflow sends the admitted test/mode selection through the
existing typed execution path, starts/awaits it and collects results. The build
applies the actual mode to selected cases and forked application processes; it
does not rediscover coverage policy or copy annotations into a per-test list.
Different modes must not share mutable global configuration or leak between runs.

Identify each variant by logical test reference, normalized test parameters and
effective mode in its existing repository/artifact/execution context. Deduplicate
only identical variants; preserve separate outcomes, actual modes, durations and
fixture/live attribution. Expected results are asserted by tests, not inferred
by sm-workflow. A test asserting disabled behavior in production may pass there
and assert enabled behavior in test. Every required variant must execute and
pass its assertions before the selected acceptance passes.
An explicitly scoped exploratory subset may return partial evidence for its
actual variants; retain outstanding requirements rather than rejecting useful
partial work or presenting it as complete acceptance.

This requires the coordinated CNCF metadata/binder and SBT integration extension;
current V1 metadata is not assumed to expose it. VM-07 connects the producer
follow-up to one harmless real dispatch before broader adoption. Unsupported
mixed-mode case selection is a reported capability gap, not permission to run
an unrestricted suite and label all cases with the requested mode.

## Focused coverage of mode-specific behavior

CNCF modes may change specific runtime behavior, not just default data or
endpoints. Mode selects a declared policy/adapter; configuration specifies the
capabilities, permitted recipients/resources and limits. Test can combine real
test-service access with substitutes or select notification redirection/fault
controls. Common authorization still applies to all selected effects.

Use existing feature metadata, explicit Test Operation references and Slice
verification plans to select focused cases for each supported test-specific
path. Identify expected enabled modes and behavior; do not build a second suite
registry or let an AI result introduce arbitrary outbound access. Cover the
intended adapter/recipient, authorized operation, denial, exclusion from modes
where the feature is disabled and missing/invalid selected configuration.

Changes affecting such a path select its focused checks during development.
The nightly plan also declares a lightweight test-specific regression set
alongside the dev/production pair so unchanged test-only paths remain covered.
Do not run the whole suite a third time in test merely to obtain this coverage.
Costly or live-provider cases retain explicit selection, prerequisites and
scheduling; a stub test cannot stand in for a required real integration result.

Keep effective mode, selected feature/scenario, actual fixture/live adapter and
outcome in existing evidence. A dev/production PASS cannot qualify code enabled
only in test. Missing prerequisites stay unavailable/not-run; required test-path
failures or omissions prevent overall nightly success even if both baseline
modes passed. Intentionally unscheduled optional cases retain their limited
coverage disposition. No silent fallback, unchanged-input proof or new launcher.

## Nightly dev and production validation

Nightly automatic validation selects the project's declared nightly suite and
expands its per-test modes against the same selected source/artifact revision.
Tests without explicit mode declarations run in both dev and production;
explicitly declared modes remain required and are not overwritten by that default.
Daily interactive work retains the dev-first default. Test and demo remain
explicit scenario selections, including the focused test-specific regression
set above, not two additional blanket nightly runs.

Production mode here means production configuration and enforcement in an
isolated test deployment, not execution against customer systems, accounts or
data. Use test-owned accounts, storage and credentials; exercise the ordinary
authentication/authorization route without enabling development login bypasses.
Provider substitutes are attributed as such and do not qualify real-provider
or actual demo/deployment acceptance.

Select the nightly coverage once, then bind its required variants, with assertions
for intentional mode differences owned by each test. Existing project plans,
metadata and standard tags govern coverage; required full regression remains
required, while Slow/explicit-only/live-provider work runs only when selected.
No automatic multiplication of costly work or external effects follows.

Retain a separate result, effective mode and duration for each run. Report the
nightly pair as successful only when both required runs succeed. Failure,
timeout or unavailable setup in one mode is not hidden by the other mode's PASS;
continue the other independent run when the environment remains usable. Keep
timeouts/retry limits finite and use existing managed execution/result collection.
Respect existing SBT serialization/locking; the two modes do not authorize
concurrent independent top-level SBT processes. No separate scheduler or
result-content comparison engine is required by this contract.

## Runtime integration and evidence

Resolve the effective target profile through the existing project validation
plan and shared typed execution/configuration boundary. Confirm how the selected
build passes it to forked application/test processes; setting only the workflow
host or an unrelated JVM property is not proof that the target consumed it.
Existing explicit test profiles and fixture construction remain authoritative
for those scenarios. No universal SBT argument is invented by this document.

Record the effective target mode, relevant profile/scenario and actual provider
kind (fixture or live) in the existing execution result/evidence. Reuse results
only for the requirements they exercised. No copied configuration ledger, source
hash, unchanged-input certificate or fresh full test solely for evidence-format
changes is required. Keep actual historical results and their original scope.

Mode never grants permission to execute a command, access production data,
publish, deploy or contact an external provider. Preserve runtime-owned command
execution, existing authority/effect limits and normal cache/SBT locking rules.
Required test purposes, full-suite obligations and final deployment checks remain;
this policy changes defaults and selection, not acceptance strength.

## Acceptance

- A project with no scenario-specific override runs its ordinary validation in
  dev through the existing execution route, with the effective child mode visible.
- Explicit test/demo/production scenarios retain their selected profile; target
  mode is not inferred from SBT `Test`, workflow host mode or Flutter build mode.
- Per-test/class declarations and absent run-policy defaults normalize through
  CNCF; shared SBT/build.sbt passes the actual mode to exact cases and child
  processes. Distinct test/parameter/mode variants retain distinct outcomes;
  no required mode is silently omitted or inferred from another mode's PASS.
- A mode/authentication/authorization change selects the relevant positive and
  denial scenarios; an unrelated edit does not repeat all suites in four modes.
- A test-specific path has focused selection for its enabled, denied, excluded-mode
  and missing-binding cases, using existing metadata/references. Real/substitute
  effects are distinguished; dev/production success cannot substitute for this
  coverage, and required focused failures prevent overall nightly success.
- A live demo requirement cannot be closed with fixture/dev evidence; missing
  setup stays explicit and independent local checks remain useful.
- A nightly validation preserves explicit per-test mode requirements and uses
  dev/production defaults only for mode-unspecified tests, on isolated resources.
  Every required variant remains visible; any required failure/not-run prevents
  aggregate success. Ordinary daytime validation remains dev-first.
- Existing tests, counters, Sessions, accepted evidence and closed Phases are
  preserved. No extra caller parameter, launcher or comparison gate is added.

Delivery: [development-mode validation workstream](../phase/development-mode-validation-plan.md).
