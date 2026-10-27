# Clean Installation and Customization Qualification

Date: 2026-10-28
Status: accepted test design; implementation and Docker qualification pending.
Plan: [Phase 19](../phase/phase-19.md)

## Purpose and ownership

Qualify the ordinary sm-workflow installation, initialization, configuration and
update experience in clean Docker environments. Detect dependencies on the
developer's machine and preserve intentional customizations and retained work.
This is an integration test suite, not a replacement installer/configuration
engine or a general container platform.

Use [native installation](native-client-installation.md),
[project initialization](project-initialization.md),
[assembly-free startup](assembly-free-runtime-startup.md),
[installed runtime stability](installed-runtime-stability.md), and
[operating profiles](ai-operating-profiles.md) as the contracts under test.
Resolve discrepancies against the actual installed public interface during I1;
do not encode stale documentation, implementation accidents or test-fixture setup
as mandatory user steps. Report missing producer functionality separately.

## Clean environment and artifact boundary

- Start with a declared Linux base image and non-root user, empty HOME and empty
  product/config/dependency caches. The image may contain only documented tool
  prerequisites, never a preinstalled sm-workflow or populated product settings.
- Supply an exact normal distributable runtime and its normal skills/agents
  bundle. Use the public installation route. Do not mount developer checkouts,
  host HOME, class directories, runtime classpaths, Workflow stores or caches.
  Product preparation/publication happens through its existing owner before the
  test; the test does not rebuild production from a source-tree shortcut.
- Distinguish normal cold online installation (when downloading is the supported
  route) from installation using an explicitly supplied normal artifact. A local
  artifact fixture cannot prove remote repository/download availability. An
  offline case is required only for the distribution's declared offline support;
  otherwise report the exact missing dependency without claiming offline support.
- Retain image/platform, prerequisite and product versions, distribution source,
  input settings and actual commands/results. Use existing artifact identities;
  introduce no new document-hash approval boundary or filesystem-wide scanning.
- Select a disposable output directory for evidence and per-case volumes. Allow
  network only as needed for the selected installation/toolchain case. Git effects
  use a test-owned local bare remote; no user repository or real remote is changed.
- A supplied test project is a small ordinary repository. Do not smuggle runtime
  config or preinitialized data into it to bypass the bootstrap being tested.

Start with one supported Linux/JDK/architecture combination. Record the actual
platform and expand to supported arm64/amd64 combinations after the main route.
Emulation results are labelled as such; they are not native performance evidence.
Docker does not qualify macOS launchers, Keychain, desktop permissions, native
Codex discovery, actual model availability or live UI delivery. Those require a
separate finite host check and remain explicit coverage boundaries.

## Coverage matrix

All rows are required acceptance dimensions for the supported product surface.
I1 records supported, planned-producer-gap, or explicitly out-of-scope for each
concrete variant. A promised supported path cannot silently become out-of-scope
because it fails. Planned capabilities retain named producer dependencies and
pending cases rather than passing skips. Exercise representative interacting
settings; do not run the full Cartesian product of every option and platform.

| ID | Scenario | Required observations |
| --- | --- | --- |
| CI-01 | Cold normal installation | Runtime/CLI and bundle install from normal artifacts in an empty HOME; version/help and executable permissions work without source checkout or local caches. Cold dependency resolution uses declared repositories. |
| CI-02 | Unconfigured project bootstrap | Initialization is reachable with no project `.textus/config.json`, `.textus/assembly.yaml`, DB or Session. Ordinary defaults/initialization create only needed resources; no hand-written private process-plan JSON or test-only bootstrap injection. No implicit Start or Git/SBT effect during initialization. |
| CI-03 | First workflow and restart | A small repository completes ordinary clean RepositorySync against a local bare remote, with real returned results and no unnecessary FULL. A new process retrieves retained state/result without duplicate effects. |
| CI-04 | Skill/agent/caller packaging | Each declared skill, required direct contract, caller and agent file is installed with usable discovery paths. Run finite caller formatting with observed test inputs; verify parent/subagent/chat markers and unknowns. No real AI/provider call or claim of desktop delivery. |
| CI-05 | Location portability | Alternate HOME/shared root/runtime/workspace/evidence roots, a path containing spaces and non-ASCII characters, and unrelated working directory work through supported locators. No hard-coded developer name, `/Users/...` or `dev2026` layout. Resolve supported symlinks according to the actual installer contract. |
| CI-06 | Configuration precedence | Inventory the supported default/shared/project/explicit/environment inputs and their actual documented precedence. Assert effective selected values and provenance for conflicting settings, absence and optional override. Cover supported repository/endpoint, proxy and CA settings with controlled endpoints and synthetic credentials; unsupported variants remain explicit. Do not invent a new precedence order in fixtures. Omitted optional config files alone do not fail startup. |
| CI-07 | Customized directories and data | Use supported resource locations and SQLite storage; data, Journal/Evidence references and configuration survive process restart. Unrelated files and repositories remain untouched; generated work/evidence are not accidentally Git-tracked. |
| CI-08 | Git and SBT selection | Missing Git executable override uses PATH; a supported explicit override is respected, absent executable has actionable diagnostics. Normal runtime-owned SBT on a tiny project uses >=4 GiB default heap, preserves larger `.jvmopts`, and respects the documented config property. Observe effective child JVM settings; do not infer them only from config text. |
| CI-09 | Duration and lifecycle customization | Configured execution budgets and SIGTERM cleanup use the owning runtime. Exercise a controlled longer operation beyond the old helper deadline without a fixed caller timeout; verify descendants settle and evidence remains retrievable. Do not run a one-hour test just to prove a configurable value. Distinguish container memory/OS kill from JVM heap exhaustion. |
| CI-10 | Operating profiles | When 6.10 is delivered, omitted/standard equivalence, explicit versus config precedence, cost/quality resolution, unavailable model diagnostics and preserved issued-work selections are tested without paid model calls. Before delivery, retain the dependency and do not claim the three-profile feature works. |
| CI-11 | Reinstall and initialization reuse | Dry-run is non-mutating; repeated identical install/init reuses resources without new Session/DB. Assert the supported installer's real reconciliation behavior rather than assuming all operations are atomic. |
| CI-12 | User customization conflicts | Existing agent/skill edits, unrelated Codex config, synthetic credentials and occupied/duplicate discovery paths are preserved. Differing shipped files produce a concrete reconcile/replace choice; explicitly selected replacement changes only its declared files. No blanket replacement of HOME or config. |
| CI-13 | Supported update and active work | Update from an explicitly selected prior supported test artifact to the candidate; new executions use the selected candidate while retained/active work follows the installed-runtime stability contract. Preserve Session, Plan, accepted results, histories, repair counts and successful checks. No forced re-Start or replay. |
| CI-14 | Failed/interrupted install and update | Controlled write denial, incomplete artifact/transfer and interruption leave attributable partial results; inspect/reconcile through public routes and retry only known unfinished preparation. Old usable installation is preserved where the contract promises it. No blind effect replay or claim of atomic rollback. |
| CI-15 | Invalid input and real prerequisites | Malformed/unsupported config, missing prerequisite tool, unavailable artifact, unavailable Docker and insufficient container resources have concrete diagnostics. Distinguish configuration error, network failure and resource failure. Never convert all-skipped/missing prerequisites into PASS. |
| CI-16 | Two independent installations | Two non-root installations/projects with different settings and owned stores can run independently; no cross-selection of config, credentials, runtime image or Session. Reuse supported parallel-execution behavior without a new machine-global lock. |
| CI-17 | Result retention and cleanup | Success/failure/interruption retain selected logs and structured results before test-owned containers/volumes are removed. Recollecting results causes no reexecution. No global Docker prune, deletion of unrelated volumes or loss of failed-run evidence. |
| CI-18 | Removal and recovery boundary | If supported, uninstall removes only managed installation files and preserves user data/config unless explicitly selected. If no uninstall API exists, test documented manual removal of a disposable installation; do not invent an API. Previous-runtime reselection is qualified only when compatible with retained data; unsupported downgrade is explicit, not automatic data rollback. |

CI-03 initially proves the smallest usable route; add a tiny runtime-owned SBT
validation through the public workflow path for CI-08, without requiring AI to
invent work. Mechanical semantic fixtures, if necessary for a separate case,
are labelled test inputs and never presented as real independent AI review.
For CI-09 use a controlled runtime-owned operation and explicit short test budget
to exercise timeout/cleanup plus a separate duration beyond the former caller
limit; do not add production timeout constants for test convenience.

## HEAVY selection, evidence and cleanup

Register executable specifications through canonical CNCF validation metadata:
HEAVY with a dedicated installation/customization feature and Docker/prerequisite
execution requirements. Use the actual producer vocabulary; new capability names
are proposals until registered. FULL/default/ADMISSION must not select these
operations. Do not use `@DoNotDiscover` or manually maintained suite lists as a
substitute for labels. Keep small pure installer/renderer tests in their existing
local categories; the Docker end-to-end cases are the HEAVY boundary.

Run through the owning validation runtime and its normal typed command/SBT paths.
Docker lifecycle belongs to the test harness/managed execution, not AI polling.
Use explicit Docker permission when needed; do not elevate or mount the host
Docker socket inside a test container. No nested Docker is needed. Validate the
actual test workload exits; container startup or CLI exit zero alone is not a
successful workflow assertion.

Each case records status, observed installed version/configuration, expected and
actual outcome, test-owned resource identities, cleanup status and Evidence
references. Use synthetic credentials and redact sensitive output. Store evidence
through the existing retention facilities outside Git; normally archive under
the configured journal/evidence destination, following the user's backup policy.
Capture evidence before cleanup. Cleanup is limited to the recorded owned
resources and must not conceal the original failure. A hard interruption may
leave a recorded resource for later explicit owned cleanup; no reservation scheme
or global process scan is required.

## Non-goals and preservation

No production install, active Session mutation, release publication, remote push,
real provider call, model evaluation, full database/service matrix or host-wide
cleanup is authorized by the plan. Do not make unsupported distro/platform,
proxy, offline or downgrade support a new product obligation merely to increase
test count. Document support decisions and remaining risks explicitly.
Reuse existing installer/config/runtime safeguards; tests must not add duplicate
production admission gates or arbitrary file-size/hash checks. Isolated failure
injection is test behavior, not a new production preflight framework.
