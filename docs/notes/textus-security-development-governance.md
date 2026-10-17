# Textus Security Development Governance

Status: initial security architecture projection
Updated: 2026-10-14
Canonical architecture: goldenport-cncf/docs/design/textus-security-architecture.md

sm-workflow treats developer, vendor, Codex, local LLM and other AI output as
candidates that require the same workflow admission. Model identity or apparent
competence is not assurance.

Security checks participate in the existing Step / Phase / closure workflow;
they do not create a second workflow. Focused checks should run near the change,
broader architectural/security review at Phase review, and release-relevant
assurance at closure when required.

CAR lint and CBD Support Review remain specialized evidence providers.
sm-workflow owns sequencing, evidence state, escalation and human decision
boundaries, not their security rule semantics.

Security findings should prefer concrete contract violations: unintended public
surface, excessive authority, bypass of Operation/Entity/DataStore boundaries,
secret leakage, unsafe command/network/filesystem authority, injection-prone
construction, or unjustified dependency/supply-chain exposure.

A security label must not justify speculative hashes, duplicate verification,
whole-tree copies, integrity machinery or unbounded defensive checks. Such work
requires an explicit threat and acceptance contract.

Security findings and incidents that reveal a Textus-wide architectural issue
are recorded in journal and fed back to the canonical CNCF security design.
