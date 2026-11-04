# Phase 6.12 acceptance checklist

Stage Status:
- Current status: OPEN
- Owner: sm-workflow development
- Update rule: Mark items complete only with actual evidence and its reference.
- Closure basis: All SLP-A01–SLP-A15 below; no implementation is claimed.

Plan: [Phase 6.12](phase-6.12.md), Subphase 6.12-A.
Existing SLP-A01–10 are relocated unchanged from the unnumbered plan, not completed.
Step mapping: SLP-1 -> A01/A07/A11/A12; SLP-2 -> A03/A04/A05;
SLP-3 -> A02/A06/A08/A13/A14; SLP-4 -> A09/A10/A15 and integrated acceptance of all items.


- [ ] SLP-A01: A project with no `.scalafix.conf` executes nonempty shared defaults
      through the normal runtime lint Action without AI rule selection.
- [ ] SLP-A02: Baseline/team/project precedence, rule union/deduplication, explicit
      removal and leaf/array overrides have deterministic focused coverage.
- [ ] SLP-A03: `_wrapCommand`, unprefixed private `exact`/`reference`, wrong local
      helper names and misplaced version metadata are detected with rule/location.
- [ ] SLP-A04: Valid declarations, license comments and supported documented exceptions
      pass; fixtures cover nested/companion/multiple-definition Scala 3 source.
- [ ] SLP-A05: Handwritten Test sources are checked as well as Compile sources;
      generated code is excluded and reported with truthful applicability.
- [ ] SLP-A06: A scoped intentional suppression affects only its selected diagnostic;
      additions/removals and project-relative configuration remain reproducible.
- [ ] SLP-A07: Missing optional config succeeds; malformed selected config and missing
      rule artifacts remain explicit failures, never CLEAN or silently OFF.
- [ ] SLP-A08: Effective profile versions/configuration are retained with the actual
      validation. Later config changes do not alter issued work or historical results.
- [ ] SLP-A09: A detected violation follows ordinary FIX, passes affected re-validation
      and returns to admission in the same Session, preserving history and counters.
- [ ] SLP-A10: New/affected findings are distinguished from unrelated existing debt;
      accepted Phase 6.5 results and ongoing work remain intact. Delivery includes
      focused results, integrated configuration review and installation/adoption notes.

Use small fixtures and existing validation infrastructure. No product self-publication
test loop, blanket FULL reruns, new AI gate or extra monitoring is required.

- [ ] SLP-A11: `sm-workflow.source.style=goldenport` with absent history style
      enables Goldenport naming and inherited goldenport history, using
      `sm-workflow.source.history.rule`, without a project config file or second
      profile selection. Existing history rollover/normalization remains qualified.
- [ ] SLP-A12: All six source/history combinations in the specification pass;
      history=none leaves Goldenport naming enabled when selected, while history-only
      goldenport does not activate Goldenport naming. Effective none accesses no
      history rule and runs no history check/update/warning or version-placement lint.
      Generic baseline lint remains applicable.
- [ ] SLP-A13: Per-key CNCF precedence is applied before absent-only inheritance;
      explicit history override survives a higher-layer source default. Malformed
      selected styles/rules fail truthfully and do not silently select none.
- [ ] SLP-A14: Old source-history.style/rule keys have no execution alias, dual
      read or automatic conversion; old-only and mixed configurations report obsolete
      keys. Owned schemas, readers, templates, examples, fixtures and adoption settings
      use new keys. Historical evidence remains readable without becoming live config.
- [ ] SLP-A15: A small connected fixture qualifies new runtime/settings adoption and
      retained history behavior; already issued work, Sessions, counters and successful
      results remain unchanged. Delivery documents the breaking key rename and actual
      adoption limits without replaying successful checks merely to rename settings.
