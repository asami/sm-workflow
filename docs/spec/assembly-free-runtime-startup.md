# Assembly-free installed runtime startup

Status: adopted target contract; residual source cleanup and runtime delivery pending.
Adopted: 2026-10-27.
Plan: [startup correction](../phase/assembly-free-startup-correction.md).
Handoff: [consumer and producer direction](../journal/2026/10/2026-10-27-assembly-free-startup-handoff.md).

## Contract

Using installed sm-workflow does not require the consumer to create
`.textus/assembly.yaml` or pass `textus.assembly.descriptor`. RuntimeRoot selects
the working directory, not a requirement for a CAR/SAR layout or a fixed config
filename. CNCF owns normal configuration discovery, typed resolution and security.
Installed sm-workflow supplies its composition and integrates the normal setup
route. A genuinely missing setting must be diagnosed as such.

An intentional assembly override remains valid; do not remove that capability or
disable its validation. The default launcher must not manufacture that selection.
A successful temporary assembly override is recovery evidence, not the default
installation contract.

## Residual inventory and correction owners

| Surface inspected on 2026-10-27 | Finding | Owner/action |
| --- | --- | --- |
| development-workstation common deployed native-goal-command.py execute | Fixed config/assembly existence loop already absent | Preserve this behavior and its no-assembly regression coverage |
| sm-workflow scripts/native-goal-command.py execute, observed line 993 | Still requires config.json and assembly.yaml | Reconcile the repository source with deployed behavior while preserving other changes |
| sm-workflow-dev scripts/native-goal-command.py execute, observed line 1022 | Same remaining fixed-file loop | Have its owner adopt the focused correction; do not overwrite the active checkout |
| Native client installation and normal composition recipes | Older fixed-file/assembly examples imply a prerequisite | Mark optional override examples and correct current setup guidance |
| CNCF preflight and final security wiring | Fixed-user admission depends on descriptor localSubject | Deliver the CNCF correction before claiming ordinary assembly-free startup |

Line numbers are investigation locators, not frozen file inventories. The source
copies are a regression risk; they are not established as the helper used by the
successful CBD invocation. Capture actual selected executable/artifact identity
during qualification. Do not infer installed behavior solely from current source.

## Setup and adoption

Use the CNCF-supported fixed-user/profile configuration path and installed runtime
composition. Do not create a second resolver, dummy assembly, blanket privilege
fallback or a replacement Session. User identity and authorization are distinct;
setup must preserve the actual selected roles, capabilities and security level.

Qualification starts in an isolated runtime fixture without a local assembly and
without a descriptor override, using the actual installed command. It must prove
the ordinary Operation route and expected missing/invalid identity diagnostics.
Also verify an intentionally selected assembly and multi-user separation.

Only then adopt through the owning Session's supported route. Preserve stable
subsystem name/identity, user, storage and grants as well as Session, Plan, accepted
results, history and repair counts. Do not silently adopt a new default subsystem
or storage target. Retain earlier failures as failures and successful evidence at
its actual scope. No re-Start, accepted Submit replay, counter reset or private
state edit is permitted as a configuration workaround.

The current CBD assembly selection remains temporary working configuration.
Do not remove it before replacement qualification or make this correction a new
gate on its ongoing Stage 12.3 implementation.

