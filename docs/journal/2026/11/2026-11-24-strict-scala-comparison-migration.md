# Strict Scala comparison migration and deterministic repair

Decision: 2026-11-24 (Asia/Tokyo)

A Phase 28.1 FixCheck sample at 15:53 JST emitted 579 distinct rule/location
findings, including 419 comparison-related AnalysisUnavailable findings.
505 findings were outside added or changed lines in that run. These are
observations of diagnostic availability and selected scope, not 419 confirmed
comparison defects or 505 automatic rewrite targets.

The user chose a separate [Phase 28.2](../../../phase/phase-28.2.md) rather
than expanding Phase 28.1. Reuse the existing Goldenport Core Vector
containsEq API and Phase 8.1 qualification work, add indexOfEq for evidenced
need, and connect only type-resolved and behavior-preserving edits to the
existing deterministic repair path. Treat strictEquality as a deliberate
Scala 3 file/module migration. Preserve explicit value, identity and array
semantics; unresolved analysis is not permission to rewrite.

This is planning only. Phase 28.1's active repair and acceptance scope,
Phase 29 lint-policy ownership, and the adopted execution order are unchanged.
