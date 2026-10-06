# 2026-10-06 Replayable AI Evaluation for sm-workflow Tasks

sm-workflow real tasks are a primary source of Textus AI Evaluation cases.

Implementation, Review, Planning, and other task classes can be replayed against multiple Thinking Engines after the original workflow has completed, provided AI Runtime audit and workflow outcome references are available. An online A/B test is not required.

sm-workflow should expose enough task classification and outcome correlation for evaluation, while textus-experiment owns Experiment/Arm/EvaluationRun and textus-corpus owns reusable EvaluationCase corpus organization.

Useful evidence differs by task class. Implementation can use build/test/lint, Review result, later corrections, and human acceptance. Review can use defect detection, false positives, missed findings, importance judgments, and later implementation outcomes. A stronger Judge engine can add semantic comparison but should not replace deterministic or operational evidence.

Aggregated results can later support the existing engine-selection direction: Task Class x Engine capability data can inform routing of work to local LLM, Luna, Sol, or other engines according to demonstrated capability and cost.

This also fits the overnight quality-work use case: historical or safely isolated real tasks can be replayed overnight against local engines and evaluated without affecting the production workflow.

No sm-workflow phase is added yet. First stabilize the cross-component evaluation contract.
