# Reasoning Model Routing Review

Date: 2026-10-02
Status: review conclusion
Related: [Phase 5](../../../phase/phase-5.md), [Semantic Reasoning Classes and Runtime Mapping](../09/2026-09-30-semantic-reasoning-runtime-mapping.md), [Implementation Class and Model Routing](../09/2026-09-30-implementation-class-model-routing.md)

## Review question

Review whether the current sm-workflow reasoning model can naturally handle a development policy that uses GPT-6.1 Luna aggressively for implementation while reserving GPT-6.1 Sol for engineering judgment and stronger review.

The current operating hypothesis is:

- use Luna as an inexpensive execution/programming worker where design decisions are already settled;
- use Sol for planning, unresolved engineering decisions, and judgment;
- use stronger reasoning for review than for routine implementation;
- keep model and provider-specific effort choices out of Workflow semantics.

## Conclusion

The current architecture already has the correct abstraction boundary. No redesign of the logical reasoning model is required.

Phase 5's `ReasoningClass` model separates semantic work purpose and semantic reasoning demand from concrete provider/model/effort controls. Runtime mapping then resolves a semantic class to a concrete execution profile. This is the appropriate mechanism for Luna/Sol routing.

The existing PROGRAMMING / ENGINEERING distinction recorded in the implementation-routing journal is also aligned with this policy:

- PROGRAMMING means that Plan has already settled the relevant design decisions and the worker mainly translates admitted intent into implementation artifacts.
- ENGINEERING means that implementation still owns unresolved architectural or engineering judgment.

PROGRAMMING can therefore be routed aggressively to Luna. ENGINEERING should normally be routed to Sol.

## Current mapping hypothesis

The following is an operational mapping candidate, not Workflow semantics:

| Semantic responsibility | Initial runtime candidate |
| --- | --- |
| Planning Standard / Deep | GPT-6.1 Sol / high |
| Coding Standard / PROGRAMMING | GPT-6.1 Luna / high |
| Coding Deep / ENGINEERING | GPT-6.1 Sol / high |
| Review Standard | GPT-6.1 Sol / high |
| Review Critical / broad full review | GPT-6.1 Sol / xhigh |
| Judgment Standard | GPT-6.1 Sol / high |
| Judgment Critical | GPT-6.1 Sol / xhigh |

The exact mapping remains configuration. In particular, `CodingStandard = Luna/high` must not be encoded into Workflow definitions or persisted semantic state.

## Luna high versus Luna xhigh

Luna/high should be the initial default for PROGRAMMING work.

Luna/xhigh should not initially become an automatic intermediate escalation step between Luna/high and Sol/high. When Luna encounters an unresolved design decision, the problem is not merely additional reasoning effort: responsibility has crossed from PROGRAMMING into ENGINEERING. The worker should yield through Continuation and let Plan/engineering reasoning resolve the issue.

Operational evidence may later identify classes of work where Luna/xhigh improves admission rate without requiring engineering judgment. If so, runtime policy can add that mapping without changing Workflow semantics.

## Continuation boundary

A PROGRAMMING worker must not invent a design decision in order to complete its task.

The intended flow remains:

```text
Plan / engineering judgment
        |
        v
PROGRAMMING -> Luna/high
        |
        +-- implementation completes -> candidate -> review/admission
        |
        +-- unresolved design decision
                |
                v
            Continuation
                |
                v
        Sol engineering judgment
                |
                v
        resume PROGRAMMING
```

This boundary is important to making aggressive low-cost worker routing safe.

## Review asymmetry

Implementation and review should not be assumed to need the same execution profile.

A useful initial policy is:

```text
Sol/high   -> plan and settle intent
Luna/high  -> produce implementation candidate
Sol/high or xhigh -> review/admission
Luna/high  -> bounded repair when the repair instruction is concrete
```

This is consistent with the Candidate-Admission Model. Candidate quality is established by deterministic validation, executable specifications, review, and admission rather than by requiring the strongest model to produce every candidate.

## Phase 5 review finding

Phase 5 currently contains an older concrete operating target:

- Coding: GPT-6 Sol medium / high / xhigh.
- Review: GPT-6 Sol high / xhigh.

This does not conflict with the architecture, because concrete model mappings are explicitly non-semantic, but the documented default is now stale relative to the PROGRAMMING / ENGINEERING routing direction.

Phase 5 should therefore update its default/runtime example so that Luna/high is the primary PROGRAMMING candidate and Sol/high is the primary ENGINEERING candidate, while retaining Sol high/xhigh for review according to semantic review demand.

The ReasoningClass vocabulary itself should not be changed merely to reflect current Luna/Sol economics.

## Measurement

The routing policy should eventually be evaluated using execution/admission evidence rather than intuition alone.

Useful dimensions include:

- ReasoningClass / implementation class;
- resolved model and reasoning effort;
- first-pass admission rate;
- repair/retry count;
- Continuation caused by unresolved engineering decisions;
- review findings by severity/type;
- execution cost and elapsed time.

A particularly useful signal is the admission rate of Luna/high PROGRAMMING candidates under Sol-based review. Repeated PROGRAMMING -> engineering Continuation is also a signal that Plan may not have settled enough design responsibility before implementation.

These metrics can later support routing-policy refinement without coupling model economics to Workflow semantics.

## Final assessment

sm-workflow can handle the proposed Luna/Sol strategy with its existing logical reasoning architecture.

Required action is primarily policy/document alignment and Phase 5 implementation:

1. preserve ReasoningClass as the semantic contract;
2. preserve PROGRAMMING / ENGINEERING as responsibility classification;
3. make Luna/high the initial PROGRAMMING routing candidate;
4. use Sol/high for engineering judgment and normal strong reasoning;
5. use Sol/xhigh selectively for critical/broad review and judgment;
6. treat Luna/xhigh as an evidence-driven optimization candidate rather than a mandatory escalation rung;
7. record requested semantic class and resolved execution profile in evidence so the policy can be measured and changed independently.
