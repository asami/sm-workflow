# Output-limit repair v3 — proposed single-file ABI addendum

Status: **proposal only; not approved, not applied** (2026-10-24).

The approved v3 contract and its45 authored paths remain unchanged. Its worker
stopped before touching an omitted authored ABI file. The parent has directed
the same worker to finish independent work inside the original45 paths; that
does not authorize this addendum or any real consumer Session operation.

## Exact additional file and candidate

Additional production file, if separately approved:

`src/main/car/abi-manifest.json`

Prepared, unapplied exact delta:

- `/private/tmp/sm-output-limit-v3.D91Lt1/abi-manifest.candidate.patch`
- `/private/tmp/sm-output-limit-v3.D91Lt1/abi-manifest.candidate.json`

`git apply --check` succeeded against the current producer file. The actual
source file has no working-tree diff. This is applicability evidence only;
no compilation, ABI acceptance, installed verification or publication is claimed.

## Concrete delta

Add declarations for the two Operations already approved in v3; no new
Operation beyond that contract:

| Service.Operation | Input | Output |
|---|---|---|
| GoalPhaseService.describeGoalPhaseValidationRepair | GoalPhaseValidationRepairDescriptionInput | GoalPhaseValidationRepairDescriptionResult |
| GoalPhaseService.adoptGoalPhaseValidationRepair | GoalPhaseValidationRepairAdoptionInput | GoalPhaseValidationRepairAdoptionResult |

Add the four already approved value declarations exactly matching CML:

- DescriptionInput: sessionId, proposal, optional priorInvocation.
- DescriptionResult: sessionId, outcome, message, componentInstance, target,
  choices, adoptionOperation.
- AdoptionInput: sessionId, target, proposal, choice, rationale, reference.
- AdoptionResult: sessionId, accepted, outcome, message.

Append one optional `record` field named `processObservation` to each existing
`GoalPhaseNativeCheckReceipt` and `RepositorySyncNativeValidationReceipt` value.
The latter is shared by the two RepositorySync projections. This describes
the already approved v3 projection, not a further raw-wire/schema extension.

**No additional configuration key** is proposed. V3's one optional resolved
caller-source policy remains the complete configuration scope. No additional
DTO, provider, process launcher, script, transport or public effect is added.

## Existing ABI and generation relationship

Preserve the existing format, component coordinate/version, ABI version,
services, requirements, old Operations, types and all old field declarations.
Only the two existing receipt values gain the optional field; the two new
Operations and four new values are additive. Keep every other export unchanged,
including unrelated pre-existing omissions or drift; do not use this addendum
to synchronize all historic CML/ABI differences or close an unrelated ABI failure.

The repository's `ComponentPackagingDescriptorSpec` reads this **authored**
source-managed ABI. Ordinary sbt-cozy packaging writes its output to
`target/cozy/abi-manifest.json`, while Cozy recognizes the existing source ABI
as a packaging input. It does not regenerate this authored file in place.
Changing a test to read a different authority would hide this dependency and
is not proposed. Generated files remain ordinary task output and must not be
hand-edited.

## Additional acceptance within the existing focused verification

If approved, extend the existing worker manifest by exactly this one file
(45→46 authored paths), with no change to v3 semantics, existing Goal/Session,
base, accepted results, repair counts or verification ownership.

Use the already owned ComponentPackagingDescriptorSpec and generation/ABI
checks to verify the exact two added operation signatures, four value shapes
and two optional receipt fields, preserving the remaining authored exports.
During the existing normal publishLocal/runtime qualification, inspect the
actual CAR ABI and generated public Operations for agreement on these v3
additions. Include this delta in the same independent focused review, not a
new review or Phase-testing lap. Retain previous unrelated ABI failures as
separate evidence unless an actual current scoped change resolves them.

No real Session Start, Submit, Advance, adoption, recovery or retry is added.
Real Session effects remain with sm-workflow-dev開発. No commit/push is included.

## Scope decision requested

Approve only the additional authored ABI file with the exact additive candidate
above and its existing focused verification/publication checks. Otherwise,
retain this one blocker and all partial work without widening ownership or
claiming the packaged v3 contract complete.
