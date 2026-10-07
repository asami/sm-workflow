# Local LLM OFP adaptation as an operational research theme

## Background

Local LLM utilization may become one of the keys to practical AI-assisted development. The objective is not merely to compare local models with frontier hosted models, nor to ask whether a model can emit syntactically valid Scala. The important question for Textus development is how far a pretrained local model can practice Object-Functional Programming (OFP), and how far its useful range can be expanded by external correction mechanisms.

Current coding agents often show a procedural bias: they optimize for completing the immediate program with explicit steps, branches, mutable state, checks, rollback machinery, and local control flow. This style maps naturally to shell/Python-like programming, but can conflict with object-oriented responsibility allocation and functional abstractions such as algebra/interpreter separation, higher-kinded types, Free Monad based DSLs, and domain-oriented modeling.

## Evaluation axis

Treat raw pretrained capability as the baseline rather than teaching OFP in the initial prompt.

Suggested adaptation levels:

1. task only: measure pretrained OFP capability;
2. repository context: measure ability to infer existing abstractions and idioms;
3. explicit OFP / project directives: measure rule-based correction;
4. good examples and counterexamples: measure in-context adaptation;
5. deterministic feedback such as compile, tests, and CAR lint: measure repair capability;
6. stronger reviewer model: measure externally supervised correction.

Evaluation must go beyond compile/test success. Candidate OFP metrics include:

- reuse of existing abstractions and DSLs;
- responsibility placement and domain modeling;
- preservation of Algebra / Program / Interpreter boundaries;
- appropriate use of type-level constraints and higher-kinded abstractions;
- unnecessary mutable state or procedural orchestration;
- oversized procedural operations;
- architectural or DSL deviation;
- number of repair cycles;
- correct decline/escalation rate;
- acceptance by stronger-model review;
- time, token, and monetary cost.

## Operational hypothesis

The goal is not to force every task through a local model. A local model that correctly declines a task beyond its reliable OFP capability is useful.

The practical execution model should therefore be:

local model -> implementation -> compile/test/CAR lint -> local repair -> accept or decline -> stronger model escalation.

CAR lint can serve not only as a quality gate but also as an OFP correction mechanism that deterministically narrows the model's available solution space.

The key quantity is the adaptation curve: how much usable OFP coverage is gained by repository context, directives, examples, deterministic feedback, and review, and at what point further correction costs more than escalation to a stronger model.

## Relation to sm-workflow

sm-workflow should eventually use these measurements for provider/model routing. Task class, reasoning level, OFP risk, observed model capability, decline behavior, and correction cost can all become routing inputs.

This connects directly with local-first reasoning and provider escalation: local execution is valuable when it is economical and reliable, while difficult architectural work can be escalated without treating local-model failure as an exceptional condition.

## Research direction

Build reproducible OFP tasks from real Textus/CNCF/Scala development history, including cases involving:

- ordinary Scala;
- monads and type classes;
- higher-kinded types;
- Free Monad / Algebra / Interpreter structures;
- CNCF and Textus-specific DSLs;
- known procedural-bias failures such as unnecessary integrity machinery, low-quality locking, and bypassing existing abstractions.

The practical target is to discover how much hosted-model usage can safely be replaced by local execution while preserving OFP and architectural quality. This may be a major determinant of the cost and scalability of continuous AI-assisted development.
