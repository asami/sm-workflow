# Phase 6.12: Shared Scala Lint and Goldenport Conventions

Stage Status:
- Current status: OPEN
- Owner: sm-workflow development
- Update rule: Update from actual acceptance evidence; planning is not completion.
- Closure basis: [Phase 6.12 checklist](phase-6.12-checklist.md), SLP-A01–SLP-A15

Planned: 2026-11-01
Sequence: Phase 6.11 -> Phase 6.12 -> Phase 7.
Specification: [shared Scala lint profiles](../spec/scala-lint-profiles.md).
Handoff: [English journal](../journal/2026/11/2026-11-01-shared-scala-lint-profiles-handoff.md).

## Purpose and preservation

Centralize baseline/team/project lint settings and make `sm-workflow.source.style=goldenport`
select naming conventions and inherited Asami-style history handling, with
`sm-workflow.source.history.style` as an optional history-only override.
Project `.scalafix.conf` contains only local differences and remains optional.
Reuse accepted Phase 6.5 Q3 without reopening its acceptance. Preserve ongoing
work, staged edits, Sessions, history, repair counters and successful results.
This planned Phase adds no gate to active work and does not adopt configuration
into an executing request. The prior unnumbered plan is relocated here with all
SLP identifiers and open checklist states preserved.

## Subphase 6.12-A: Shared convention execution

Stage Status:
- Current status: OPEN
- Owner: sm-workflow development
- Update rule: Advance only on observed SLP acceptance outcomes.
- Closure basis: [SLP-A01–SLP-A15](phase-6.12-checklist.md); all required items must pass.

Steps SLP-1–SLP-4 below belong to this Subphase. Their closure evidence is mapped
in the checklist; step narrative does not replace that ledger.

## Main route first

1. **SLP-1 — Resolve and execute shared defaults.** Inventory the current lint Action,
   installed resource/configuration owners and source selection. Implement baseline
   plus the selected `goldenport` team profile and retain one effective configuration through
   the existing runtime route. Prove a tiny project without `.scalafix.conf` runs a
   shared private-member rule and reports `_wrapCommand` as a violation. Resolve
   source style and history inheritance from normal CNCF-resolved parameters.
2. **SLP-2 — Complete the initial convention rules.** Add local-helper/flatcase and
   version-comment placement checks. Package reusable rules once; use positive and
   negative fixtures for production and test code, including documented exceptions.
   Share the package with CNCF rather than duplicating its implementation.
3. **SLP-3 — Add project differences and exceptions.** Resolve optional project
   settings, rule additions/removals, leaf precedence and standard suppressions.
   Keep selected profile provenance and effective configuration in existing evidence.
4. **SLP-4 — Connect acceptance and delivery.** Feed findings into ordinary FIX and
   selected re-validation, qualify Compile and Test handwritten-source coverage,
   and adopt profiles for subsequent validations. Retain prior findings/results;
   no repository-wide naming cleanup or successful-test replay is a prerequisite.

Use `sm-workflow.source.style` and `sm-workflow.source.history.style`; the latter
inherits only when absent. Keep external history rule data at
`sm-workflow.source.history.rule`. SLP-1 establishes source/history separation;
SLP-3 covers explicit overrides, old-key rejection and removal from all owned
configuration producers/consumers. SLP-4 qualifies new configuration/runtime adoption
without changing issued work or historical evidence. No old-key aliases, dual-read
fallback or automatic conversion is delivered. See the specification's six-row matrix.

Align shared artifact ownership and project-override settings with existing build
mechanisms. Runtime owns validation and any necessary local publication. This
planning change does not execute SBT, publish, edit live configuration, change a
Session or dispatch work to another chat.


## Completion

Close only with actual results for all required checklist items and recorded dispositions
for any explicitly relocated work. Publish an English completion record describing
configuration selection, rule coverage, preserved history behavior, validation and
consumer adoption. Plan edits alone do not satisfy delivery or close Phase 6.12.
