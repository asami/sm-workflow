# Phase 25.1 Acceptance Checklist

Status: planned
Plan: [Phase 25.1](phase-25.1.md)

- [ ] P251-01 Verify request-sized admission for ordinary code review and bounded bug fixes. Include the complete input and enforce the configured completion-token limit for each task type. Calibrate the limits with representative usage, reasoning tokens, truncation and retries. Show that several small reviews fit in a bounded allowance when the previous fixed input bound would have blocked them.
- [ ] P251-02 Verify that request-bound usage settles once and returns the unused portion to the original UTC-day allowance. Include repeated settlement, mismatched or missing usage, uncertain dispatch and an application-rejected result. Verify that concurrent callers stay within the shared limit after margin and that a restart preserves existing reservations, claims and settlement history.
- [ ] P251-03 Check whether provider-side free-only admission can refuse an over-quota request before any billed execution. Enable reservation-free dispatch only after verification at the quota boundary and with concurrent callers. Retain usage and outcome records. With no verified enforcement, confirm that ordinary review and repair use local request-sized admission and that usage-only reporting cannot bypass it.
- [ ] P251-04 Verify eligible-model restriction, pre-dispatch decline with unknown or insufficient allowance, and the separate verified promotional-credit policy. Use offline fixtures to qualify both ordinary Task API routes, and record scoped validation and independent review before Phase closure.
