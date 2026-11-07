# Commit preparation: extra blank lines at end of file

Date: 2026-11-08
Status: user-requested EOF-only planning addition; extension and installed qualification pending
Owner: sm-workflow development; shared commit-preparation owner
Allocation: [Phase 6.11 B3/B5](../../../phase/phase-6.11.md#blank-line-commit-preparation-follow-up--2026-11-08)

## Request and observed boundary

The user clarified that this follow-up concerns the end of the file only.
Remove extra terminal blank lines, including terminal lines containing only
spaces/tabs, while retaining the final newline. Do not normalize blank lines
inside the file. Supported correction must finish in ordinary preparation,
without consuming a product FIX or failed-effect recovery cycle merely for this
EOF normalization.

On November 7, the sm-workflow development chat reported an additional isolated
CLI Step-commit check stopping at
`src/test/scala/ProductCheck.scala:21: trailing whitespace.` The generating fixture
had emitted indentation on an otherwise empty line. This is not Git's
`new blank line at EOF.` finding. The record establishes a commit-precheck stop;
it does not establish that a repair/recovery counter was incremented. Keep that
distinction and do not refund or rewrite historical counters.

That reported interior-line case is not an acceptance case for EOF normalization.
Its producer correction remains separate; it does not authorize a whole-file
whitespace pass.

At this inspection, `_write_primary_product` in
`NativeGoalPrerequisitePublicationRecoveryCliSpec` already emits an empty string
when its optional document check is absent, rather than indentation alone.
Reuse that producer correction; do not repeat the earlier fixture repair or its
successful checks solely to adopt this follow-up.

`CommitHygiene.supported` still covers CML and Markdown under `docs/`, excluding
test/fixture/data locations. `corrected` removes only extra terminal LF bytes.
Consequently, the runtime preparation route does not cover that Scala finding.
Keep existing file eligibility and data exclusions; this follow-up does not add
Scala/test files to the supported set. Existing terminal-LF implementation is
reuse material, not proof of handling space/tab-only terminal lines.

## Bounded addition

1. Reuse the supported terminal-LF correction. Extend only its terminal suffix
   handling to extra LF-terminated blank lines containing spaces/tabs. Preserve
   exactly one terminal LF after the last content-bearing line, all bytes before
   that suffix, file mode and unrelated work. For example, `body\n \t\n\n`
   becomes `body\n`; `body  \n` and an interior `\n \t\n` remain unchanged.
2. Keep the existing selected-path, UTF-8/LF, regular-file and content-safety
   boundaries. Empty/all-blank files, CRLF, binary data and meaning-bearing test,
   fixture, golden or snapshot content remain outside automatic correction.
   Do not add general trailing-space removal, newline insertion, source parsing,
   indentation changes or a formatter.
3. Retain the actual typed precheck and inspect the exact selected EOF bytes.
   An interior-line or content-bearing trailing-space diagnostic is unsupported,
   even if it occurs in the same file as a terminal finding. Do not infer safety
   from the diagnostic wording alone or silently exempt an integrity finding.
4. Keep `sm-workflow.commit.hygiene.mode`: omitted/`auto-fix` corrects a supported
   candidate once, updates only its selected staging, rechecks and performs the
   ordinary single commit in the same Advance. `error` reports without correction
   or commit. Unsupported findings remain diagnostics, not an automatic loop.
5. Preserve the existing partial-staging boundary. Never stage unrelated work to
   capture the correction; retain a specific no-write limitation if the existing
   typed selection cannot safely apply it. Reuse managed File and typed Git owners,
   not a Python/shell formatter or a parallel commit executor.

The [commit hygiene contract](../../../spec/commit-completion-and-recovery.md#mechanical-commit-hygiene)
remains the source of truth for delivered behavior. Integrate the qualified
EOF-suffix extension into that contract when adopted; this journal does not
authorize interior-line normalization or claim implementation completion.

## Accounting and continuation

Supported mechanical preparation happens before commit dispatch. It must not
invoke product FIX, increment its ordinary/hard-limit counters, allocate a
failed-effect recovery attempt, or require another AuthorizeNewAttempt/Human/AI
round trip merely for normalization. Record the finding, correction and recheck
as preparation facts through the existing owner. Preserve existing counters;
neither reset nor refund past attempts.

Do not extend this exemption to semantic source changes or arbitrary minor fixes.
An actual commit failure or UNKNOWN still follows existing effect observation and
recovery. Recollection must not create a second commit. Reuse applicable tests and
review results only for an established meaning-preserving correction, retaining
their original target and correction relationship.

If the CNCF typed finding model needs additional facts, request the bounded
producer change from the existing CNCF development owner on canonical main.
Do not implement competing Git-output parsing or filesystem effects in the caller.
No such dependency change is asserted or dispatched by this planning edit.

## Connected acceptance to add to B3/B5

- Qualify extra terminal LF blank lines and space/tab-only terminal blank lines
  on existing eligible files. Retain one final LF and the exact preceding bytes.
- Omitted and explicit `auto-fix`: supported finding -> one correction -> clean
  check -> exactly one real commit -> normal continuation in the same Session,
  with unchanged product FIX and recovery counters and no extra Human/AI work.
- `error`, unsupported findings and an unsuccessful recheck: no automatic commit
  or repeated correction; retain the actual reason and counter observations.
- Interior blank lines, including the reported Scala test-source case, remain
  unchanged. Literal/data/snapshot bytes, content-line trailing spaces, empty or
  all-blank files and CRLF are not normalized. No Scala eligibility expansion.
- Preserve unrelated changes, partial staging, mode, prior results and history;
  clean input causes no rewrite and successful result recollection no duplicate
  commit. Exercise affected Step/release/RepositorySync callers of the shared owner.

Use small local fixtures and the nearest existing hygiene/closure coverage, then
one normal installed route for the connected counter/commit outcome. Do not add a
blanket FULL campaign or reopen accepted Phase 6.8 work. The Phase owner adopts
this delta at its ordinary planning boundary, preserving live P611 work, Session,
Plan history, accepted results and counters. This edit does not operate that Session.

## Scope of this edit

Only this handoff and its Phase-plan reference are added. No product or test source,
live configuration, staging, counters or Session is changed; no compile, test,
commit, publication, synchronization or cross-chat dispatch is performed.
