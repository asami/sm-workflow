# Authentication provenance bounded repair

Same active Phase 2, direct user direction: "CNCFの認証由来管理と対応specも修正して".
The live goal is active again. Previous answer turn recorded direction only.
Retain parent-direct limited recovery; no subagent, commit or publication.
The local planning demand is bounded-settled and the parent profile is not
directly observable: PROCEED_UNVERIFIED was disclosed before this freeze.
This does not complete the whole Phase PLAN or admit original bases at a commit
gate. Existing authority/base and repair evidence is not reconstructed.

Freeze IMPLEMENT-AUTH-ORIGIN-1 to the CNCF root
`/Users/asami/src/dev2025/cloud-native-component-framework`, owning only:

- src/main/scala/org/goldenport/cncf/security/SecuritySubject.scala (provenance group/from propagation only)
- src/main/scala/org/goldenport/cncf/security/SecurityAuthenticationProvenance.scala (new internal registry)
- src/main/scala/org/goldenport/cncf/security/IngressSecurityResolver.scala (trusted-local marking only)
- src/test/scala/org/goldenport/cncf/security/SecurityAuthenticationProvenanceSpec.scala (new)
- src/test/scala/org/goldenport/cncf/security/IngressSecurityResolverSpec.scala (local/controlled origin assertions only)

No existing dirty delta was observed in these exact paths before freezing.
Preserve all unrelated changes. Own this journal in sm-workflow. No Cozy or
sbt-cozy source change is needed for this security prerequisite.

Remove equality-keyed WeakHashMap admission. Use synchronized weak references
and reference identity, with collected entries pruned on access. No management
hashes, identity-derived IDs or persisted control tokens. Internal closed
origins distinguish provider from trusted configured-local ingress; no public
constructor/attribute can mark them. Preserve isProviderAuthenticated and add
isTrustedLocalAuthenticated as a distinct read-only predicate. Subject
derivation and framework role expansion explicitly inherit admitted origin;
ordinary equal copies do not. The trusted-local resolver marks only its
host-configured branch, never fallback privilege or controlled-test ingress.
Authentication still does not prove explicit human consent or write permission.

Consumer map: OperationAuthorization retains provider-only behavior; current
provider ingress remains marked; fixed CLI can inspect its distinct origin;
role expansion preserves only an admitted source. No registration Operation
is exposed or changed here. Existing SecuritySubject public constructor and
wire fields remain unchanged. Registry extraction avoids expanding that file.

Executable authoring gate: new AnyWordSpec/Matchers/GivenWhenThen suite; every
new scenario has adjacent setup/action/expectation clauses. Prove distinct
equal SecurityContext/Subject copies denied, explicit role derivation admitted,
local origin not provider, public forged attributes unmarked, and a table of
independent identities retaining the same properties. Existing ingress specs
must prove real fixed-local marking and controlled-test refusal. Focused
validation: SecurityAuthenticationProvenanceSpec, SecuritySubjectSpec,
OperationAuthorizationSpec, IngressSecurityResolverSpec via the shared serial
SBT wrapper under retained parent-direct recovery. Stop on scope drift or
different semantic requirement, no edits while validation is live.

Execution owner: parent /root, model and effort not directly observable,
standard mode, no delegate (explicit retained recovery). This is a new admitted
pre-review repair, not a fabricated Phase closure repair cycle. Independent
review and full Phase validation/release remain outstanding.

## Implementation and current validation boundary

The five frozen CNCF paths were changed by the scoped escalated apply_patch
operation, which exited zero. A preliminary temporary-patch authoring call
failed format validation before creating an artifact or touching product
sources; the corrected patch was applied once. No validation followed the
failed authoring call.

SecuritySubject now delegates both origin predicates to the extracted internal
registry and inherits origin when deriving from SecurityContext. Role expansion
retains its existing explicit inherit call. The registry uses weak references,
reference `eq`, and synchronized access; pruning releases dead entries and
marking replaces only the same object's entry. It stores no attributes, hashes
or persisted identity. The trusted-local resolver marks its configured branch
only. Provider marking and provider-only OperationAuthorization were not
relaxed. Origin inspection is not an explicit-human or write grant.

The new four-scenario spec includes a four-identity table of equal copies,
explicit provider role derivation, trusted-local separation/copy rejection,
and forged public attributes. Existing ingress Given/When/Then scenarios gained
assertions for both fixed-local routes and controlled-test refusal. Source
inspection confirms clause placement; git diff --check passed. This is static
authoring evidence, not compilation or passing executable-spec evidence.

Before allocating an SBT attempt, the parent read the current serial execution
skill completely and its authoritative typed-permission-control reference.
They now explicitly prohibit SBT/wrapper execution until log allocation,
command intake/result/authority and receipt consumers have been converted and
admitted. The earlier retained direct route cannot override this newly explicit
conversion stop. No SBT, wrapper, permission metadata allocator, attempt ID,
preflight shell suite or alternative Scala build route was started.

The required focused command remains selected, not executed:
`testOnly org.goldenport.cncf.security.SecurityAuthenticationProvenanceSpec org.goldenport.cncf.security.SecuritySubjectSpec org.goldenport.cncf.security.OperationAuthorizationSpec org.goldenport.cncf.security.IngressSecurityResolverSpec`.
Resume it only through the admitted converted serialized execution route.
The changes are uncompiled and untested; no prior test count proves this delta.
No publication, acceptance commit, Phase completion or independent review is
claimed. Product registration/participant binding remains pending.

## Subsequent read-only ingress continuity check

The next continuation re-read the serial skill's current execution gate: it
still explicitly prohibits SBT and the wrapper pending consumer conversion.
No execution or new attempt was made. Two malformed diagnostic sed addresses
failed before reading content; corrected reads succeeded. Those shell errors
were not build failures or evidence about product behavior.

Inspect the affected normal path rather than assuming that replacing equality
with identity preserves ingress: IngressSecurityResolver._resolve_with_security
passes the marked SecurityContext to ExecutionContext.withSecurityContext.
That method copies the containing core with `security = security`, preserving
the exact marked security reference. Its runtime/UoW rebinding copies enclosing
execution/runtime structures, not the security product. Formatting and command
mode adjustments similarly leave security untouched. Subject derivation and
role expansion explicitly inherit the registry origin. Thus the inspected
normal path has no equal-security-copy step requiring an additional exception.
This source-level trace supports the current repair strategy but does not prove
runtime dispatch, compilation or spec success. No production changes were made
on this continuation; whitespace validation remains clean.

The next required action remains the selected four-suite focused validation
after the converted SBT route is admitted. Do not treat this static inspection
as a substitute, broaden into untested registration transport, or modify shared
skills merely to bypass their explicit execution stop.

## User-directed execution-gate exception

The direct user instruction "回避して" follows the explicit report that the
serial skill's conversion stop prevents the four-suite validation. Admit it
as a bounded exception to that skill-only stop for this required validation,
not permission to evade sandbox review, the shared SBT lock, typed permission
validation or management-hash removal. Retain parent-direct recovery. Do not
edit shared skills, invoke unconverted command-intake/receipt helpers, run SBT
without the serial wrapper, weaken tests, commit or publish.

Inspected current wrapper: permission metadata v3 and UUID allocation v2 are
now consumed directly with their typed references, without old allocation hash
inputs. Use those installed converted boundaries and the existing shared lock,
normal caches, fixed JVM profile and full log/summary terminal handshake. The
parent directly selects the exact already-frozen command under the source user
instruction; this exception does not claim the whole command/receipt migration
has been completed or independently admitted for general use.

## Actual verification under the bounded exception

Invocation 525 started through the real shared wrapper and passed typed
permission/allocation checks. It ended before tests because the project-owned
Cozy development classpath was stale, not because of a platform refusal or
authentication repair failure. Both exits were one and lock released; full
summary read at
`/tmp/skill.cncf.d/a-3ac91538-3c2d-4e55-a6d9-ac4275e84c1e/96028-20261001T052609Z.summary.json`.

Dependency-only invocation 526 ran `cozyExportRuntimeClasspath` in the already
admitted validation-only Cozy root. It compiled current existing Cozy sources
into target and refreshed its development classpath without editing source.
Both exits zero, lock released; full summary read at
`/tmp/skill.cncf.d/a-31fd53a5-b5b3-4e56-aee8-ca9b4eee8cf7/96696-20261001T052652Z.summary.json`.

Fresh invocation `SMWF-AUTH-PROVENANCE-DIRECT-20261001-527` repeated the exact
selected four-suite command after the prerequisite terminated. Compilation
succeeded and all 67 tests in four suites passed, with zero aborted, failed,
canceled, ignored or pending cases. Both exits zero and lock released; full
summary read at
`/tmp/skill.cncf.d/a-5e6b7071-7055-4771-86fd-fce9f27d893f/97319-20261001T052734Z.summary.json`.

New provenance scenarios passed together with SecuritySubject,
OperationAuthorization and real IngressSecurityResolver scenarios/properties.
The original provider-only rule remained covered. Shared locking, typed v3
permission, typed v2 allocation, normal caches and the terminal evidence
handshake were preserved. No unconverted intake/receipt helper or old
management hash control was executed. No sources changed while SBT was live;
post-run git diff --check passed. All three invocation identities were appended
to the retained consumed-ID ledger; no live session remains.

This is focused compile/behavior evidence for the authentication delta, not
independent review, general execution-route migration admission, full Phase
validation, registration transport evidence or a release. No commit, push or
publication occurred. Next connect admitted provider/local subject identity to
the exact participant contract and new registration reader/Operation, retaining
the existing managed pool and Create-only committed-readback constraints.
