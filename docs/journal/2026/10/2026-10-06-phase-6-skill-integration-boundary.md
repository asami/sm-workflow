# Phase 5 runtime foundation and Phase 6 skill integration

Date: 2026-10-06 (Asia/Tokyo)
Status: user direction adopted into planning; implementation not performed here

## Decision

The user identified that abstract thinking modes can directly change the skill
interface. A task asking for problem structure or constraints may need different
inputs and results from concrete design/implementation. Provider/model mapping
alone cannot account for that semantic difference. Avoid creating provisional
skills in Phase 5 and then having to redesign their interface in Phase 6.

The user selected this sequence:

1. [Phase 2](../../../phase/phase-2.md): complete the first usable runtime scope;
   a normal CLI/control route suffices without requiring new skill integration.
2. [Phase 5](../../../phase/phase-5.md): complete the minimum execution foundation
   and remove unnecessary checks; validate through CLI/control operations and
   Executable Specifications. Do not create/adapt for integration, connect or
   deploy skills in this phase.
3. [Phase 6](../../../phase/phase-6.md): define abstract thinking modes and the
   request/result interface; create/adapt and connect the selected skills with
   the runtime and connected Executable Specifications as one delivery. Begin
   gradual skill-based real use after this connected acceptance.
4. [Phase 7](../../../phase/phase-7.md): continue real use while delivering
   multi-repository synchronization and bounded additions discovered through use.
5. [Phase 8](../../../phase/phase-8.md): reassess and finish necessary carryover;
   initial skill connection remains Phase 6 work, not optional Phase 8 expansion.

## Interface and acceptance

Phase 6 owns the supported modes' meanings, context, expected result abstraction
and types, normal invocation/completion and error behavior. Exact mode names and
payloads are still design work; this plan does not claim they exist. Keep their
semantics distinct from reasoning intensity, execution placement and model effort.
Existing skill files are references to reconcile during Phase 6, not delivered
integration evidence or a prematurely frozen compatibility requirement.

Workflow keeps progression responsibility and the harness keeps provider
execution responsibility. Phase 5 retains CNCF's generic contract and resource
ownership. Existing saved requests must not silently acquire a different meaning
when Phase 6 changes the interface. Establish the accepted Phase 6 interface as
the baseline for subsequent skill development rather than adding a speculative
compatibility framework before the first connected delivery.

Phase 5 runtime validation remains meaningful actual execution evidence but
does not claim skill-based readiness. Phase 6 must demonstrate the actual issued
request -> skill result -> accepted completion -> scenario outcome, including
the selected thinking modes. Keep connected implementation/spec batches and
integrated independent review; do not restore per-helper acceptance cycles.

## Scope of this update

Updated the roadmap, Phase 2/3/4 scheduling notes, Phase 5 and its removal
checklist, Phase 6 scope/acceptance, Phase 7 use policy and Phase 8 ownership.
The earlier rollout journal is retained with an explicit superseding note.
No source, skill implementation, live Goal, stop schedule, test result or repair
history is changed. No product tests, release, commit or task resumption are
claimed. Validate document links, patch formatting and scheduling consistency.
