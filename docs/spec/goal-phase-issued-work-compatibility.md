# Retained GoalPhase issued-work compatibility

The operational GoalPhase work reader accepts the retained
`sm-workflow.goal-phase-native-work-request.v5` schema within a finite
compatibility boundary. This reads an existing issued request without changing
its Session, native revision, semantic kind or historical record.

The v5 payload requires this exact typed metadata:

```json
{
  "operatingProfile": {
    "selection": {
      "identity": "standard",
      "revision": "1",
      "source": "default"
    },
    "reasoningClass": "CodingEngineeringDeep"
  }
}
```

These are the only supported selection identity, revision and source. The finite
accepted reasoning classes are `CodingEngineeringDeep` and `ReviewCritical`.
Both remain unchanged when read, including historical reviews that already
retain `CodingEngineeringDeep`. Missing or null objects or fields, extra fields, and unknown values are
rejected. The metadata records the historical request's profile; it does not
select an execution profile, resolve or launch a model, assign a role, grant
permission, or establish acceptance of a semantic result.

A decoded v5 Request retains the metadata in its optional `operatingProfile`
field. Encoding that Request emits v5 again and preserves the complete payload,
including decision context, diagnostic history, selected Plan, base, candidate,
completed prefix, generation, repair progress and references. The v5 reader
requires exact canonical JSON reprojection: encoding the decoded Request must
equal the received root and payload. Unknown root, payload or nested data cannot
be discarded during this read.

The existing v2, v3 and v4 boundaries remain unchanged and require
`operatingProfile` to be absent. v2 has no purpose-repair or diagnostic-history
marker; v3 requires the purpose-repair marker without diagnostic history; v4
requires diagnostic history. Their existing normalization and semantic
validation remain in place. A v5 marker without a valid profile, or a profile
under an older marker, is rejected.

New Request constructors default the optional metadata to None. Such requests
omit `operatingProfile` and retain the existing version selection: v4 for
diagnostic history, otherwise v3 for purpose repair, otherwise v2. This repair
does not capture profiles for new work or change issue routing, host
configuration, execution requirements, mappings or fallback behavior.

The normal CNCF WorkflowWorkOrderJsonV1 envelope continues to own the handle,
revision, operations, completion and evidence contracts, requirements and
disposition. Its owning application payload codec retains v5 metadata through
decode, replay projection and re-encoding. No replacement WorkOrder codec,
service operation, migration, automatic reissue, downgrade or store write is
introduced. Existing stored rows remain immutable. Broader profile routing and
M2A work remain outside this compatibility repair.

The `retained-review-description-path` regression normally stores synthetic
WorkOrders through `IssuedWorkOrderPersistence.LocalJson.putIfAbsentC`, then uses
fresh LocalJson readers to load both retained classes through
`WorkflowWorkOrderJsonV1` and the owning application codec. It compares the full
interaction and Request, native replay and business output projections, and
saved bytes before and after passive reads. The normal retained description path
uses `source.storage.bindIssuedC(...).loadC`, whose managed DataStore adapter
shares this WorkOrder wire decoder and application codec. LocalJson is a separate
storage adapter: this isolated qualification does not establish managed storage,
Session lifecycle or complete consumer description success.

The observed consumer evidence is a public Record-shaped Review response with
`ReviewCritical` and a Repair response with `CodingEngineeringDeep`. Neither is
a raw persistence envelope. No actual consumer store was read or exported for
this regression, and its synthetic fixtures do not claim to reproduce an
unavailable failing saved envelope. Actual consumer description and recovery
remain with the consumer owner. This qualification does not extend separate
public Record roundtrip or legacy release recovery contracts.
