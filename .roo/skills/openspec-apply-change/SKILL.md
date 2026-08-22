---
name: openspec-apply-change
description: Implement tasks from an OpenSpec change. Use when the user wants to start implementing, continue implementation, or work through tasks.
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.9.0"
---

Implement tasks from an OpenSpec change.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Input**: Optionally specify a change name (e.g., `/opsx-apply add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, run `openspec list --json` to get available changes and ask the user to select one

   Always announce: "Using change: <name>" and how to override (e.g., `/opsx-apply <other>`).

2. **Check status to understand the schema**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to understand:
   - `schemaName`: The workflow being used (e.g., "spec-driven")
   - `planningHome`, `changeRoot`, and `actionContext`: planning scope and edit constraints
   - Which artifact contains the tasks (typically "tasks" for spec-driven, check status for others)

3. **Get apply instructions**

   ```bash
   openspec instructions apply --change "<name>" --json
   ```

   This returns:
   - `contextFiles`: artifact ID -> array of concrete file paths (varies by schema - could be proposal/specs/design/tasks or spec/tests/implementation/docs)
   - Progress (total, complete, remaining)
   - Task list with status
   - Dynamic instruction based on current state
   - Optional `context`: current required project instruction input from the selected root
   - Optional `operationGuidance`: current advisory guidance for apply

   **Handle states:**
   - If `state: "blocked"` (missing artifacts): show message, suggest using `/opsx-continue` (if it is not installed, run `openspec status --change "<name>" --json` to see the next artifact and `openspec instructions <artifact-id> --change "<name>" --json` for how to create it)
   - If `state: "all_done"`: congratulate, suggest archive
   - Otherwise: proceed to implementation

   Treat `context` as a required prompt-level input. Read and consider it, and
   apply relevant project facts, conventions, and constraints while implementing.
   Treat `operationGuidance` as optional additive advice. Read and consider every
   entry, and follow entries that are applicable and compatible with the built-in
   workflow.

   Keep both fields separate from CLI-returned state, missing artifacts, tasks,
   progress, `contextFiles`, and the built-in `instruction`. They are not
   evidence of task completion, do not replace the built-in instruction, and do
   not permit bypassing a blocked state. If context conflicts with the built-in
   instruction, an explicit user choice, or a CLI-controlled value, report the
   conflict and preserve the controlling value. If guidance is inapplicable or
   conflicts with those controlling inputs, do not follow it and explain why.
   These are prompt-level behavior contracts, not enforceable checks.

4. **Read context files**

   Read every file path listed under `contextFiles` from the apply instructions output.
   The files depend on the schema being used:
   - **spec-driven**: proposal, specs, design, tasks
   - Other schemas: follow the contextFiles from CLI output

   Do not copy `context` or `operationGuidance` verbatim into implementation
   files or planning artifacts unless the user separately asks for that content.

5. **Show current progress**

   Display:
   - Schema being used
   - Progress: "N/M tasks complete"
   - Remaining tasks overview
   - Dynamic instruction from CLI

6. **Implement exactly one rolling batch, then stop at its feedback gate**

   A batch is the smallest coherent change that can be reviewed, reverted, and validated independently. Determine batch boundaries from the semantic grouping in the tasks artifact, not from each checkbox or decimal task number:
   - A numbered section/heading that groups implementation, automated tests, submission, and one CI/device gate normally defines one batch.
   - Child checkboxes such as `1.1`-`1.4` are steps of that batch, not mandatory pause points. Complete all locally actionable implementation and test steps in the group, then submit/trigger the group's validation and stop awaiting its result.
   - Do not stop after `1.1` merely because `1.2` is a separate checkbox when both are required to produce the same reviewable and testable change. A push or Action run should contain the complete coherent change plus its tests.
   - If one section actually contains multiple independently reviewable and verifiable behavior changes, split the section in the tasks artifact before coding; do not silently reinterpret arbitrary individual checkboxes as batches.

   Before changing code:
   - First reconcile any CI/device result supplied for the earliest unresolved validation gate
   - If that result failed, select only the minimal repair for the current batch; do not advance to later implementation tasks
   - Otherwise select the first pending batch and all of its locally actionable child steps through the batch's validation handoff
   - If the batch has already reached an external CI/device validation gate and no result is available, do not guess or edit code; stop and report the validation handoff

   For the selected batch only:
   - Show which batch is being worked on
   - Complete its grouped implementation, regression-test, local-check, and submission/validation-trigger steps in order; do not pause between child checkboxes solely because their decimal numbers differ
   - Make only the code, spec, and task-artifact changes required for that batch
   - Run only locally available checks permitted by project context
   - Mark an implementation task complete only when its specified implementation behavior is complete
   - Mark a CI/device validation task complete only from actual reported evidence, never from local inference
   - Stop when this batch reaches its external feedback gate (or after reconciling that gate's returned result); never begin the next numbered batch in the same invocation

   **Pause if:**
   - Task is unclear → ask for clarification
   - Implementation reveals a design issue → suggest updating artifacts
   - A task needs work beyond what the spec and tasks describe, or you are tempted to drop, narrow, defer, or accept exceptions to specified behavior to make it fit → surface the added scope and ask; do not absorb it silently
   - Error or blocker encountered → report and wait for guidance
   - The batch is ready for CI/device feedback → report the handoff and stop
   - User interrupts

7. **On batch completion or pause, show status**

   Display:
   - The single semantic batch handled this session, including all child task numbers completed within it
   - Overall progress: "N/M tasks complete"
   - Files changed and local checks actually run
   - The exact next CI/device validation, expected result, and minimum evidence to return
   - Whether later implementation is gated on that evidence
   - If all done: suggest archive
   - If paused: explain why and wait for guidance

**Output During Implementation**

```
## Implementing: <change-name> (schema: <schema-name>)

Working on batch 1 (tasks 1.1-1.4): <coherent change and validation description>
[...implementation happening...]
✓ Batch ready for CI/device validation
⏸ Stopping at the feedback gate before batch 2
```

**Output On Completion**

```
## Implementation Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 7/7 tasks complete ✓

### Completed This Session
- [x] Task 1
- [x] Task 2
...

All tasks complete! You can archive this change with `/opsx-archive`.
```

**Output On Pause (Issue Encountered)**

```
## Implementation Paused

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 4/7 tasks complete

### Issue Encountered
<description of the issue>

**Options:**
1. <option 1>
2. <option 2>
3. Other approach

What would you like to do?
```

**Guardrails**
- Process exactly one smallest coherent implementation/repair batch per invocation, then stop at its feedback gate
- Treat task hierarchy semantically: a grouped range such as `1.1`-`1.4` may be one batch when it collectively represents implementation + tests + CI/device handoff; checkbox boundaries alone are not pause boundaries
- Finish all locally actionable child steps needed for the batch's reviewable change and tests before pushing or triggering its Action; do not send partial child-step changes to CI merely to pause after every checkbox
- Never accumulate a second code batch while the current batch lacks its required CI/device result
- On failed CI/device feedback, repair only the current batch before progressing
- Always read context files before starting (from the apply instructions output)
- If task is ambiguous, pause and ask before implementing
- If implementation reveals issues, pause and suggest artifact updates
- Keep code changes minimal and scoped to each task
- Update an implementation checkbox immediately after its behavior is complete, but keep external validation checkboxes open until actual evidence is reported
- Pause on errors, blockers, or unclear requirements - don't guess
- When a task needs work beyond what the spec describes, surface the added scope and pause - never silently narrow, defer, or simplify away specified behavior
- Only mark a task `- [x]` when its specified behavior is fully implemented, not when it is partially done or deferred
- Use contextFiles from CLI output, don't assume specific file names
- Do not use context or operation guidance as proof that a task is complete
- Apply relevant project context; report conflicts with controlling workflow inputs
- Consider every guidance entry; explain any inapplicable or conflicting advice
- Do not copy runtime context or operation guidance into implementation files or planning artifacts
- Preserve CLI-controlled blocked/ready/all-done behavior and completion criteria

**Fluid Workflow Integration**

This skill supports the "actions on a change" model:

- **Can be invoked anytime**: Before all artifacts are done (if tasks exist), after partial implementation, interleaved with other actions
- **Allows artifact updates**: If implementation reveals design issues, suggest updating artifacts - not phase-locked, work fluidly
