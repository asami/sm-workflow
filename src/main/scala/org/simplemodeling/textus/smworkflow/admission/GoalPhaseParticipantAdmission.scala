package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SubjectKind}
import org.goldenport.cncf.security.SecuritySubject

/** Actor-only admission for the current call. No consent, storage or command grant.
  * Participant identity is the exact authenticated principal ID, never a role/alias.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseParticipantAdmission {
  def admitC(participantIdentity: String, context: ExecutionContext): Consequence[Unit] =
    if (context == null || context.security == null || participantIdentity == null ||
        participantIdentity.isEmpty || participantIdentity.trim != participantIdentity)
      Consequence.securityPermissionDenied("GoalPhase participant admission is incomplete")
    else {
      val subject = SecuritySubject.from(context.security)
      val kind = context.security.subjectKind
      if (subject.isAuthenticated &&
          (subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) &&
          (kind == SubjectKind.User || kind == SubjectKind.Service) &&
          subject.subjectId == participantIdentity)
        Consequence.unit
      else Consequence.securityPermissionDenied("GoalPhase participant differs from the admitted current principal")
    }
}
