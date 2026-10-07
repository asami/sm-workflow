# Canonical user-facing server entry

Goal State: IMPLEMENT — sm-workflow Phase 2. Previous turn proved real network
decoder parity for nine current tools, not positive Workflow completion.
Parent limited recovery remains authorized; no agents or management hashes.

## Frozen implementation

Own scripts/run-server.sh, README.md, CanonicalGoalPhaseLauncherSpec.scala and
this journal. Whole selected files and source scripts/cncf-common.sh inspected.
Current run-server.sh separately resolves a potentially different framework
version, merges it ahead of official runtime classpath, and uses legacy class
discovery; it cannot accept explicit host assembly arguments. Replace that
entry with exec of the repository's canonical bin/launcher, using only the
official target/cncf.d classpath/descriptor/manifest and --component-dev-dir.
Require all three nonempty preparation artifacts, forward caller's explicit
runtime host configuration arguments before server, inherit cwd for host file
configuration. No SBT/coursier resolution, default consent, assembly/user mode,
port override, private runtime home or shadow dependency selection in this entry.

Change existing real server spec to invoke this actual script with its owned
assembly. CLI still uses canonical launcher directly; all current network
coverage remains required. Keep loopback endpoint and owned bounded cleanup.
Update README with invocation, preparation requirement and evidence limits.
Legacy debug/preparation scripts are not invoked or silently repaired here.
Validate bash syntax plus fresh serial SBT official preparation and entire
GoalPhase/helper accumulator; no source edits while validation runs, Git,
publication, new authority records or other-process management.

## Validation 682

bash -n and git diff --check passed. Official cozyPrepareRuntime and 212/212
examples in 32 suites passed, including actual scripts/run-server.sh startup,
MCP initialize/full catalog, Start admission and all-nine malformed decoder
parity with ordinary CLI while the server is live. The exact owned child was
reaped. Complete summary read; SBT/wrapper exits 0/0 and shared lock released:
`/tmp/skill.cncf.d/a-62362a74-d240-40c6-9573-15fc7e563749/54733-20261001T170352Z.summary.json`.
No source edits occurred during validation; final README paragraph placement
is documentation-only. No accepted Step or Phase release is claimed.

This replaces the user-facing server startup dependency/discovery path only.
Missing preparation artifacts fail closed without launching Java or SBT.
Normal-user successful Start still needs independently recorded human selection;
durable successful Workflow recovery, remaining profiles/reference/management,
skills/cost and independent review/exact release remain open. Legacy debug and
preparation helpers were inspected but not executed; their direct SBT paths
remain outside this frozen slice and must not be used by this workflow.
