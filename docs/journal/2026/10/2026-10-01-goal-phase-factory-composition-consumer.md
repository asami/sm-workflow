# GoalPhase factory composition consumer

Continue Phase 2 after CNCF producer validation 434 (57 passing tests).
Refresh the admitted unchanged local `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`
through publishLocal, not a shared warehouse or remote publication. Inspect
the exact resolved JAR and consumer classpath before attributing source changes.

Freeze the complete consumer test program GoalPhaseManagedSuspensionOperationSpec,
read entirely before editing. Replace its manually initialized review Component
with a real PrimaryComponentFactory using WorkflowComponentStorage inside
create_Core. Bind real managed instance/issued/completion dependencies there;
do not access private Subsystem members or retain a raw provider/pool. Keep
legacy append rejection and throwing UoW supplier and all completion/reopen/replay
assertions. Execute through the same Subsystem dispatch with explicit controlled
test runtime admission. No production authority store, permissive fallback,
launcher configuration, initial data reset or public skill mutation.

Run the five existing focused GoalPhase suites against the updated artifact.
This is downstream factory composition evidence, not deployment of the default
application factory, full Start-to-close production service or Phase closure.

## Refresh and focused consumer evidence (435/436)

Refresh `SMWF-CNCF-COMPONENT-STORAGE-REFRESH-DIRECT-20261001-435` reported
organization org.goldenport, name goldenport-cncf, version 0.5.3-SNAPSHOT and
published only to local Ivy. SBT/wrapper 0/0; serial lock released. No tests were
requested by that refresh. The exact consumer JAR is
`/Users/asami/.ivy2/local/org.goldenport/goldenport-cncf_3/0.5.3-SNAPSHOT/jars/goldenport-cncf_3.jar`,
SHA-256 `ee8ba908c6c12804d3d04db64bd3967110e9f91941ad4d96e01c48194f09e6c5`.

Consumer `SMWF-GOAL-FACTORY-COMPOSITION-CONSUMER-DIRECT-20261001-436`
reported that exact local JAR in Test/fullClasspath, recompiled 73 main and
63 test sources, and passed 41 tests in five suites. SBT/wrapper 0/0, serial lock
released. JAR hashes before and after were identical; git diff --check passed.
The downstream test now constructs its review Component through
PrimaryComponentFactory.createPrimaryC. create_Core observes the uninitialized
Component and binds all managed dependencies through WorkflowComponentStorage,
then registers the actual generated completion Operation. Normal Subsystem
dispatch, typed completion, bad revision rejection, replay rejection and exact
fresh-host recovery continue to pass without a legacy UoW or independent append.

- refresh log: `/tmp/skill.cncf.d/cncf-sbt-947828e2c039953598a516451b73f08dff5904a10900ee2e195134a3762cfbfa-7f62e41bc272b57ed25e1b0e2eaadf1b/4594-20260930T220951Z.log`
- refresh summary: `/tmp/skill.cncf.d/cncf-sbt-947828e2c039953598a516451b73f08dff5904a10900ee2e195134a3762cfbfa-7f62e41bc272b57ed25e1b0e2eaadf1b/4594-20260930T220951Z.summary.json`
- consumer log: `/tmp/skill.cncf.d/cncf-sbt-86c9e0e59addb35ebb4e2426db9af2c92c20589ac486c58eae4b3d776f53ac7a-4e95c0dfdd86070c82cfdd08d3d33704/5280-20260930T221047Z.log`
- consumer summary: `/tmp/skill.cncf.d/cncf-sbt-86c9e0e59addb35ebb4e2426db9af2c92c20589ac486c58eae4b3d776f53ac7a-4e95c0dfdd86070c82cfdd08d3d33704/5280-20260930T221047Z.summary.json`

This supersedes artifact 426 for current consumer identity only. It does not
complete the default SmWorkflowPrimaryFactory, Start selection and submission
authority storage, deterministic command/Step closure, repair continuation,
separate-process launcher/server/MCP equivalence or management visibility.
Next freeze an application-owned managed service composition using this proven
factory seam and explicit host authority ports; do not promote legacy fixture
runtime stores or turn absent authority into an automatic approval. All Phase
review, validation and acceptance gates remain open.
