# Phase 77 - Source Boundary Reader Fixture

status=planned

## Scope

| ID | Outcome | Status |
| --- | --- | --- |
| work-1 | First item | planned |
| work-2 | Second item | planned |
| work-3 | Third item | planned |

## Split boundary contract

```split-phase-source-boundary.v1
{
  "schemaVersion": "sm-workflow.split-phase-source-boundary.v1",
  "sourcePhaseIdentity": "77",
  "sourceAuthorityReference": "source-authority-1",
  "items": [
    {"identity": "work-1", "closureCriteria": ["close-work-1"], "prerequisiteItems": [], "authorityReference": "source-authority-1"},
    {"identity": "work-2", "closureCriteria": ["close-work-2"], "prerequisiteItems": ["work-1"], "authorityReference": "source-authority-1"},
    {"identity": "work-3", "closureCriteria": ["close-work-3"], "prerequisiteItems": ["work-2"], "authorityReference": "source-authority-1"}
  ],
  "authorizedHandoffReferences": ["authority-edge-work-1", "authority-edge-work-2"]
}
```
