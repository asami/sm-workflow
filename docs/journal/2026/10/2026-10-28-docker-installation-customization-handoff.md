# Docker Clean Installation and Customization — Phase 19 Handoff

Date: 2026-10-28
Status: plan adopted; no Docker run, installation or qualification claimed.
Audience: sm-workflow development.

The user requests broad installation/customization coverage in clean Docker
environments, classified as HEAVY. Create [Phase 19](../../../phase/phase-19.md)
as an independent qualification track. Phase 18 already owns tool-neutral
validation/Flutter; do not mix these obligations into it or active 6.x work.

Follow the [specification and CI-01–18 matrix](../../../spec/clean-installation-qualification.md)
and [P19 checklist](../../../phase/phase-19-checklist.md). Establish one main route:
empty non-root HOME/caches -> normal runtime and skill-bundle installation ->
ordinary public initialization -> clean RepositorySync against a disposable local
bare remote -> process restart -> retained result retrieval. No source checkout,
developer classpath, HOME, live stores or prebuilt settings may leak into the
container. Ordinary artifact preparation remains with its existing owner.

Then add alternate paths/HOME/runtime roots, spaces/Unicode, configuration
precedence, custom data/evidence destinations, Git executable selection, default
and increased SBT heap, runtime budget/cancellation, profile selection, reinstall,
customization conflicts, supported update pairs, interrupted preparation,
independent installations, removal/recovery and owned cleanup. Use the actual
configuration contracts and public interfaces; no test-only config injection to
conceal a missing initialization route. Keep SQLite as the local store; do not
introduce database servers for this coverage.

Existing evidence shows the portable installer already has dry-run, selected
replacement, discovery-conflict handling and preservation rules. Its writes are
not promised to be atomic. Installed-runtime stability has its own active/old/new
generation contract. Reuse and test these mechanisms rather than implementing a
second installer or generic rollback layer. Source tests are not Docker evidence.
The no-project-config bootstrap remains owned by Phase 15's INIT slice; the
three-profile variants depend on Phase 6.10. Missing producer work stays visible
without blocking unrelated independent matrix development.

Select HEAVY through canonical executable-spec metadata and a dedicated feature;
keep FULL/default/ADMISSION free of Docker. Missing Docker/tools or all-skipped
cases are unqualified, not PASS. A single Linux platform is the first target;
expand only to declared supported combinations and record native versus emulated
architecture. Docker cannot establish macOS launcher behavior, actual Codex role
loading, live model availability or visible chat UI. Retain a separate finite
host check for those claims, without paid AI calls in the Docker suite.

Keep the main route first (I1 inventory -> I2 clean install/use -> I3 customization
-> I4 lifecycle/failure -> I5 integrated qualification). Reuse existing execution,
configuration, Journal/Evidence and cleanup ownership. Retain logs/results outside
Git before removing recorded test-owned containers/volumes; never prune unrelated
Docker resources. Failure injection is isolated test behavior, not a new set of
production preflight checks, hash approvals or capacity reservations.

Preserve current source edits, Sessions, accepted work, histories, repair counts
and verification results. This plan adds no active acceptance gate, permission to
change the real HOME, automatic migration, publication, push or new model usage.
Do not claim product support for every platform/offline/downgrade combination;
record support decisions and producer gaps explicitly. Completion requires actual
supported-matrix receipts, focused checks and integrated review, with limitations
stated honestly.
