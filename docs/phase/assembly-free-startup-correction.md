# Assembly-free installed startup correction

Stage Status:
- Current status: OPEN
- Owner: sm-workflow development; CNCF owns the runtime prerequisite
- Specification: [assembly-free runtime startup](../spec/assembly-free-runtime-startup.md)
- Handoff: [2026-10-27](../journal/2026/10/2026-10-27-assembly-free-startup-handoff.md)
- Allocation: bounded setup/runtime correction, independent of active Phase 6.7 and planned 6.8/6.9 acceptance

## Main route first

- [ ] AF-S1: Reconcile the deployed common helper, repository source, active
  sm-workflow-dev copy and install/bundle ownership. Remove mandatory fixed
  config/assembly file checks in the owned delivery sources; preserve task/output
  checks and concurrent work. Align templates and current installation guidance.
- [ ] AF-S2: Consume CNCF AF-C1/C2 and integrate the existing installed composition/
  setup with normal fixed-user resolution. Diagnose actual missing settings
  without a dummy assembly or privilege fallback.
- [ ] AF-S3: Run affected focused helper/runtime tests and qualify the actual
  installed command in an isolated directory with no assembly and no descriptor
  override. Verify effective user, roles/capabilities, fallback policy and stable
  subsystem/storage identity; cover missing/conflicting identity and optional
  explicit overrides. Do not use a test-only launcher to establish success.
- [ ] AF-S4: Deliver one outcome identifying source/artifact, actual checks,
  consumer adoption procedure and limitations. Preserve the consumer's current
  temporary setup until qualified; only its owner adopts through a supported
  same-Session route. Demonstrate retained accepted work/history/counts/results.
- [ ] AF-S5: Reconcile repository/bundle/deployed helper copies and documentation
  against actual delivered behavior so subsequent installation does not restore
  the fixed-file requirement. Record closure evidence without claiming a whole
  development Phase complete.

AF-S1 may proceed alongside CNCF work. AF-S2/3 establish the connected main route
before AF-S4 live adoption. Existing working CBD development continues throughout;
there is no mutual wait for the full CNCF or sm-workflow Phase to finish.

Preserve current Sessions, ongoing source edits, staged work, failed history,
repair counts and successful tests. No active Phase plan, acceptance or existing
Session is rewritten by this document. This is planning, not implementation,
publication, test execution or permission to operate the live consumer Session.

