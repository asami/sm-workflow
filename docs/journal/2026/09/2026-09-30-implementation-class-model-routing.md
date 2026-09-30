# Implementation Class and Model Routing

Date: 2026-09-30
Status: architectural direction / experiment candidate

## Context

An sm-workflow Phase has distinct reasoning points: plan, implementation, focused review, and full review. GPT-6 Luna is attractive as a low-cost programming worker, while GPT-6.1 Sol is better suited when engineering judgment remains necessary.

The workflow should not hard-code current model product names into the semantic definition of a Phase. Plan should classify the kind of implementation work required; a routing policy maps that class to an available model.

## Initial implementation classes

Start deliberately small with two classes.

### PROGRAMMING

The Plan has already made the relevant design decisions. The implementation worker is expected to translate the Plan, contracts, checklist, and executable specification into code using established project patterns.

Initial routing candidate:
- Luna for implementation.
- Reasoning level may be selected separately according to task size/complexity.

### ENGINEERING

Implementation still requires design or engineering judgment that was not settled by Plan. Examples include selecting an implementation strategy, resolving architectural trade-offs, or deciding how to change a boundary shared by multiple components.

Initial routing candidate:
- GPT-6.1 Sol for implementation.
- Start with medium reasoning and raise it only when the task requires it.

## Primary classification question

The initial classifier should avoid a large scoring/rule system. Plan should primarily answer:

> Does the implementation worker need to make a design decision that the Plan has not already made?

No -> PROGRAMMING.
Yes -> ENGINEERING.

This classification is about required responsibility, not model prestige or a generic difficulty score.

## Continuation / escalation

A PROGRAMMING worker must not silently invent a design decision merely to finish the task.

If implementation discovers an unresolved design decision, it should yield through the existing Continuation mechanism with the unresolved question and relevant context. Engineering reasoning can then be performed by Sol / Plan-side processing, after which the implementation may resume.

Conceptual flow:

Plan
-> classify implementation
   -> PROGRAMMING -> Luna
        -> unresolved design decision?
             -> Continuation / engineering decision
             -> resume implementation
   -> ENGINEERING -> Sol

This makes aggressive use of a low-cost programming worker safer without requiring the worker itself to be architecturally strong.

## Review points

Initial routing hypothesis for the existing Phase reasoning points:

- plan: Sol medium
- implementation / PROGRAMMING: Luna
- implementation / ENGINEERING: Sol medium, raise when needed
- focused review: Sol medium
- full review: Sol high when broad system-level reasoning is required

These are routing-policy defaults, not semantic requirements of the workflow.

Focused review checks the changed scope and its conformance to Plan/checklist/executable specification. Full review can examine broader repository/component consistency, unintended design changes, Model-up results, dependencies, and other system-level effects.

## Relation to Candidate-Admission

Implementation produces a candidate. Using Luna for PROGRAMMING does not weaken the Admission model: candidate quality is established by deterministic checks, executable specification, focused/full review, and Admission criteria rather than by assuming that the implementation model itself is sufficient.

This supports a broader principle:

> Do not obtain safety only by using the strongest implementation model. Use workflow structure, review, and Admission so that an appropriately inexpensive worker can safely produce candidates.

## Planning quality signal

The classification also exposes Plan quality. If work expected to be PROGRAMMING repeatedly yields because major engineering decisions remain unresolved, the Plan may not be sufficiently concrete.

Conversely, a well-specified Phase should allow a larger share of implementation to be routed to PROGRAMMING workers.

This can eventually become an operational metric, but no automatic quality judgment or threshold is defined yet.

## Model independence

PROGRAMMING and ENGINEERING are capability/responsibility classes. Luna and Sol are current routing candidates, not permanent workflow vocabulary. A future local model, different commercial model, or deterministic generator can satisfy PROGRAMMING without changing the Phase definition.

This keeps model economics and model generations in routing policy while keeping workflow semantics stable.
