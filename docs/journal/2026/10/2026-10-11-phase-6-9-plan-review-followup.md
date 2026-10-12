# Phase 6–9 plan review follow-up

Date: 2026-10-11, Asia/Tokyo
Status: planning corrections applied; implementation and acceptance not claimed

The user requested application of the Phase 6–9 review and clarified that local
LLM checks are developer-owned reference work. Phase 5.1 remains in progress;
this change does not alter its execution or authorize Phase 6 implementation.

## Review dispositions

- CB-PLAN-01: Phase 8 now marks historical intermediate-recovery and competing-
  submission guarantees as superseded rather than mandatory carryover. Phase 7
  and P7-TS04 distinguish normal answer-driven continuation/evidence reuse from
  interrupted-effect recovery. Abandonment follows Phase 5.1's new-execution
  fresh-start boundary. Historical results and original IDs are retained.
- CB-PLAN-02: Phase 6 defines explicit adopted Goal/Phase overrides of project
  defaults, with owner resolution of conflicting adopted conditions during
  planning. The existing connected driver and P6-TS02/03 verify required-failure
  blocking, nonblocking excluded assurance, and consistent request, FIX,
  Review and Admission consumption. No separate review cycle is added.
- CB-PLAN-03: Phase 9 retains ordinary repair and automatic Fix counters across
  provider switches. Provider-specific limits cannot enlarge the existing
  shared allowance. Its bounded-termination scenario includes switching just
  before the shared hard limit and observing exhaustion.

Local LLM setup and capability trials are not Phase 6, 8 or 9 acceptance gates.
Phase 9 uses available admitted providers/profiles for its real-task routing
acceptance; deterministic scenarios exercise the policy branches. The manual
trial document remains reference information for the developer.

## Verification scope

Delivery allocation requested by the user: Phase 6 implements the reset
contract, including any missing runtime decision/guard/Step-close transitions
and normal skill/client wiring. Batch B, P6-TS05 and Phase 6 completion now
require connected reset acceptance before gradual use. Phase 9 reuses that
evidence and verifies preservation through provider switching. This is a plan
update, not an implementation or acceptance claim.

Further user decision: explicit approval to continue repairs resets both active
counts to zero. Retain cumulative history and convergence trends; one approval
covers a new counting window rather than one finding. Accepted Step completion
with its Step commit closes the loop and the next Step starts at zero; WIP,
checkpoint and within-Step repair commits do not. Updated the canonical cycle
policy, Phase 6/9 and connected checklist. These specifications do not establish
that current runtime/skill implementations already perform the reset and do
not modify the live Phase 5.1 counters.

User clarification: measured convergence governs ordinary continuation,
provider switching and stopping. Ordinary repair count contributes to that
judgment; its soft threshold alone does not stop converging work. Phase 9 now
explicitly states that ten automatic Fix cycles are an exceptional final
backstop, not a target or the normal stopping criterion. A successful tenth
result may complete; unresolved work cannot dispatch an eleventh automatic Fix.
Existing executable scenarios reflect this distinction without another gate.

Documentation-only change. Affected plan/checklist wording was read back and
`git diff --check` passed. No product edits, SBT execution, runtime acceptance, commit or push
are part of this change.
