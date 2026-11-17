# Phase 21: Recursive Repository Catch-up checklist

Status: planned; implementation and acceptance unverified.
Restored: 2026-11-16
Plan: [Phase 21](phase-21.md)
Origin: former Phase 13 P13-01–06, preserved as P21-01–06 in the same order.

- [ ] P21-01 (former P13-01): Traverse only registered `localRepository` dependency edges, using the Phase 7 model, stable dependency-first order and visited-set/cycle handling; `localJar` and `localProject` remain independent and do not imply Git traversal.
- [ ] P21-02 (former P13-02): Support explicit `remote`, `local-main` and `all` targets (remote then local main), with source/branch snapshots before mutation and no implicit push, force reset or destructive cleanup.
- [ ] P21-03 (former P13-03): Qualify fast-forward and explicit rebase, retaining dirty work, shared-history safety and truthful conflict outcomes without silent shared-history rewriting.
- [ ] P21-04 (former P13-04): Qualify history-preserving merge, actual Human conflict/authority decisions and optional pre-commit approval through the common Continuation route, including continue/cancel and validation before commit.
- [ ] P21-05 (former P13-05): Provide dry-run/plan and per-repository completed/blocked/partial outcomes using existing typed Git, permissions, workspace coordination and Evidence owners.
- [ ] P21-06 (former P13-06): Record actual recursive producer/consumer, offline local-main, remote, diverged-history, merge yield/resume, rebase, cycle and dirty-refusal verification. Confirm provider artifacts are not implicitly built, republished or reselected.

All items are pending. Restoring documentation supplies no implementation, test,
review or installed-use evidence and changes no active Session or acceptance.
