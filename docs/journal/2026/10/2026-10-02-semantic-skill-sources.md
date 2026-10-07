# Component-owned semantic skill sources

Goal State: IMPLEMENT — sm-workflow Phase 2. Previous turn made progress with
canonical server entry and 212 passing tests. User-authorized parent limited
recovery; no agents, management hashes or acceptance/release claim.

## Frozen authoring slice

Own new skills/sm-goal-phase/SKILL.md, skills/sm-split-phase/SKILL.md,
skills/sm-repository-sync/SKILL.md and this journal only. skill-creator was read
completely, along with the normative skill adapter design and distribution
section. Keep short self-contained standard directories; no global install,
legacy skill replacement, implicit MCP/config changes or fabricated manifest.

Skills use exact runtime-issued semantic requests and result contracts. They
never choose operations from results, start/chain another profile, implement
advance/retry, mint consent/claims/authority, run Git/SBT or access SQLite.
They return semantic payload through the host-configured CLI or MCP client at
the completion route issued by Workflow; missing runtime/contract remains a
limitation, not permission to run the old workflow. Neutral bundle/manifest,
CAR inclusion, real worker round trips and installation remain separate open
acceptance. The files are authoring sources, not a working installed bundle.

Use quick_validate on each source with managed Python; inspect routing and
scope manually. No SBT needed for Markdown-only sources. No schemas, public
Operation changes, source mutation or workflow runtime claim in this slice.

## Validation and limits

All three sources passed official skill-creator quick_validate (exit 0):
sm-goal-phase, sm-split-phase and sm-repository-sync. Initial sandbox attempt
could not access normal uv cache; scoped offline execution established that
PyYAML was not cached. Successful validation used uv --no-cache with an isolated
transient PyYAML dependency and existing managed Python, no Python downloads,
global pip, repository dependency changes or user skill installation. Package
registry read/download was explicitly bounded in platform-reviewed execution;
no application/server/model was contacted. All owned validator sessions are
terminal. git diff --check passed.

Manual scope inspection: no referenced legacy skill, private path/SQL/storage
instruction, state selector, operation selection from result, shell command or
automatic advance/retry loop. Start intent requires a host/client entry binding
and independent human selection; the source does not invent that missing host.
Only the issued semantic task may be performed, and returned data is submitted
at its issued completion route. Skill-creator guided concise self-contained
directories rather than adding unused scaffolding or a second dispatcher.

Frontmatter validity and manual inspection do not prove behavioral conformance.
P2-C02 remains unchecked pending real issued-order worker/client round trips,
neutral versioned bundle bindings, framework-independent validation/installation
and standalone/CAR inclusion equivalence. No manifest or distribution claim is
fabricated. Remaining Phase scope and independent review/release remain intact.
