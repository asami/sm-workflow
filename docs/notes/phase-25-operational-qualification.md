# Phase 25 operational qualification

This record describes retained observations for the feature release boundary.
The two OpenAI examples used real provider calls and ordinary native Admission
in isolated consumer targets; they are not claims about routine production
traffic. Fixtures used for transport, allowance and failure cases are controlled
simulations. T3 checks and review, Phase validation and independent review,
release and formal local closure remain pending.

## Connected observations

| Route | Actual observation | Boundary and retained evidence |
| --- | --- | --- |
| Supplied-input review | Isolated Session `135031f0095032b5ca6e4d2b224f3439`, `ordered-review:13@14`, selected OpenAI GPT-5.6 Terra. The authorized retry reported 3,509 tokens and was admitted in the same Session with the original candidate and Compile evidence. | An earlier response was rejected with HTTP 409 for an out-of-scope Phase condition; its 3,509 tokens and daily reservation remain recorded. The one authorized retry used a verified promotional-credit reservation after the daily reservation was unavailable for the bounded request. `147-live-demonstrations-approved-release-evidence.md`, `142-openai-corrected-review-evidence.json`, `143-openai-corrected-review-admission.json`. |
| Compiler repair | Isolated Session `8d2ebc4e164eb2675aa3a8b1c1202175` used a real failed Compile on a synthetic quoted integer. GPT-5.6 Terra returned a bounded edit with 415 observed tokens. The runtime scope-checked and applied it, Compile then succeeded, and independent review was admitted. | `119-openai-live-compiler.json`, `120-isolated-openai-compiler-receipt-observation.json`, `124-openai-compiler-independent-admission.json`. The accepted repair was not repeated. |
| Additional provider | Isolated Session `a718bef5e41eab1900e09169437fba0c` returned a completed bounded review response from Ollama `gpt-oss:20b`, reporting 3,288 tokens. | This response was eligible for separate Admission; the observation does not establish Admission. A separate older Ollama 500 remains uncertain and was not replayed. `177-secondary-provider-qualification-index.md`. |

These named records are retained in the Phase task's `daily-margin-adoption`
evidence directory under `target/sm-workflow/runtime-phase-25-20261123`.
They identify the provider receipts and native outcomes without reproducing
provider content or credentials here. Observed tokens are provider-reported
usage, not settled billing. A reservation records its verified basis and bound,
not the invoice or an exact remaining credit balance.

The T2 producer delivery retained Compile, 158 passing tests, independent
re-review and phase-private publication for commit `164f348a990ec1b2aac80daf63501d07b5ee410e`;
the existing requester adopted its selected private artifact. The consumer
T2 candidate subsequently passed native Compile, 26 tests and fix-check;
independent review was admitted and its local Step closure recorded commit
`9497ac03f88e3be4f5484c0026080834b2677a72`. The evidence includes
`66-consumer-admission-recovery-proposal.md`,
`184-live-t2-lint-guard-validation.json`,
`194-live-t2-review-corrected-submit.json` and
`197-live-t2-local-closure-advance.json`. These records do not substitute for
T3 or Phase acceptance.

## Operation and limits

Follow [provider routing](../spec/phase-9-provider-routing.md) to configure the
binding, explicit capabilities, caller mapping, HTTP grant and verified allowance
authority. An eligible bounded task prefers OpenAI only after a full reservation;
credential availability alone does not establish quota. The daily allocation is
checked first against a shared reservation ledger with a configurable 1–99%
margin (default 10%). Prior reservations remain counted when the margin changes.
A verified monthly promotional-credit reservation may be used only when it covers
the full potential charge. Out-of-ledger traffic can still produce paid overage;
the operator accepts that residual risk under the Phase plan. Refresh allocation
and verify its validity for the dispatch window, including UTC-day boundaries.

For review, retain the issued work, candidate, package, check receipts and attempt
state. For compiler repair, run applicable deterministic repair first and retain
the failed candidate, diagnostic, patch admission and subsequent checks. Known
pre-dispatch ineligibility, refusal or failure follows the documented eligible
fallback; a potentially dispatched or unbound outcome remains uncertain and must
not be blindly replayed. Provider completion is a proposal or review result;
sm-workflow owns file application, checks and Admission. Provider execution does
not grant shell, Git or commit authority. Unsupported context, tools, providers,
unknown allowance and unknown costs remain explicit.

The production allowance connection and the two live cases belong to the feature
release boundary. Wider routine-use coverage, reconciliation against actual
billing and margin tuning continue alongside Phase 18 after release.
