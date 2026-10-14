# Large text and streaming extensions for File Workspace DSL

Date: 2026-10-15
Status: design note / later extension
Related: File Workspace DSL for Safety Sandbox, CNCF Phase 110

## Context

The normal full-read DSL should remain simple:

- `file_read`
- `file_read_text`
- existing logical-resource `read_resource` / `read_resource_text`

CNCF Runtime applies a default full-read size limit; the current candidate default is **1 MiB (1,048,576 bytes)**. Applications do not select a separate bounded/safe API.

Large text files, especially build/test/application logs, need a different semantic operation rather than weakening the normal full-read limit.

## Partial text operations

Candidate large-text inspection DSL:

- `file_read_head`
- `file_read_tail`
- `file_read_lines`
- `file_search_text`

These operations return only the requested bounded region. Runtime limits still cap bytes/lines regardless of a larger caller request. Implementations must not read the entire large file into memory and then slice it merely to satisfy a partial-read operation.

A typical AI workflow is tail -> identify an error/key -> search -> read surrounding lines. This reduces both memory use and LLM context/token consumption.

## Streaming extension

A later extension may provide line-oriented streaming:

- `file_stream_lines`
- `file_stream_line_chunks`

`file_stream_lines` exposes a line stream. `file_stream_line_chunks` exposes bounded windows of lines, for example 200-line chunks, so deterministic analyzers or AI workers can inspect a large log incrementally and stop when sufficient evidence is found.

The requested window size is not itself authority. CNCF Runtime/ExecutionLimits defines maximum lines/bytes per chunk and any applicable total-stream limits.

## Runtime responsibilities

Streaming remains under the same File Workspace/Safety Sandbox authority as ordinary file reads. SandboxContext decides whether the workspace/file is readable; ExecutionLimits decides how much data can be returned at once or over the stream.

A real streaming implementation must own resource lifetime, close/cancel behavior and bounded buffering/backpressure. Do not simulate streaming by reading the whole file first.

## Delivery boundary

Do not expand the current sm-workflow Phase 6 correction into a full streaming subsystem. Phase 6 should implement the minimum bounded full/partial read and file operations required by real workflow use. Preserve the DSL extension point and develop line/chunk streaming later when a concrete large-log workflow requires it.
