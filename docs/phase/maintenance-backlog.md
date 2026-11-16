# Maintenance follow-up and retained dispositions

This document records remaining management meaning after journal consolidation.
It starts no work and changes no Session, Plan, accepted result, history or
repair allowance. Existing Phase/checklist dispositions remain authoritative.
Old review observations are not automatically promoted to current defects.

## Open operational observation: response projection

Owner: sm-workflow. A successful native SBT validation was followed by HTTP 409 in
`WorkflowResponseMaterials` while projecting the retained state. The actual
receipt-bearing snapshot remained publicly readable. A later explicitly selected
repair should address the response/result-collection boundary without repeating
successful SBT or editing the retained Session. Public Task closure was not
established by collecting that successful receipt.

## Retained current maintenance

- `HYG-PLANNING-HOST-TERMINOLOGY-01`: the Phase 8/15 planning prose still uses
  `host` for runtime/composition concepts. Keep the existing low-priority,
  naming-only disposition; no product/configuration rename is selected here.
- `HYG-RS-REFRESH-PRIVATE-NAMING-01`: older helper names in
  `RepositorySyncDirectoryListPurposeSpec.scala` retain their separately owned,
  nonblocking naming follow-up. Do not bundle it into workspace repair.

These are the recorded scopes, not a fresh whole-source review or acceptance
of their proposed fixes. Original locations and wording remain in the archived
journal and Git history.

## Existing plans and owners

| Remaining subject | Existing management source |
| --- | --- |
| Runtime-owned initialization and Evidence setup | [Phase 8](phase-8.md), [Phase 15](phase-15.md) |
| Related-project/workspace/artifact integration | [Phase 7](phase-7.md), [Phase 8](phase-8.md) |
| CBD review integration | [Phase 10](phase-10.md); no retrospective reopening of the 6 series |
| Phase 6.11 ending and deferred delivery | [Phase 6.11](phase-6.11.md), [carryovers](../notes/phase-6.11-carryover.md) |
| Existing bounded-controller/source-size maintenance | [Common continuation correction](common-development-continuation-correction.md) |
| Earlier Phase 5/5.1 and 5.2/5.3 dispositions | [Phase 5](phase-5.md), [Phase 5.1](phase-5.1.md), [Phase 5.2 closure](phase-5.2-closure.md), [Phase 5.3 closure](phase-5.3-closure.md) |

The named plans keep their actual planned/accepted/closed status. Listing them
here neither adopts a proposal nor restarts a Phase. Owner-specific CNCF, Cozy,
development-workstation and sm-workflow-dev work stays with its existing owner.

## Historical findings and proposals

The old journals contained naming observations, review summaries, source-size
counts and unadopted proposals from many past increments. Their original IDs,
OPEN/pending/resolved dispositions, ownership and aliases are retained in the
archived originals and at Git revision
`9debf6484e3e16e6739b83688a30a814772c7b29`. They are not reproduced as a new
active backlog, re-reviewed, declared resolved or made into new prerequisites.
Current owning plans retain any already adopted work.

In particular, the original stale-Submit labels
`DEV-CANDIDATE-CONTINUATION-01-STALE-SUBMIT-500` and
`DEV-CANDIDATE-DUPLICATE-SUBMIT-001` name the same observation;
`HYG-P611-A-PRIVATE-NAMING-01` was consolidated into
`HYG-P611-CHECKPOINT-LEGACY-NAMING`. This cleanup creates no duplicate task.

Archive location on the owning machine:
`~/backup/work.d/2026/11/16/sm-workflow-journal-cleanup/a-b9366459-1c14-46fd-bbad-d36e97e19c86/`.
`archive-index.json` identifies the moved originals and the seven files already
absent at the start, preserved separately from their last committed version.
No execution payload or source-update history is copied into this register.
