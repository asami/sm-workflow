# SplitPhase native Human answer admission

This is the original PHASE-2 Human-answer prerequisite, not a repair frame or
an independently selected skill processing stage. It consumes the genuine
privately constructed native Human-direction plan and its complete Decision.

## Closed answer and passive wire

`SplitPhaseHumanDirectionAnswer` has exactly three alternatives:

- `ProvideResolution(reference)` requires one canonical identity/revision.
- `Defer` has no resolution reference.
- `Cancel` has no resolution reference.

The payload codec retains the generated CML `choice.value` and optional
`resolution.identity/revision` shape inside a versioned payload envelope.
Unknown choices, extra nested fields, authority/receipt fields, null values,
noncanonical references and alternative/field mismatches reject. Decoding
constructs passive data only, never independent Human evidence.

## Native preparation

The owning plan must contain the exact issued Decision. Its current native
instance must equal the plan's adjacent HumanPending postimage in full. The
typed submitted result must satisfy the CNCF Decision result contract, not the
semantic WorkOrder contract. Preparation appends precisely HumanAnswered,
removes the current suspension and retains the complete native history prefix.
This pure calculation neither commits the append nor creates a Terminal.

## Independent evidence

Admission occurs only in the actual generated `submitSplitHumanDirection`
call, for the original authenticated participant and owning Component. A host
verifier independently resolves the submitted result reference as a dedicated
identity/revision evidence reference. There is no client receipt decoder and
no built-in authentication-as-consent verifier.

The independently issued receipt binds the owning Component instance, entire
issued question, entire submitted result and original relay participant. Its
issuer, purpose, reference and validity must match. Validity uses the actual
call's execution clock, with `occurredAt <= now < validUntil`; assembly clocks
and ambient system clocks do not decide admission. Indeterminate verification
does not admit an answer or create an automatic retry.

Only this admission boundary constructs its call-bound verified value. Neither
passive result decoding nor pure postimage preparation constructs it. That
value supplies no managed storage/command grant; the actual completion still
requires the Subsystem's canonical managed Decision-completion operation.

## Remaining mandatory integration

The managed completion must atomically retain the canonical answer and native
postimage, verify committed readback and handle retained same-result observation
without replay. Public advance must route retained Human work before obsolete
scope work, and fresh scope publication must reach the actual Human issuer.
`ProvideResolution` must independently resolve and validate its planning data
against the complete frontier; it is not approval of authority expansion or
application. `Defer` and `Cancel` must reach their real typed terminal outcomes.
Those public/managed handlers, production assembly and full Phase acceptance
remain mandatory; this prerequisite does not stand in for them.

Executable specifications: `SplitPhaseHumanDirectionAnswerContractSpec` and
the genuine Mechanical/PartitionCompletion root scenarios in
`SplitPhaseManagedPlanningCheckpointSpec`. New authoring is unverified until a
fresh registered runner and independent read-only review complete.
