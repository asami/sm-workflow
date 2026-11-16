# Design rationale journal

This directory keeps the considerations behind `docs/design` and `docs/notes`:
the problem, alternatives considered, reasons for a choice, and its tradeoffs.
It does not define executable contracts or establish that a proposal shipped.
Follow the linked design/specification and owning Phase for those facts.
Journal documents are written in English and are created only when the user
requests them. Routine AI work must not automatically create an entry.

| Consideration | Related rationale |
| --- | --- |
| Human decisions, continuation, closing and validation cost | [Workflow choices](workflow-design-choices.md) |
| Dependency sources, installation, configuration and public input | [Runtime choices](runtime-design-choices.md) |
| Independent services and cross-project responsibility | [Integration choices](integration-design-choices.md) |

## What belongs elsewhere

- Source edits, implementation sequences and commit history belong in Git.
- Commands, test/review output and execution observations belong to their
  operation records and private retained evidence, not Markdown copies here.
- Completion/ending decisions and outstanding work belong in
  [Phase records](../phase/README.md), the
  [maintenance register](../phase/maintenance-backlog.md) and
  [retained ending dispositions](../phase/maintenance-dispositions.md).
- A statement already explained by a design/note/spec needs no second journal
  entry unless it adds a distinct reason or rejected alternative.

The former date-by-date documents are available in Git history before this
consolidation, at revision `9debf6484e3e16e6739b83688a30a814772c7b29`.
Historical references may select that revision; live guidance uses the documents
above. The original working files are archived on the owning machine under
`~/backup/work.d/2026/11/16/sm-workflow-journal-cleanup/a-b9366459-1c14-46fd-bbad-d36e97e19c86/`.
Do not restore daily files merely to duplicate source history.

This repository's explicit documentation specialization is recorded in
[shared directive exceptions](../rules/shared-directive-exceptions.md).
It changes neither the product's structured Journal operations nor any existing
Session, accepted result, Plan, history or repair allowance.
