# Provider Routing Basic Design

- Date: 2026-10-07
- Status: Design decision
- Scope: sm-workflow
- Related: ExecutionRequirement, Provider Selection, local-first Judgment/Implementation, Review, corpus, experiment

## Decision

The local-LLM discussion is generalized into the basic design of sm-workflow Provider Routing rather than retained as a special local-provider optimization.

Provider Routing has three primary principles:

1. Implementation and Review are local-first when the resolved ExecutionRequirement can be satisfied locally.
2. Implementation and Review have a typed Decline Protocol; DECLINED work is gracefully re-executed by the next stronger/capable admitted provider.
3. Provider execution history is recorded and used to improve versioned routing policy through measured evidence, corpus/experiment, and Human-in-the-Loop admission.

Bounded Judgment follows the same local-first principle where appropriate.

## Why local-first

The objective is to use inexpensive/local capacity aggressively without pretending that every task is within local-model capability. STANDARD work is an important target, not only hygiene/ROUTINE work. A strong Workflow envelope can reduce effective difficulty by constraining scope, supplying typed context/evidence, and validating results deterministically.

Local-first therefore means "try the least expensive admitted capable provider first when policy says the risk is acceptable", not "force local completion".

## Decline as a normal protocol outcome

A provider must be allowed to say that it cannot reliably perform assigned semantic work.

~~~text
SemanticWorkOutcome
  COMPLETED
  DECLINED
  BLOCKED
~~~

DECLINED is not a failed implementation/review. It says provider capability/context is insufficient and routing should select the next admitted stronger/capable provider. BLOCKED says stronger-model retry is not the right response because an upstream requirement/design/input/authority/human decision is needed.

This distinction allows sm-workflow to route STANDARD work optimistically to local providers without requiring perfect pre-routing classification.

A decline/escalation chain is bounded and preserves work identity, Candidate/evidence context, requirement, decline reason, and attempt history.

## Implementation routing

A representative path is:

~~~text
Implementation WorkOrder
  -> local provider
       COMPLETED -> deterministic validation -> Review
       DECLINED  -> stronger provider -> execute same semantic work
       BLOCKED   -> typed decision/error handoff
~~~

A local COMPLETED claim has no acceptance authority. Existing deterministic validation, Review, Admission, and Candidate freshness rules remain authoritative.

## Review routing and staged review

Review is also local-first, while preserving REQUIRED independence where the review contract demands it. A local implementation and local review can both be used when they run in suitably independent execution contexts.

Review can use staged/two-level operation:

~~~text
Candidate
  -> deterministic validation
  -> independent local Review
       FINDINGS -> REVIEW_FIX
       DECLINED / insufficient confidence / deep issue -> stronger Review
       PASS -> routing/review policy decides whether stronger Review is required
  -> stronger Review when required
  -> Admission
~~~

During calibration, a policy may send a sample of local PASS results to stronger review. The important quality metric is disagreement/false-negative behavior: cases where local Review passes but stronger Review finds material issues. Once evidence is sufficient, selected work classes may allow local-only final review; other classes may permanently require stronger review.

Local Review is not merely a weak substitute. It is a cheap independent quality layer that can run broadly, while stronger review is spent where semantic depth or measured risk justifies it.

## Routing evidence and KPIs

Each provider attempt should record enough evidence to answer:

- What semantic work and WorkClassification was routed?
- What abstract reasoning/independence requirement applied?
- Which provider/profile attempted it?
- Did it COMPLETE, DECLINE, or BLOCK?
- If completed, did deterministic validation pass?
- What did Review conclude?
- If a stronger Review also ran, did it disagree materially?
- How many Fix/retry/escalation steps followed?
- What time/cost/resource evidence was observed?

Useful aggregate KPIs include local completion rate, local validated-success rate, decline rate, escalation rate, post-local validation failure, Fix cycles, and local-vs-strong-review disagreement/false-negative rate.

The objective is not maximum local utilization. The objective is the best total cost/latency/quality/convergence tradeoff.

## Policy improvement loop

Routing starts with simple versioned rules, for example ROUTINE local-first, STANDARD local-first candidate, and DEEP/CRITICAL stronger-provider defaults. These defaults should evolve from evidence.

~~~text
ExecutionEvidence
  -> routing metrics
  -> corpus construction
  -> offline/online experiment
  -> policy revision proposal
  -> human/admission
  -> new versioned routing policy
~~~

Runtime MUST NOT autonomously rewrite its routing policy from recent outcomes. Evidence informs a proposed policy revision; the revision is reviewed/admitted like other operational policy.

Reproducible corpus environments are particularly useful: a new local model/provider can be tested against historical Implementation/Review/Judgment work before it is admitted into production routing.

## Relationship to Fix reasoning routing

Compile/test diagnostics provide a strong initial use case. Deterministic classification handles obvious cases; bounded local Judgment can classify unresolved evidence and request an abstract ReasoningLevel; Provider Routing then selects local or stronger Fix execution. The provider itself can still DECLINE if the pre-routing judgment underestimated difficulty.

Thus pre-routing classification and provider self-decline are complementary:

~~~text
failure evidence
  -> classify expected difficulty
  -> choose initial provider
  -> provider COMPLETED / DECLINED / BLOCKED
  -> validate / escalate / handoff
  -> record evidence
  -> improve future routing policy
~~~

This closes the feedback loop without turning sm-workflow into a self-modifying ML router.
