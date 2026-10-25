# Runtime-owned SBT JVM memory and Control Center handoff

## Decision

Normal SBT launch belongs to sm-workflow through the CNCF typed SBT DSL.
The default maximum heap is at least 4 GiB; larger project `.jvmopts` values
must survive. The user additionally requested ordinary configuration support.
Manual or exceptional standalone SBT uses standard SBT configuration. No new
global SBT launcher or per-validation-program fixed JAVA_OPTS is introduced.

The runtime property is `cncf.sbt.jvm.maximum-heap-mib`, numeric MiB, default
4096. A selected runtime project's `.textus/config.json` can contain:

```json
{ "cncf.sbt.jvm.maximum-heap-mib": 8192 }
```

The selected maximum is at least this baseline and the larger explicit target
settings. Values below 4096 do not satisfy the Textus policy. This is neither
initial allocation nor a guarantee against all future OOMs.

## Implementation boundary

CNCF `SbtJvmParameters` resolves ordinary configuration and the admitted
workspace's JVM/launcher options. `SbtExecutionAdmission.resolveC` applies it
after normal target/scenario/installed-runtime selection and before driver
execution. Thus Compile, Test, TestOnly and FixCheck share one implementation.
sm-workflow's existing protected DSL calls need no duplicate launcher logic.

Only runtime-derived JVM arguments and explicit JVM option environment tokens
change. Selection, capability, cwd, deadlines, capture limits, process lifecycle
and durable workflow records retain their existing owners. The implementation
does not read config by ProcessExec or inherit arbitrary caller environment.
It reads current optional settings for each new admission, changes no user
files and does not automatically retry an old invocation.

The source is in the sibling goldenport-cncf checkout:

- `src/main/scala/org/goldenport/cncf/processexecution/SbtJvmParameters.scala`
- `src/main/scala/org/goldenport/cncf/processexecution/SbtExecution.scala`
- `docs/spec/typed-sbt-execution.md`, SBT-JVM-01
- `docs/design/typed-sbt-execution.md`, JVM memory ownership

Repository identity is authoritative; machine-specific dev directories are not.
The source and installed runtime must be distinguished when adopting this work.

## Skill-side correction

The source and deployed cncf_command_runner contract now delegate normal Phase
SBT to sm-workflow. The exceptional standalone wrapper no longer injects
`-mem 4096`/G1, which could overwrite a larger `.jvmopts`. It leaves memory to
standard SBT config and retains its existing permission, argument, log and
termination handling. The semantic sm-goal-phase skill and workstation caller
skill prohibit labeling an ordinary Phase check as an independent development
test to launch it outside the runtime.

The earlier experimental global adapter was withdrawn and the original
Coursier entry restored. That prototype is not delivery evidence. Shared
home-config files were not edited; concrete changes there still require the
user's requested human confirmation.

## Adoption instructions for textus-control-center development

1. Preserve the existing Phase 5.1 Session, accepted Submit, pending work,
   history, repair counters and all previous verification results. Do not
   restart, replay acceptance or reinterpret the 1 GiB OOM as success.
2. Adopt the qualified producer change into the runtime actually selected by
   the existing consumer. Do not copy the entire dirty sibling checkout or
   overwrite its unrelated work. The required producer delta is the new
   SbtJvmParameters source and its call in SbtExecutionAdmission.resolveC;
   related specs reserve the contract. Preserve current typed SBT selection
   and dependency work already present in that runtime.
3. Keep the existing SBT executable and the five per-program JAVA_OPTS proposals
   unapplied. Default 4096 requires no new property. When an approved increase
   is needed, set the ordinary property on the selected runtime project, not
   a separate fixed environment value on every check. Larger `.jvmopts` in the
   actual SBT cwd are automatically retained.
4. Verify the selected rebuilt runtime includes this producer before normal
   continuation. Source tests alone do not establish installed artifact adoption.
   Then use the existing native owner and its permitted continuation; retain
   actual new JVM/build outcomes separately from earlier failures. Do not add
   a direct SBT fallback or reset an exhausted repair window.
5. Report actual consumer validation and any concrete adoption gap. This
   handoff does not claim Phase completion, publication, installation or a
   successful consumer build without its actual result.

## Verification scope and evidence location

Focused producer specifications cover defaults, larger project/config values,
explicit environment precedence, unchanged operation intent and local-driver
dispatch. An explicitly selected real SBT shell-parser check uses a recording
Java substitute: it proves final JVM argv, not a second build or Phase acceptance.
The standalone wrapper regression similarly uses a recording executable.

Live validation receipts/logs are outside Git in goldenport-cncf's
`.codex-workflow/disposable/`; task indexes are under its ignored `target/`.
Existing failed and successful receipts remain available. Consumer evidence
stays at its original Session-owned locations. No active record is relocated.
