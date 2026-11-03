# Dev-first managed validation workstream

Status: PLANNED; execution/qualification pending
Decision date: 2026-11-02
Contract: [development-mode validation](../spec/development-mode-validation.md)

Keep completed Phase 5/6 and successor histories unchanged. Connect this policy
to Phase 6.11 B2 ordinary validation resolution and Phase 7 multi-repository
consumer validation. It is not an automatic restart or a retrospective closure
condition on already completed work.

- [ ] VM-01: inventory existing project/typed-SBT bindings and actual child
  profile propagation. Select dev by default without replacing explicit
  per-scenario modes or adding caller control parameters.
- [ ] VM-02: connect purpose/feature-based selection and Slice verification
  plans to affected mode-boundary scenarios. Routine unrelated work does not
  acquire a four-mode full-suite matrix.
- [ ] VM-03: retain actual effective mode and fixture/live provider attribution
  in existing result/evidence; prevent dev/fixture success from satisfying a
  required live demo/production scenario. Preserve useful existing results.
- [ ] VM-04: qualify one ordinary dev validation and explicit test/demo/
  production override cases through the real managed route, including missing
  live prerequisites. Hand shared behavior to Phase 7 consumers without a second
  launcher, new comparison ledger or repeated per-helper review.
- [ ] VM-05: connect the existing nightly execution entry to the declared suite
  with dev/production defaults and preserved explicit per-test mode requirements,
  using the same selected revision and isolated
  test resources. Keep mode-specific results/durations and finite execution
  limits; verify one failed/unavailable run cannot become aggregate PASS and
  does not unnecessarily suppress the independent run. Preserve SBT serialization
  and explicit Slow/explicit-only/live-provider selection. This qualifies production-mode
  behavior, not access to a real production deployment.
- [ ] VM-06: consume CNCF MODE-07 and identify supported test-specific behavior
  through existing feature metadata/scenario references. Select focused checks
  for intended adapter/recipient, authorized/denied effects, disabled modes and
  missing bindings. Include the declared lightweight set in nightly regression;
  retain explicit costly/live scheduling and fixture/live attribution. Verify
  dev/production PASS cannot conceal required test-path failure or not-run.
  Do not add a third full-suite run, duplicate registry or arbitrary effect input.
- [ ] VM-07: connect [CNCF TM-01..04](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/test-execution-mode-plan.md)
  to per-test mode discovery -> variant selection -> shared SBT/build.sbt binding
  -> actual execution -> per-test/mode results. Qualify inheritance/override,
  absent daily/nightly defaults, explicit requirements, mixed-mode suites,
  forked target propagation, no mode leakage and unsupported/conflicting input.
  Preserve ADMISSION/Slow/DoNotDiscover selection and existing effect authority.
  No second mode table, caller command or silent loss of required variants.

Deliver the normal dev path first, then its bounded explicit-profile branches
and the nightly dev/production pair with focused test-specific regression.
Reuse the ordinary execution mechanism;
do not build a second scheduler for this workstream. Establish VM-07's small
producer/build/consumer connection before adopting metadata-driven mode expansion
in VM-02/05/06; do not claim the existing V1 already supports the new declaration.
Do not wait for every Textus deployment/provider to be implemented; actual live
acceptance remains with each owning project's declaration.
