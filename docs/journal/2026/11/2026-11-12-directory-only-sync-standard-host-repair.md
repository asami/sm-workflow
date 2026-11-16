# Directory-only sync and candidate workspace preparation

## Design background

RepositorySync prepares a candidate separately from the source checkout so
validation observes the candidate while ongoing source work remains intact.
The packaged configuration therefore needs a candidate parent, a dormant alias,
and owner permissions for the existing typed Git/SBT operations.

CNCF admission requires real workspace roots before a Session can allocate its
candidate. Allocating a descendant of an admitted parent cannot prepare that
parent for its own admission. Declaring the policy alone therefore does not
provide first-use readiness; pre-creating directories in a fixture hides this
dependency.

## Responsibility

Preparation and admission of configured workspace resources belong to CNCF's
normal initialization boundary. sm-workflow consumes the admitted logical
resources and retains workflow-specific candidate state. A skill or application
must not provide another allocator or require manual directory creation to
complete ordinary initialization.

First-use preparation must retain containment, source separation, permission
and symlink protections. Reopening an existing installation reuses its resources
and candidate lifecycle rather than resetting Session data or repeating effects.
Connected qualification must exercise initially absent managed roots through
the owning framework, rather than arranging those roots before admission.
