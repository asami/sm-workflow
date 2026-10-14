# File Workspace DSL for Safety Sandbox

Date: 2026-10-15
Status: design input from Phase 6 real use
Related: CNCF Phase 110 Safety Sandbox

## Background

During sm-workflow Phase 6 development, AI-generated code attempted external file access through Process Execution. This exposed two separate requirements: ordinary logical resource reads and detailed development-workspace file manipulation must both remain inside CNCF-managed execution boundaries.

## Decision

Ordinary resource reads continue to use the existing CNCF Internal DSL:

- `read_resource`
- `read_resource_text`

Do not add safety-variant APIs such as `read_resource_bounded`. Read-size limits belong to CNCF runtime/execution limits and apply to the normal read operations.

For sm-workflow-style detailed file manipulation, add a dedicated CNCF File Workspace Internal DSL following the existing naming convention. Candidate operations are:

- `file_read`
- `file_read_text`
- `file_write`
- `file_write_text`
- `file_list`
- `file_exists`
- `file_mkdir`
- `file_copy`
- `file_move`
- `file_delete`

The exact minimal operation set is finalized from real Phase 6 needs rather than implemented speculatively.

## Workspace authority

File operations target a logical workspace bound through ExecutionContext/SandboxContext, not an arbitrary host absolute path.

A workspace grants coarse authority below one admitted directory root. The important boundary is the root, not a SELinux-like permission matrix for every individual path. A typical sm-workflow context may grant MANAGE/read-write authority to the assigned worktree, read-only authority to dependency worktrees, and bounded read-write authority to sm-workflow state.

Every relative path is resolved by the runtime against its bound workspace and must remain contained within that root. Attempts to escape through parent traversal, alternate paths or cross-workspace source/destination operations are denied. Copy/move validate both source and destination authorities.

## Resource limits

Authority and resource limits are separate concerns.

- Sandbox/workspace authority decides which logical tree may be accessed and with what coarse authority.
- CNCF execution/runtime limits decide how much data may be read, including a maximum single-resource/file read size.

Applications and AI workers use the ordinary read DSL and do not select a special safe API.

## Process boundary

Process Execution is not a substitute for the File DSL. Using cat/cp/mv/rm or similar external commands to reproduce file operations is Capability Laundering and is not an accepted workaround.

Git and SBT use their planned CNCF typed execution DSLs. Other processes remain explicit opt-in.

## Phase 6 use

Use the real Phase 6 workflow as the development driver. Implement the minimum CNCF File Workspace DSL and workspace authority needed for actual source/worktree editing, then verify that the workflow can perform its required edits while unrelated directories and dependency worktrees without write authority remain protected.
