# Project initialization Operation

Date: 2026-10-24 (Asia/Tokyo)
Status: adopted design; implementation and installed acceptance pending
Delivery: [INIT-1–4 plan](../notes/project-initialization-delivery-plan.md)

## Purpose

Provide a typed `initializeProject` Operation for adopting sm-workflow in an
existing repository. The name is proposed, not an installed API. The installed
CLI must reach it without project sm-workflow configuration, a runtime-root,
database, Session or Workflow binding already existing. This initializes
sm-workflow use, not the repository/product: Cozy retains project generation.

Installed CLI/component availability, filesystem access and required platform
permissions remain real prerequisites. Missing installation is reported explicitly;
initialization does not download arbitrary artifacts, install dependencies or
silently substitute a source launcher. The requested Workflow's defaults must not
require settings for unrelated Workflow services merely to invoke initialization.

## Minimal bootstrap entry

Expose a fixed installed CLI route to a minimal bootstrap assembly containing
this Operation and its managed configuration/storage facilities. It must load
before the normal configured Workflow assembly. Do not implement initialization
as an Operation that first requires the complete configuration it will generate.
The CLI transports the typed request; shared Operation logic owns initialization.
Avoid a second business implementation in Python or a generic shell executor.
Determine the exact public service/CLI spelling during INIT-1 and document it;
examples must not imply a current command exists before delivery.

Use existing CNCF UoW/File/Resource/DB facilities under actual platform permissions.
The minimal context admits only the requested installation work, not Git/SBT
effects, fabricated Human evidence, Review acceptance or Phase execution.

## Request and result

Input contains the actual repository, selected supported Workflow capability and
optional existing installation locator/overrides. Resolve conventional defaults
for ordinary users; do not require them to supply low-level process-plan JSON,
DB schema, review byte limits, environment allowlists or response directories.
Use the existing project-local work-area convention under `.codex-workflow` for
new runtime resources; returned paths are authoritative. Do not encode `dev2026`,
user names or a Phase number into shared defaults. Existing configured locations
remain usable; initialization is project-scoped, not a new installation per Phase.

Return an attributable structured result with disposition (created, reused,
needs input, incomplete or failed), selected installation/runtime location,
actual created/reused resources, supported caller entry and concrete unresolved
items. These are proposed semantics, not claims of existing DTO enum names.
Readiness means configuration is usable for the selected entry, not test success,
accepted Phase completion or automatically granted execution authority.

## Deterministic preparation

Generate configuration using current installed typed contracts and shared default
definitions. Reuse the production constructors/validation instead of maintaining
an unrelated template schema or asking AI to discover bounds by trial and error.
Prepare required owned directories, normal DB initialization and selected service
bindings, including input/output retention through existing facilities.

Keep project-level infrastructure separate from the semantic Plan and per-run
bindings. The user/AI selects objectives, Steps and meaningful test choices;
runtime converts the adopted choices into coherent typed execution declarations
through existing preparation. Derive Git/SBT environment and operation settings
from their supported contracts, and distinguish logical ownership from actual
Git change selection. Do not copy future owned files into mandatory pathspecs.
Workflow-specific runtime policy is generated only as needed; fixed schema defaults
and directories do not become AI work.

Initialization and Start remain separate. A normal explicit Phase-start request
can use initialization as preparation within its authorized scope, without an
extra general confirmation, then prepare and Start through the usual human-intent
binding. Initialization alone does not Start, Advance, run tests, mutate Git,
commit, push, publish or change an existing Session. Missing genuine semantic
choices or authority are reported precisely, not manufactured.

## Existing resources and partial failure

Inspect the explicitly selected or conventionally located installation first.
Reuse a usable installation with no rewrite or new DB/Session. Keep existing
overrides, credentials, data, history, repair counters and validation references.
If multiple installations are plausible, ask only for the ambiguous selection;
do not silently reuse another repository's runtime.

Malformed/incompatible existing configuration is reported with the affected
setting and proposed correction. Do not reset it to defaults or silently migrate
live data. Use supported explicit repair/adoption where needed. Incomplete fresh
initialization may resume only known unfinished preparation, preserving completed
resources; do not delete retained output, replace a DB or overwrite unknown files.
If partial ownership/effects cannot be established, report the unresolved part.
An existing Session's configuration/captured bindings retain their own contracts;
creating an installation is not proof that Session migration occurred.

Use normal create/update conflict handling and actual resource results, not hash
envelopes, broad filesystem scans or a new reservation/approval framework. Repeated
successful calls reuse resources. Report partial preparation truthfully. Retain
diagnostics through existing outputs/Journal when available; lack of Journal before
bootstrap must not make initialization impossible or trigger AI transcription.

## Skill integration and preservation

`sm-goal-phase` first selects/reuses the installation, initializes if genuinely
absent, then uses normal preparation and Start/resume. The runtime prepares its
owned invocation output directory so the caller need not create one manually;
preserve existing output and use a fresh invocation location as appropriate.
Do not alter already-issued work or create a duplicate Session on reuse.
Other entrypoints, including planned sm-goal-task/sm-fix, reuse the same facility.

Keep ongoing product edits and applicable accepted results intact. No re-Start,
replayed Submit, counter reset, private storage mutation or repeated successful
validation solely for adoption. A running consumer that already initialized
successfully is a reuse/adoption case, not a fresh-install demonstration.
