# Resolved Failure Model in AI Implementation Context

sm-workflow must pass a fully resolved Failure Model whenever AI is instructed to implement a modeled target.

The AI is a consumer of the Failure Model, not an implicit author of it.

Implementation context should include:

```
ImplementationContext
  target
  specification
  resolvedFailureModel
  reasoningLevel
  presentation
```

For CAR development, the resolved model originates from CML/CNCF metadata.

AI instruction semantics:

- implement within the supplied ResolvedFailureModel;
- do not add defensive mechanisms for OUT_OF_SCOPE failures;
- do not silently extend the Failure Model;
- if a materially relevant missing failure is discovered, return/surface a FailureModelGap for upstream modeling/admission rather than implementing speculative robustness.

This keeps implementation complexity bounded by the modeled execution contract.
