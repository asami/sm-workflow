# Resolved Failure Model as an AI Contract

A failure boundary must be resolved before implementation work reaches AI.

Recent defensive-code behavior illustrates the problem: hashes and repeated checks can be locally defensible yet make a simple single-user tool effectively unusable. The implementation agent must therefore not decide its own robustness scope.

sm-workflow will carry ResolvedFailureModel in the implementation context. In CAR development it is derived from CML declarations and CNCF resolution semantics. AI receives the final effective model rather than Component/Service/Operation inheritance that it would have to interpret itself.

OUT_OF_SCOPE failures are active constraints. AI must not add locks, hashes, retries, rollback, duplicate validation, or similar machinery solely for them.

When AI identifies a plausible missing failure, it should surface a FailureModelGap. That gap can be considered upstream, including through the Candidate-Admission model where appropriate. Only after the model changes and is resolved again should implementation expand its robustness boundary.
