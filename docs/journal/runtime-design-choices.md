# Runtime and public-interface design choices

The linked design/notes own the corresponding proposals and behavior. These
paragraphs retain why particular alternatives were rejected, not their edit or
verification history.

## Dependency source is not an installed image

Requiring an installation UUID or manifest for every source revision would
couple ordinary dependency development to deployment state. The
[source-revision design](../design/goal-phase-dependency-source-revision.md)
therefore separates source/base/scope succession from fixed runtime images.
Inventing placeholder image IDs or reviving a retired API would preserve the
coupling under another name.

## The installed command follows normal publication

A workstation script loading arbitrary development classes makes unfinished
edits affect operational use. The selected CNCF launcher and published artifact
are the intended boundary. Pinning one SNAPSHOT number would then undermine
routine upgrades: catalog version comparison must consider both releases and
SNAPSHOTs, with release preferred at the same version.
See the [initialization delivery notes](../notes/project-initialization-delivery-plan.md).

## Pure configuration, owned initialization

Automatic startup is useful, but a descriptor read or codec roundtrip must not
create directories. Trusted runtime initialization can act on explicitly
declared roots after admission. This avoids both hidden decoder effects and
application-side `mkdir` as a workaround for missing framework setup.

Deriving a normalized runtime parent in CNCF also avoids constructing `..`
paths in application templates. A candidate alias is an execution resource,
not another ordinary source repository. The
[directory-list contract](../spec/repository-sync-directory-list.md) distinguishes
those roles for the actual layout.

## Resource policy belongs at acquisition

Rejecting a complete valid result after an effect because sm-workflow has its
own arbitrary byte/count ceiling can make recovery necessary for an unrelated
reason. CNCF's configured acquisition/output policy is the proper boundary;
ownership and workflow scope remain application concerns. Short presentation
is useful when full detail is still available through an actual reference.
See the [response inventory](../design/phase-6.11-response-inventory.md).

## Build execution is program work

AI-supervised SBT start/wait/handoff loses deterministic recovery and spends
reasoning on idle time. A global wrapper lock would additionally serialize
independent projects. The selected design puts execution in typed CNCF UoW and
leaves required exclusion to sbt. Basic behavior should not depend on sbt-cozy
being installed in every build; plugin-specific features remain explicit.

## Local publication is not source intent

Writing a local publication time into a tracked catalog makes two machines
conflict even when they publish the same version. `publishLocal` should convey
its local meaning internally; asking the operator for an additional flag would
put plugin responsibility back on the user. Local repository updates and
source-catalog publication are consequently different operations.

## Existing typed interfaces before bespoke transport

A CML/DTO mismatch needs correction from the public declaration through its
generated consumers. A client-specific DTO, private CLI parser or control-center
special path would leave other transports inconsistent. Configuration is
resolved through CNCF/ExecutionContext; structured large input uses existing
Resource/UoW handling. The
[source-history driver notes](../design/source-history-external-provider.md)
likewise separate resolved style selection from comment formatting, allowing a
small built-in driver without inventing an unavailable external provider.
