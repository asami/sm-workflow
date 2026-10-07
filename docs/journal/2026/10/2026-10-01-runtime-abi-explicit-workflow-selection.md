# Explicit Workflow selection from official runtime ABI

Continue Phase 2 and parent-direct limited recovery in the already admitted
sm-workflow and CNCF prerequisite repositories. Previous turn closed the
official ABI packaging gap; it did not establish runtime catalog admission.

Own CNCF CandidateWorkflowAbi.scala and its focused spec, sm-workflow
GoalPhaseRuntimeCatalog.scala and its focused spec, and this journal.
Also own the default version literal in project/plugins.sbt: select the
already validated and locally refreshed 0.1.18-SNAPSHOT so normal Compile
resource generation supplies official resources without a test-only override.
Retain both existing explicit version overrides. No release version is changed.
Preserve all unrelated dirty changes. Work class C: pure cross-repository
receiver/binding admission. No case-class shape, persisted identity schema,
Operation, CML, provider/pool, default launcher configuration or authority
change. The additive parseSelectedC receiver is a protected API boundary;
freeze compatibility and validate binding re-admission before downstream use.
Independent acceptance review and Phase closure remain separate unfinished gates.

Keep parseC's exactly-one contract. Add explicit identity/version selection
for a complete official artifact, rejecting absent/ambiguous selection and
malformed siblings. Preserve rawJson and SHA-256 over all original bytes;
never reconstruct a per-Workflow document. Definition re-admission must use
the recorded Workflow key and still compare every reconstructed Definition
fact, so persistent bindings can validate the same complete artifact.

The application uses its compiled GoalPhase identity/version, reads exact
UTF-8 from META-INF/cozy resources, rejects missing, duplicate or invalid
resources without fixture fallback, and closes streams. Catalog loading does
not acquire storage or create human authority. Keep the existing source CML
correlation identifier. Add executable cases for the actual official three-
Workflow resources, isolated main-JAR resource loading, absent resources and
malformed input, alongside existing catalog and managed-operation scenarios.

Validation: CNCF CandidateWorkflowAbiSpec, producer ABI and persistence-binding
specs; derived local CNCF SNAPSHOT refresh after producer source changes;
downstream previous eight-suite accumulator with 0.1.18-SNAPSHOT plugin
and the packaged-JAR test property after packageBin. No release, public
publication, push or commit is selected.
Full compiled producer policy correlation remains a separate next obligation;
do not claim that metadata-only Workflow comparison proves that obligation.

Attempt 477 compiled the changed receiver but stopped during Test compile:
one new ScalaTest assertion put `shouldBe` at the start of a continuation line,
which Scala 3 parsed as a separate expression. Parent-direct mechanical fix
moves that operator to the preceding line. SBT/wrapper exited 1 and lock
released; no tests ran and publishLocal was not reached. Retain the failed
attempt and rerun the same bounded validation after this syntax-only correction.

Attempt 478 stopped at the Cozy launcher stale-development-classpath check
before tests or publishLocal. Main receiver compilation in attempt 477 changed
the mutable CNCF runtime classpath consumed by Cozy. Refresh through official
cozyExportRuntimeClasspath (Cozy validation/artifact scope only), then resume
the same command without bypassing freshness checks. Default catalog loading
uses its own code-source JAR/classes with a parentless resource loader, rather
than scanning dependencies for a shared filename. Explicit loader admission
still rejects duplicate resources. No live SBT session remains from 478.

Attempt 479 successfully refreshed the official Cozy development runtime
classpath with cozyExportRuntimeClasspath after waiting for the shared lock;
no Cozy source was changed. Attempt 480 then passed all 29 tests in the three
selected CNCF suites and refreshed the local CNCF SNAPSHOT. SBT and wrapper
exited 0 and the shared lock was released. The complete structured summary
was read at
`/tmp/skill.cncf.d/cncf-sbt-5b40ba5e96ef93b6082409d478149bb3c14df18b4404edfac14133a77ad92b48-817f59ba65e53ba0ddd903885089c736/27596-20261001T011006Z.summary.json`.

Attempt 481 selects sm-workflow Compile / packageBin followed by the eight-suite
accumulator, with smworkflow.runtime.abi.jar pointing at the actual main JAR.
This verifies the updated catalog against packaged official resources, not a
synthetic JAR or single-Workflow fixture. Its result is pending. No independent
SBT invocation may start while this attempt owns or waits for the shared lock.

Attempt 481 completed successfully: actual main packageBin plus all 60 tests
in eight suites passed, with zero failed, canceled, ignored, pending or aborted.
SBT/wrapper exited 0 and the shared lock was released. The complete structured
summary was read at
`/tmp/skill.cncf.d/cncf-sbt-53633c9baa58eade208f764aea13381a5d903209c12b14027dab461597eea3c5-b706464807b2c4d46f7fdc378fe25866/29967-20261001T011414Z.summary.json`.
The catalog spec loaded the actual packaged main JAR using a parentless loader,
also verified default own-code-source loading, retained all three official
Workflow definitions in the exact raw text, validated the selected GoalPhase
persistence binding, and rejected missing and duplicate resources. Existing
managed review/repair/re-review reopening and interrupted-follow-up tests remain
green. No generated fixture fallback or provenance/freshness bypass was used.

The sbt-cozy and sm-workflow tracked diff checks passed. No invocation remains
live from this validation. This establishes official resource packaging and
explicit Workflow selection, not full Phase 2 acceptance or default CLI runtime
integration. Full producer-policy correlation must next compare the received
GoalPhase ProducerModel with the compiled
GoalPhaseWorkflowCandidateAdmissionProducer1.producer, including Judgment
alternatives, criteria, evidence semantics and source correlations, Admission
relations, operation contracts and Required-SPI facts. The compiled descriptor
is present in main generated output; metadata-only Workflow comparison is not
proof of that policy. Trusted host configuration, public interrupted-follow-up
recovery, managed external commands and independent acceptance review remain
unfinished. No public publication, push, commit or Phase closure was performed.
