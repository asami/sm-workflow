# Workflow mechanical receipt capacity

## R1 — Application default and explicit selection

The normal sm-workflow configured factory declares CNCF's optional
`workflow.mechanical-receipt.max-bytes` Component parameter. It chooses
**16,777,216 bytes (16 MiB)** only when that parameter is absent. A present valid
value overrides this application default; an invalid value fails configuration.
CNCF's independent default remains **1,048,576 bytes (1 MiB)**. The accepted
inclusive range is **1–67,108,864 UTF-8 bytes (64 MiB)**, expressed as an integer
numeric value or exact ASCII decimal string. Fractions, overflow, zero,
negative/above-ceiling values, whitespace, signs, exponent strings, Boolean,
null and structured values are refused.

The resulting `WorkflowInstancePersistence.Configuration` is the same common
logical configuration passed to GoalPhase, SplitPhase and RepositorySync and
used when reopening. The seven existing persistence declarations remain
required. Receipt capacity is an optional eighth declaration, not a command
or Operation parameter.

Normal resolution stores `Some(limits.maxBytes)` in Configuration's standard
`mechanicalReceiptMaxBytes: Option[Int]` field, including the application's
16 MiB absence selection. Configuration validates the finite integer range;
`mechanicalReceiptLimitsC` resolves it to validated immutable limits for adapter
binding. A programmatic `None` instead resolves CNCF's independent 1 MiB default.
The borrowed adapter captures the resolved value once before use.

## R2 — Existing normal configuration resolution

For an explicit 2 MiB policy, add the following entry alongside the existing
host/storage settings in the project's normal `.textus/config.json`:

```json
{
  "workflow.mechanical-receipt.max-bytes": 2097152
}
```

This is an example property addition, not a complete runnable host
configuration. Omit the property to use sm-workflow's 16 MiB default. A nested
`workflow` / `mechanical-receipt` / `max-bytes` JSON representation is also
consumed by the normal Component resolver.

Component precedence remains, from highest to lowest: test overlay, runtime
configuration, subsystem instance, assembly default, packaged default. Host
home/project values are already resolved upstream. This policy consumes that
normal immutable snapshot and introduces no separate project customization,
environment/system-property reread, path search or override registry.
Independent factory resolutions do not share or mutate selected policy.

## R3 — Complete receipt capacity and retained rows

The selected byte limit applies to the actual complete mechanical JSON
envelope: schema/owner/reference/operation fields, typed payload and both full
current and next histories, including UTF-8 encoding, escaping and syntax.
The exact limit is inclusive. Current history contributes again within next
history, so aggregate capacity must account for both copies and all captures
retained in the payload/history. Sixteen MiB is a finite policy, not a promise
for arbitrary history or multiple maximum-sized captures.

Capture behavior and capture bounds remain unchanged. Physical storage text
prefixes and database framing are excluded; the byte limit is not a heap quota
or aggregate datastore quota. Encoding rejects capacity excess before native
root/checkpoint preparation. The existing borrowed atomic transaction remains
unchanged; no truncation, compression or backfill is performed.

The current selected policy also controls retained decoding and physical
reopen. A lower setting or an older CNCF 1 MiB reader rejects a larger retained
row. Stored metadata cannot automatically relax policy. Select sufficient
capacity through normal configuration to read such rows. The receipt schema
and owner/type/history checks remain unchanged.

Passive Context and typed Start records continue using their existing ordinary
generic aggregate derivations. No dedicated downstream Configuration encoder or
decoder is required. Their Configuration data contains the original five required
fields plus `mechanicalReceiptMaxBytes: Option[Int]`: absent or JSON-null optional
bytes decode as `None`, and explicit integer bytes roundtrip as `Some(bytes)`.
Old five-field JSON therefore retains CNCF's 1 MiB meaning without rewriting
rows. Generic decoding rejects data it cannot interpret as an optional integer;
it is not a custom policy certificate. Invalid operational integer ranges fail
Configuration validation. These passive JSON semantics do not relax the normal
Component property decoder, which still rejects explicitly null, malformed and
out-of-range configured properties.

Preparation uses the independently selected and validated current Configuration
instead of a historical passive snapshot, so a current normal 16 MiB selection
replaces historical `None` in the prepared record. This preserves source
compatibility for containing generic derivations and the existing five-argument
constructor and companion calls. Consumers must rebuild current SNAPSHOT sources;
full case-class extractor/copy binary compatibility and old-binary upgrades are
not promised.

Executable evidence is specified by `GoalPhaseRuntimeParametersSpec` E1:
default 16 MiB, explicit string/numeric 2 MiB override, independent default
resolution and invalid present values through the existing configured factory.
Its existing required-setting example preserves all seven omission failures.
`SplitPhaseExplicitPartitionContextSpec` and
`SplitPhaseTypedStartPreparationSpec` E1 specify full-record generic roundtrips
for `None` and explicit 16 MiB, old five-field and optional-null decoding,
malformed integer data, required original fields, operational range refusal and
preparation with an independently selected current policy.
CNCF's `WorkflowMechanicalReceiptParametersSpec` E1 and
`WorkflowMechanicalTransitionSpec` E2/E3 specify finite policy validation,
exact escaped/multibyte envelope boundaries, default refusal, selected-policy
acceptance, atomic SQLite refusal and actual physical reopen.
