# GoalPhase effective acceptance boundary

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

Review repair requires an actual unsatisfied required condition and exact
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

This equality defines semantic repair scope, with no unchanged-record proof.
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
| E3 | GoalPhaseAcceptanceBoundarySpec; NativeHostReviewPreparationSpec | Saved Plan/Start/Work/Result codecs and generated Record/JSON report and effective boundary |
| E4 | GoalPhaseAcceptanceBoundarySpec | Initial/repair required failure versus excluded/advisory completion |
| E5 | GoalPhaseAcceptanceBoundarySpec; NativeHostReviewScopeSpec; NativeHostReviewPreparationSpec | Owning Step/Phase dispositions, applicable subsets, scoped repair and meaningful independent target/alias admission |
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
