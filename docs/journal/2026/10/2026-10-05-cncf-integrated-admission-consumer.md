# CNCF Integrated Admission Management Consumer

Date: 2026-10-05
Status: integration direction
Producer: goldenport-cncf Phase 102
Foundation: goldenport-cncf Phase 90

sm-workflow retains CNCF Phase 90 Candidate-Admission as its internal Workflow/StateMachine admission authority and adopts Phase 102's provider-neutral Admission Management projection for external management.

This allows Textus Control Center to present sm-workflow admissions in the same Admission Inbox as GitHub Phase/Plan or BoK Pull Requests without weakening workflow authority.

Phase 90 evaluates Candidate/Requirement/Evidence/Gap/Result within Workflow semantics. sm-workflow owns software-development-specific closure/review policy. Phase 102 projects bounded external admission item/status/actions. Control Center/Slack/mobile/watch consume that projection. Dot/OpenClaw may supervise or create candidates but do not own admission state.

No GitHub-specific concept is added to sm-workflow Workflow semantics.
