---
name: bonkai
description: do all the things
argument-hint: <task description>
disable-model-invocation: true
---

Act as coordinator on my behalf. Use the delegation workflows to build out the work
Fan out work over worktrees as you see fit, folding in as you go.
Ensuring all know to NEVER restart the mill or weaver. they MUST do any testing locally with local weaver workspaces

act proactively without me and sign off on work. delegate as much as possible to save your context, opus and pi-main should be able to handle nearly all the work

For larger chunks you can delegate to sub supervisors to own and manage their own delegations (pi-main is a good coordinator) and act as supervisors of their own trees

Ensure no activity is awaited for more than 50 minutes by any supervisor (including yourself) to avoid missing cache timeouts

$ARGUMENTS
