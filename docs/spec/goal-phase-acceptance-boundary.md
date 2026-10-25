# GoalPhase effective acceptance boundary

Newly issued ReviewStep work may additionally use the explicit typed
[Step purpose repair contract](goal-phase-purpose-repair.md). Its sources cover
every required failure exactly and bind additional independent purpose defects
to the issued Step scope. The legacy required-only form and all accept gates
below remain; ReviewPhase keeps its existing required-condition semantics.

Updated 2026-10-23: a phase-only configured boundary can honestly return
`decision-needed` with an actual finding and no applicable Step observations.
The legacy untyped form cannot return `repair` without unsatisfied applicable required
observations whose ordered details exactly equal the findings. Excluded ideas
and advisory-only results never become repair evidence. The separate narrow
[remaining Plan adoption](goal-phase-remaining-plan-adoption.md#narrow-review-decision-continuation-r8r12)
contract lets an independently admitted actual Human add exactly one fresh
required `scope=step` condition for newly issued and future unaccepted work,
bound to the actual saved Review finding, candidate and successful checks.
Existing raw/effective conditions and their sources remain exact. The old
review stays `decision-needed`; no unsatisfied historical observation is
fabricated, excluded condition promoted, or accepted work reassessed. Ordinary
repair Submit, candidate/check admission and independent review remain owed.
The source regressions are pending parent-owned execution and review.

This contract defines application quality for a selected GoalPhase task. It
does not define CNCF execution policy or issue permission, independent review,
Human consent, artifact, mutation, commit or repository authority. Workflow
continues to own the current assignment, native evidence, checks, CAS writes,
repair allowance, Step closure and whole-Phase closure.

The first connected example is the fourth normal Goal Operation loop. Its
responses and host evidence are explicitly specification-owned fixtures. This
contract's implementation and examples require parent-selected generation,
validation and independent review; their presence is not a verified acceptance
claim. Actual installed/provider/all-family Boundary acceptance remains open.

## R1 — Declared conditions and explicit adoption

The internal typed model is `Project(conditions)`,
`Adoption(usage, conditions)`, `Effective(usage: Option[String], conditions)`,
`Condition(identity, scope, required, description)` and
`EffectiveCondition(identity, scope, required, description, source)`.

Scope is exactly `all`, `step` or `phase`. `all` applies to ImplementStep,
RepairStep, ReviewStep and ReviewPhase; `step` applies to the first three;
`phase` applies only to ReviewPhase. These categories select quality conditions,
with no routing by individual Step name and no execution permission.

Identity is a canonical token matching `[A-Za-z0-9][A-Za-z0-9_.-]{0,127}`.
Description and adopted usage are nonblank, NUL-free UTF-8 text bounded to
8192 bytes. Each declaration has at most 64 conditions with unique identities.
Effective source is exactly `project` or `adopted`.

Start preserves project order. An explicitly adopted same-identity condition
replaces the whole project condition, including scope, description and
`required=false`; new identities append in adoption order. Untouched project
conditions retain their original meaning. Duplicate or contradictory adopted
identities fail before Start. Planning settles conflicts; admission never
unions stronger conditions or invents default quality requirements.

## R2 — Existing Component configuration and saved Start

Exactly one optional Component parameter, `sm-workflow.acceptance.project`,
is declared with the existing ComponentParameterDecoder and resolved by
ComponentInitializationParameters. It accepts an ObjectValue or JSON string:

```json
{
  "schemaVersion": "sm-workflow.acceptance-project.v1",
  "conditions": [
    {"identity":"behavior","scope":"all","required":true,"description":"Implement the selected behavior"},
    {"identity":"extra-robustness","scope":"all","required":true,"description":"Add extra robustness"},
    {"identity":"phase-closure","scope":"phase","required":true,"description":"Review complete ordered Phase closure"}
  ]
}
```

There is no secondary file lookup, environment override or hierarchy merger.
The existing CNCF configuration owner resolves hierarchy before this parameter
is consumed. The independently selected complete Plan may adopt this input:

```json
{
  "acceptance": {
    "usage": "Implement the selected behavior; extra robustness is excluded",
    "conditions": [
      {"identity":"extra-robustness","scope":"all","required":false,"description":"Keep extra robustness advisory"}
    ]
  }
}
```

This is an optional Plan field, alongside its existing identity, revision,
phase, repository, ordered Steps, checks and repair limit. It is not a separate
Start payload or permission. Start rejects a caller-supplied
`effectiveAcceptance`, validates project/adoption and captures the complete
effective value once in the existing saved Plan:

```json
{
  "effectiveAcceptance": {
    "usage": "Implement the selected behavior; extra robustness is excluded",
    "conditions": [
      {"identity":"behavior","scope":"all","required":true,"description":"Implement the selected behavior","source":"project"},
      {"identity":"extra-robustness","scope":"all","required":false,"description":"Keep extra robustness advisory","source":"adopted"},
      {"identity":"phase-closure","scope":"phase","required":true,"description":"Review complete ordered Phase closure","source":"project"}
    ]
  }
}
```

Both absent leaves an unconfigured Plan with absent optional fields. Explicit
empty project/adoption conditions retain an explicit Effective, even if no
conditions apply to a particular assignment. Pending/reopened/completed work,
repair and Human reconstruction consume the saved Plan. They never re-resolve
later host project defaults. An adopted but uncaptured Plan is invalid.

## R3 — Honest scoped result report

`GoalPhaseWorkResult` retains outcome, summary and findings and appends optional
`acceptance: Report(conditions: Vector[Observation], advisories: Vector[String])`.
An Observation has identity, status and detail. Status is exactly `satisfied`,
`unsatisfied` or `not-evaluated`. Reports have at most 64 distinct observations,
only for known applicable effective conditions. Detail is nonblank, NUL-free
UTF-8 text bounded to 8192 bytes. There are at most 128 advisories, each under
the same text rule. Every applicable required condition needs one observation;
excluded observations are optional.

A configured Effective requires a report, including an empty applicable subset.
An unconfigured Plan forbids a report. Existing outcome/summary/findings validity
and independent evidence checks continue to apply. For ImplementStep or Step
acceptance under the example boundary, an accepted report can retain exclusions:

```json
{
  "outcome":"completed",
  "summary":"Implemented the selected behavior",
  "findings":[],
  "acceptance":{
    "conditions":[
      {"identity":"behavior","status":"satisfied","detail":"Observed selected behavior in the assigned scope"},
      {"identity":"extra-robustness","status":"unsatisfied","detail":"Additional robustness remains excluded"}
    ],
    "advisories":["Consider extra robustness as a separate task"]
  }
}
```

Completed/accept requires every applicable required observation satisfied and
empty normal findings. Unsatisfied or not-evaluated required conditions block
acceptance. Excluded observations can be unsatisfied/not-evaluated without
blocking it. Unknown observations are rejected; other nonblocking ideas belong
in advisories. Phase review additionally observes `phase-closure`.

Legacy untyped review repair requires an actual unsatisfied required condition and exact
ordered projection of its observation details into findings:

```json
{
  "outcome":"repair",
  "summary":"Repair the assigned first boundary",
  "findings":["The selected failure case is missing"],
  "acceptance":{
    "conditions":[
      {"identity":"behavior","status":"unsatisfied","detail":"The selected failure case is missing"},
      {"identity":"extra-robustness","status":"unsatisfied","detail":"Excluded extra robustness"}
    ],
    "advisories":["Consider extra robustness separately"]
  }
}
```

This equality defines the legacy untyped semantic repair scope, with no unchanged-record proof.
Other required not-evaluated observations do not prevent repair of known
required failures, but still prevent acceptance. Excluded-only/advisory-only
repair fails before a state write or repair-count change. Structurally valid
decision-needed reports, including required not-evaluated, retain the existing
explicit Human route. They grant no new authority.

## R4 — Existing owning consumers

Initial and assigned repair result admission consume the saved selected Plan
and actual kind. Owning review admission obtains ReviewStep/ReviewPhase from
the controller before applying the shared quality gate. Its independent
EvidencePort, candidate/check receipts, review identity, repair directive and
existing repair budget remain authoritative. Implementation completion does
not become review acceptance, and a provider never selects native state.

Issued ContextBundle instructions refer to the existing saved Plan; they do
not add another boundary snapshot, dispatch executors or call providers.

## R5 — Generated public types and normal intake

The CML declares GoalPhaseAcceptanceCondition, GoalPhaseAcceptanceAdoption,
GoalPhaseEffectiveAcceptanceCondition, GoalPhaseEffectiveAcceptanceBoundary,
GoalPhaseAcceptanceObservation and GoalPhaseAcceptanceReport. OrderedPlan
appends optional `acceptance` and `effectiveAcceptance`; WorkResult appends
optional `acceptance`. Normal generated wire names include
`effective_acceptance`. Nested fields are usage/conditions/identity/scope/
required/description/source/status/detail/advisories.

Only normal Cozy generation owns generated target Scala. Pure public/internal
projections retain invalid nested nulls for typed validation rather than
throwing or inventing data. Public work, requesting result and Human views,
strict generated Record/JSON intake and normal typed Submit retain the values.
No generic JSON result escape, handwritten generated adapter or additional
operation is introduced.

## R6 — Meaningful independent review scope

Review targets include the actual optional effective boundary and verdicts
include the actual optional report. Typed selection validates complete
condition scope/requirement/source and observation/status/advisory meaning.
Absent/null optional fields are omitted consistently. Changing meaningful
quality target or disposition cannot reuse an unrelated attestation.

Passive review preparation converts the generated Report and supports fixed
`effectiveAcceptance`/`effective_acceptance` aliases. Agreeing aliases are
admitted; contradictory names fail. Existing testChecks aliases and structured
SBT facts remain. Independent evidence still targets the actual candidate and
semantic repair direction. No full Plan/diff/log copy or custody certificate
is added; unrelated Plan presentation is outside the selected target.

## Executable examples and acceptance limits

| Example | Executable specification | Selected behavior |
| --- | --- | --- |
| E1 | GoalPhaseAcceptanceBoundarySpec | Existing Component resolution, complete override/exclusion and untouched defaults |
| E2 | GoalPhaseAcceptanceBoundarySpec | Duplicate/null/invalid configuration or adoption; scoped report/status/identity/bounds rejection |
| E3 | GoalPhaseAcceptanceBoundarySpec; NativeReviewPreparationSpec | Saved Plan/Start/Work/Result codecs and generated Record/JSON report and effective boundary |
| E4 | GoalPhaseAcceptanceBoundarySpec | Initial/repair required failure versus excluded/advisory completion |
| E5 | GoalPhaseAcceptanceBoundarySpec; NativeReviewScopeSpec; NativeReviewPreparationSpec | Owning Step/Phase dispositions, applicable subsets, scoped repair and meaningful independent target/alias admission |
| E6 | GoalPhaseAcceptanceBoundarySpec | Unconfigured compatibility and refusal of forged report or uncaptured adoption |
| E7 | ThreeWorkflowGoalOperationSpec, fourth configured whole-Phase variant | Normal Submit required-report/failure rejection with unchanged pending state |
| E8 | Same fourth variant | Original project/adopted meaning across changed host defaults, SQLite reopen, repair and Human |
| E9 | Same fourth variant | Separately fixture-prepared excluded-only repair rejection, true required repair, accepted excluded advisory and two-Step whole-Phase terminal |

The connected loop uses normal Start/Advance/Submit, separately prepared host
review/Human input, typed Git, actual ordinary SBT and repository closure in
its owned local fixture. It preserves the original three variants. Fixture
actors are not actual independent models/providers. A newly installed task
needs normal assembly refresh and actual installed/provider/session/dialogue
and all-family Boundary acceptance. Existing security/CBD, Step acceptance,
sole full Phase review and release obligations remain open; PHASE-6.3 remains
the aggregate full-suite owner. Completed native task effects are not replayed.

## Normal CLI connected examples

`NativeGoalAcceptanceBoundaryCliSpec` is an explicit `runtimeTestOnly` suite
with two examples (E10 and E11). E10 uses normal `prepareGoalStart`, managed
import, Start, Advance and issued Submit to observe ordered project defaults,
complete adopted replacement/exclusion and saved effective conditions after a
later host configuration change. Missing/not-evaluated required implementation
reports fail without changing the pending request. One real typed Test of the
fixture-owned `value.txt` correction produces the actual ReviewStep and its
effective review target. Normal `prepareEvidence`, managed import and issued
Submit refuse excluded-only repair and not-evaluated required acceptance;
the valid excluded-advisory response reaches StepClosure Wait without authority
or completion. Repeated Advance retains that Wait without another Test. E11
uses normal preparation and Start to refuse contradictory duplicate raw adoption
before caller routing or SBT execution.

All declarations, semantic responses, reports and review execution identities
in these two examples are specification-owned test inputs. The examples cover
real CLI persistence/admission and one real typed Test; they establish no genuine
Human approval, independent review, actual AI authorship, Step acceptance,
source-policy-review evidence or whole primary acceptance. They do not replace
genuine I2 review or discharge the existing Phase acceptance obligations.
