# Phase 6.1 Step Admission: minimum provider connection

Date: 2026-10-17
Status: bounded producer and consumer focused validation passed; real provider/Submit/Advance/local commit remain open
Owner: sm-workflow Phase 6.1, P61-ADMISSION / P6-SQ01

## Superseding allocation — 2026-10-18

The [production-first decision](../journal/2026/10/2026-10-18-production-first-cbd-review-phase-10.md)
moves this CBD provider integration to [Phase 10](../phase/phase-10.md).
P61-ADMISSION retains ordinary reviewer-result/authority/commit acceptance in
6.1, without mandatory CBD. The dependency, authorization and numbered results
below are retained history; they do not require CBD15/15.1 before 6.1 closure or
claim the revised initial-use route already works. Keep all source and evidence.

## Earlier adopted execution separation — 2026-10-18

The owner separated CBD Phase 15 scoped evaluation from Phase 15.1 external
ReviewRequest execution. The [handoff](../journal/2026/10/2026-10-18-cbd-phase-15-15.1-review-handoff.md)
and [consumer specification](../spec/goal-phase-scoped-quality-admission.md)
govern the next implementation boundary. P61-ADMISSION/Phase closure awaits both
accepted required CBD handoffs; bounded owner acceptance may precede unrelated
owner work. Preserve all recorded source, tests and accepted PLAN/COMMAND work.

The synchronous CBD AiRunner connection described below is the retained starting
point and standalone basis, not the target orchestrated completion route. Prepare
the request before issuing semantic work, accept its external typed result and
continue the same Review through CAM/Submit/Advance. Do not expand the old internal
AI route merely to obtain consumer acceptance. Required independent review stays
explicit; same-context review is ordinary where independence is not required.
External executor attribution must not be invented as Textus AI SPI metadata.
Existing purpose-registration/SPI checks remain valid for the standalone route.

These are documentation/planning changes, not permission to reset active state,
retarget in-flight work or relabel prior fixture evidence as actual execution.
This plan's earlier authorization paragraph and counts remain historical records;
the new allocation changes neither source history nor completed acceptance.

Live adoption and the actual caller/gap map are recorded in the
[2026-10-18 continuation journal](../journal/2026/10/2026-10-18-phase-6.1-cbd-external-review-adoption.md).
SQR-04/RR06 owner acceptance remains pending. Existing evidence is reused only
for its original conditions; synchronous Submit is not the external-route check.

## Required observable outcome

Normal GoalPhase commands consume actual Cozy CML metadata, scoped lint and an
independent CBD semantic review. Runtime evaluates their typed Evidence through
the existing CNCF Candidate-Admission Model (CAM), then uses the existing typed
Git/UoW closure to make the authorized local Step commit. A provider's `accept`
string, a completed development review or an evidence-shaped fixture alone
does not satisfy this route.

The existing Goal, original bases, PLAN epoch, review history and repair counts
remain in force. P61-PLAN and P61-COMMAND are accepted and locally committed.
P61-ADMISSION is incomplete. Repository-full validation remains deferred-not-run
to Phase 6.3; the sole Phase 6.1 comprehensive review and release remain open.

## Reuse and concrete gap

| Owner | Reuse | Missing connection or limitation |
|---|---|---|
| CNCF | `CandidateAdmissionModel` typed requirements, evidence, subject/revision, coverage/applicability policy and explicit gaps | Bind actual producer results to this existing evaluator; introduce only a demonstrated missing generic request/result contract, without another Admission engine |
| Cozy | Ordinary `cozy.cml.model-metadata.v1` output: component, services, operations, input/output declarations, model elements, declared relationships and implementation descriptions | Select the actual relevant model scope with origins and explicit limits; do not infer generated-file relationships or complete usage contracts from names or descriptions |
| CBD | Existing CNCF `ProviderEngine`/UoW pattern, bounded `AiRunner` integration, deterministic quality rules and source-use diagnostic | A bounded scoped-quality request/result that uses ordinary identities/revisions, reports real semantic findings and coverage, and can participate in Admission |
| sm-workflow | Native pending Review, owning host evidence reader, normal Submit/Advance, typed Git/UoW Step closure | Assemble actual scoped producer input, consume CBD output as typed CAM Evidence, and finish the actual workflow commit |

CBD's current `CarReviewProviderBundleAdmission` requires target, request and
bundle digests. `CarReviewQualityProviderAdmission` composes that same admission;
it is not a hash-free alternative. `TextusAiCarReviewProviderRunner` additionally
marks AI output advisory-only and rejects an empty findings collection. Its
existing output cannot be relabeled as a clean independent semantic review.
The existing `reviewManagedEffects` operation classifies supplied effect-use
facts; it does not supply the missing semantic Step review.

The proposed scope does not migrate the whole historical CAR Review API or
reopen its accepted behavior. The new needed contract must have one ordinary
typed route and explicit unsupported/gap results, with no legacy hash fallback.

## Authorized minimum CBD addition

Use the existing `textus-cbd-support` main checkout. Add a bounded private
quality-review operation through its ordinary CML/service/ActionCall boundary,
using a fixed provider through `ProviderEngine` inside UoW. Keep purpose-profile,
provider/model selection and credentials with CNCF `AiRunner`.

The request carries a declared review identity/revision, actual subject
identity/revision, requested checks, selected paths/model subjects, relevant
requirements and bounded review material. These references associate inputs;
they do not certify unchanged content or grant an effect permission. Do not
accept arbitrary host paths, executable names, argv, shell commands or caller
assertions of a successful check as provider proof.

The result distinguishes completed evaluation, unavailable/unsupported coverage
and execution failure. Completed evaluation reports selected and covered scope,
typed findings/disposition, applicable rule/provider references and actual
execution provenance. A successful structured review may have zero findings;
absent, failed, malformed or insufficiently covered output cannot become PASS.
AI semantic findings cannot override deterministic failures. CBD owns the rule
meaning; sm-workflow owns the selected lifecycle requirements and progression.

Expected CBD edit areas are the CML declaration, ComponentFactory/ActionCall
binding, one bounded runtime provider and its connected specifications plus the
necessary contract documentation. Freeze exact paths and the protected
contract before implementation. This is the minimum P6-SQ01 delivery, not
authority to start or close CBD Phase 15/16 or implement their full backlog.

## Consumer and validation order

1. Freeze the finite typed contract and actual producer/consumer map. Reuse
   existing CAM and metadata before adding producer functionality. Record a
   typed repository addition before any newly admitted upstream edit.
2. Implement the bounded owner connection and normal sm-workflow consumption.
   All configuration comes from the current `ExecutionContext`; operation
   parameters remain operation inputs. Use existing resource and provider/UoW
   paths, without custom mains, direct Process requests or ad-hoc HTTP.
3. Run selected producer contract/provider specifications and affected consumer
   specifications with ordinary build compilation. Do not invoke
   `cozyReviewSbtEvidence`/`cozyReviewSubmit` as a focused shortcut: their current
   dependency runs `Test/test`, which remains Phase 6.3's responsibility.
4. Use an isolated validation plan, never product Phase 7. Verify real CML
   metadata -> scoped deterministic/independent semantic results -> CAM
   Admission -> normal Submit/Advance -> actual authorized local Step commit.
   Keep C4's actual pending Review, semantic change, generated history and
   checks; finish its review/Admission/commit through the same supported route.
   If C4 has no applicable CML subject, report that limitation explicitly and
   use an actual CML-backed scenario for required model-scope coverage.
5. Cover missing/wrong subject or scope, insufficient coverage, provider failure
   and a blocking finding without committing. Verify that covered/unaffected
   security requirements cause no extra provider call. Reuse applicable actual
   evidence; there is no new per-finding or per-helper review stage.
6. Use the already required protected independent ADMISSION Step review,
   applicable focused repair, accepted local development commits and the
   existing Phase 6.1 comprehensive review/release sequence.

Development SBT uses the registered runner and shared serial execution policy.
Product Git/SBT remains thin typed DSL execution inside UoW, using normal
executables and sbt's own locks. No scripted prerequisite, product-wide serial
wrapper, hash/copy/TTL control, external publication, push or deployment is
introduced by this plan. A real external-provider call still needs its exact
existing execution authority; adding a source repository does not grant it.

## Authority boundary

The adopted integration direction already permits necessary CNCF and Cozy
changes in their selected main checkouts. The earlier CBD addition covered the
Capability Laundering minimum rule and its existing CNCF dependency integration.
It did not explicitly add this scoped semantic-review provider. The Phase 6
[shared-scope rule](../phase/phase-6.md#shared-scope-and-adopted-integration-direction)
requires separately scoped authority for this missing CBD capability. The native
approval “CBDを追加して最小のレビュー接続を実装する” admits this minimum
addition. It is recorded as `p61-cbd-scoped-review-addition@1` in the existing
Phase 6.1 review ledger; original bases, history and repair counts are preserved.
The approval permits the bounded source connection, focused validation and
normal-command verification. Implementation and acceptance remain open; push
and deployment are excluded.

## Current bounded delivery

The new CBD provider passed its normal CML generation/compilation and four
focused suites (16 tests). This covers the actual generated private QUERY,
factory, Provider/UoW and recording-AiRunner wiring, together with existing
managed-effect consumers. It is not real external-provider acceptance.
ADMISSION repair 2 records the QUERY correction and the shared test fixture's
required empty-array correction. Closed COMMAND 5, PLAN 11, Phase 0 and prior
Phase 6 repair 79 remain unchanged.

The consumer decodes the ordinary CBD Operation Record into internal typed
results for existing CNCF CAM requirements/evidence and explicit coverage gaps.
CBD remains a CAR component; sm-workflow has no Maven dependency on its
generated classes. Actual Cozy metadata, the owner-selected `textus-ai-runtime`
connection and normal Submit/Advance/local Step commit remain required. Its
checkout is identified below; no fixture is promoted to real execution evidence.

References: [Phase 6.1](../phase/phase-6.1.md#staged-quality-review-across-projects--2026-10-13),
[shared review matrix](../journal/2026/10/2026-10-13-staged-quality-review-matrix.md),
[security review profile](../phase/phase-6-security-review-profile.md).

## Actual runtime location and bootstrap contract

The owner identified `textus-ai-runtime` in the `textus-ai-core` checkout.
That runtime consumes ordinary component-Port application-purpose registrations.
CBD therefore registers its fixed `car-review.semantic-consistency` purpose
with the existing `software-analysis` standard purpose. Textus AI retains model,
provider, execution-class and deployment-policy selection; configuration does
not create the registration. This is the existing approved provider connection,
not a new Textus AI source-change scope. Its source remains read-only.

The ordinary Record-to-CAM consumer adapter passed focused validation. ADMISSION
repair 3 covers the bootstrap gap; repair 4 corrects the CAR boundary and removes
the erroneous generated-class Maven dependency. The normal Submit acceptance
hook now connects the actual CBD Operation after independent host admission,
using current ExecutionContext configuration and the existing File DSL. Three
focused suites passed 107 tests with both process exits zero and the development
SBT lock released. ADMISSION repair 5 corrects only the consumer specification's
ordinary optional-array fixture and request field expectation; production
response validation stays strict. Real provider/Submit acceptance remains open.
The retained C4 manual closure consumer passed ordinary test compilation. It
requires separately obtained actual accepted Submit output and native local
effect authority before checking the normal Wait/preparation/Advance/commit
route. Compilation establishes no runtime acceptance or model-scope coverage.
The normal metadata/provider/Submit/Advance route remains open. Earlier repairs,
accepted Steps, original Goal/base,
PLAN epoch and the single comprehensive-review obligation remain unchanged.
