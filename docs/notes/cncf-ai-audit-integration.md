# CNCF AI Audit Integration

sm-workflow must use CNCF Phase 96 AI Audit for AI-backed JudgmentAction and Continuation execution rather than introduce a parallel prompt/response log.

For each applicable AI execution, preserve correlation to Workflow/GoalPhase/Step/Action/Continuation and the CNCF AI Interaction identity. Later review, correction, retry/escalation, Candidate-Admission evidence/result and downstream workflow outcome should be attachable as evaluation evidence.

This supports both runtime audit and engineering feedback: detecting AI deviation, tuning prompts/context/guards, and identifying work that can be converted from AI execution into deterministic Workflow/rules/programs.

Raw prompt/response storage follows CNCF classification/redaction/reference, authorization and retention policy.
