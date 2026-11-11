# Phase development permissions and scoped dependency artifacts

Adopted direction: 2026-11-10 (Asia/Tokyo).
Status: planned; no public input, installed implementation or live adoption claimed.
Owner: [purpose-based authorization correction](../phase/purpose-based-authorization-correction.md).
Consumer: [Phase 7](../phase/phase-7.md#phase-development-permissions-and-scoped-jars--2026-11-10).

## Managed dependency projects and JAR scope

PDP-01. A dependency project incorporated into Phase 7's managed development
scope is an ordinary development target. Its necessary repair needs neither an
additional dependency-repair Human decision nor enabling external repair. Establish
membership through the admitted project/dependency graph, development ownership
and Phase purpose, rather than checkout availability or directory names. Preserve
explicit exclusions and canonical owners; reuse the existing workspace model.

PDP-02. Phase 7 dependency JARs are published and resolved only inside the selected
development scope, using the existing development artifact repository, consumer
binding and overlay/selection. Do not modify ordinary shared Ivy/Maven publication
or implicitly change unrelated consumer resolution. If the SBT task is called
`publishLocal`, its actual destination must still be scope-private. Retain source
commit, publication, selected artifact and owning scope. Leaving the scope restores
ordinary resolution without rewriting/deleting shared caches. This supersedes the
2026-10-26 ordinary-publishLocal shortcut for Phase 7 delivery.

A shared installed CLI remains a host effect even when its producer project and
JAR are managed inside the Phase. JAR isolation does not authorize its replacement.

## Independent Phase properties

PDP-03. Declare two independent booleans, both defaulting to false when omitted:

| Logical property | False or omitted | True |
| --- | --- | --- |
| `allowDependencyRepair` | A necessary repair outside managed development scope requires Human direction | Admit necessary external dependency repair and selected local delivery within the Phase purpose without another Human permission decision |
| `allowSharedCliUpdate` | A necessary shared CLI installation/update requires Human direction | Admit necessary shared CLI installation/update through the normal route within the Phase purpose without another Human permission decision |

These are target logical names; establish CML/public input and projection together
during implementation. Callers must not invent these fields in current runtime
requests. Historical omission means false for this policy; it does not revoke
separately retained actual authorization for concrete work/effects.

PDP-04. Initial true in admitted Phase configuration supplies Phase-level
direction. Otherwise, when work needs a disabled operation, issue a normal Human
request identifying the property, reason, Phase purpose and effects, including
shared CLI impact. A genuine Human decision can enable either or both for the
remainder of the same Phase. Persist effective values and attributable direction
through existing native configuration/history and public same-Session continuation.
Rejection, deferral, an unanswered question or an AI proposal never enables them.

PDP-05. Reuse enabled policy across later Steps, repairs, dependency projects and
caller reconnects. Necessary files, another external dependency Component serving
the same Phase purpose, or another required local delivery/install does not itself
create a repeated Human permission gate. Runtime records concrete Components,
purpose, inventory, operations and targets under retained Phase authorization.
Reuse ordinary scope-update/delivery owners, without fabricating a Human answer
per proposal or Step. Unresolved semantic choices can still require a decision.

## Delivery and retained authority

PDP-06. External repair covers necessary source changes, affected validation,
required independent review, local commit and selected local artifact delivery
through the configured owner. Phase 7 always uses PDP-02's scoped JARs. Shared CLI
permission covers the selected artifact/version, normal installation/update and
applicable read-only activation checks. Neither implies the other: external
launcher repair plus shared installation needs both; an incorporated producer
needs no external-repair decision but shared installation still needs CLI
permission. A scope-local launcher is not a shared CLI update.

Keep Phase purpose, acceptance, explicit restrictions, actual identities and
effect targets. These properties do not authorize unrelated work, remote
publication or push. Preserve fixed version/component-loading semantics and
platform execution permissions without another product permission question for
enabled in-purpose work. No fallback execution route is introduced.

PDP-07. Phase policy is an authorization source for the existing admission owner,
not another approval ledger or engine. Reconcile it with actual concrete grants
and exclusions. Managed membership, policy, execution permissions, validation,
review, publication, installation and consumer acceptance retain distinct actual
results. Missing configuration/support is reported as such, not as missing consent.

PDP-08. Public adoption retains Goal/Session, Plan, accepted Steps, pending
obligations, source/base, approvals/rejections, unknown/failed outcomes, repair
counts and successful checks/effects. No re-Start, accepted Submit or successful
effect replay, private store edit or count refund. This plan neither enables a
live property nor answers an existing Human question.

## Connected qualification

PDP-09. Qualify through the actual installed public route:

- Omitted/false keep external-repair and CLI policy disabled; already managed
  dependency repair nevertheless proceeds under ordinary development ownership.
- Initial true and genuine mid-Phase false-to-true both work across Steps and
  reconnects, without repeated in-purpose permission questions.
- All four combinations are independent; a managed producer's shared install
  still observes the CLI property.
- A scoped replacement JAR changes only its selected consumer. An unrelated
  consumer retains its artifact and shared local repositories remain unchanged,
  including same-coordinate SNAPSHOT replacement and scope exit.
- Normal dispatch, affected validation/review, commit, scoped publication,
  selected installation and consumer adoption retain actual attributable results.
- Exclusions/out-of-purpose work remain bounded; invalid input, unavailable
  support, rejected/deferred Human and uncertain effects never become consent.
- Existing Session adoption retains history/counts without replay. Phase 6.11
  Session `3585e04191dce0bc0f70e394b6745767`, Plan 2, B5, Human 3251 motivates
  the policy; it is not a fixture to operate or consent supplied by this plan.
  Its authored five-file response correction remains unvalidated until actual checks.
