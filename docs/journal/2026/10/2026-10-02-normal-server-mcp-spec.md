# Normal server MCP executable spec

Goal State: IMPLEMENT — sm-workflow Phase 2. Previous turn made progress with
209 passing examples. Parent limited recovery remains user-authorized; no agents,
management hashes, alternate dispatch engine or invented human authority.

## Frozen slice

Own CanonicalGoalPhaseLauncherSpec.scala and this journal only. The whole
existing spec and canonical launcher were inspected. Extend the actual normal
standalone host with explicit loopback endpoint and owned temporary port registry.
Start the official prepared CAR using bin/launcher and CncfMain server in one
owned child; use bounded actual HTTP readiness and MCP calls, then stop/reap that
exact child in finally. Never attach to or kill another process; a port collision
fails rather than choosing a different listener. No child SBT or outbound network.

Verify initialize, nine actual GoalPhase tools (excluding generated placeholders)
alongside the canonical standard Tool component,
canonical string command schema, and missing-human-selection refusal against
the ordinary CLI while the server is live. This does not prove positive Start,
durable completed workflows or all Phase 2 transport parity. Retain SQLite bytes
and child output on failure. No producer, runtime script, public schema, consent
writer, Git or publication mutation. Validate through fresh serial SBT metadata
and official cozyPrepareRuntime; preserve the entire Phase scope.

## Validation 678 and bounded expectation repair

The actual normal server started and MCP initialize/list returned successfully.
The new scenario failed before Start because the complete standard Subsystem
also publishes CNCF Tool's six operations. Inspected ToolComponent.Factory:
resource.read, web.fetch/head/search, time.now and decimal.calculate are its
explicit standard MCP services/operations. This is not a sm-workflow placeholder
or unexpected new application binding. The owned server was reaped by finally.
209/210 examples passed; terminal exits 1/1 and lock release verified from the
complete summary:
`/tmp/skill.cncf.d/a-c35142f0-c80b-40a9-90d3-49953d247b4c/47227-20261001T164941Z.summary.json`.

M1 correction stays within the owned spec: compare the entire catalog against
exactly those six standard tools plus the nine managed tools (size 15), then
check each exact managed tool's canonical command schema. Do not filter away
unexpected names, disable standard services or change production publication.
Revalidate with fresh permission. Positive Start/complete Phase remain unproven.

## Validation 679 — actual transport mismatch exposed

Official preparation and 209/210 examples passed; the new network scenario now
passes readiness, initialize, the exact full 15-tool catalog and managed arg1
string schema. It fails at the required Start refusal equivalence: MCP returns
`GoalPhase Start command JSON is missing`, whereas the ordinary CLI reaches the
missing-human-selection refusal. The failing expectation is retained. Both
owned processes terminated; the server finally block reaped the exact child.
Complete summary read, exits 1/1 and shared lock released:
`/tmp/skill.cncf.d/a-2a2aac65-8bc0-477c-b3ce-9d43add2b4fe/48300-20261001T165112Z.summary.json`.

Source cause: standard McpJsonRpcAdapter._to_request constructs `arguments = Nil`
and properties from tool arguments (plus textus.format=json). The authored
GoalPhaseStartServiceOperation consumes only List(Argument(arg1,...)). Existing
WorkflowJsonCommandRequest declares the correct canonical schema but provides
no shared transport extraction. Other authored service bindings also directly
consume request.arguments. This is an application integration defect, not
missing human authority, platform refusal or a reason to alter CNCF MCP dispatch.

Next admitted repair frontier: inspect all current bound GoalPhase input
consumers, add one closed shared extractor accepting the declared command from
either canonical CLI argument or canonical MCP property, reject duplicate,
ambiguous and undeclared command inputs, and route those bindings through it.
Keep the versioned JSON decoders, human authority checks, real server regression
and canonical framework unchanged. Freeze exact files after whole-program
inspection before edits. Do not claim working network invocation or Phase
acceptance from successful catalog publication alone.
