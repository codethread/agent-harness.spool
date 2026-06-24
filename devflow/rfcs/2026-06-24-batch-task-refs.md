# Batch task refs

**Document ID:** `RFC-001`
**Status:** Open
**Date:** 2026-06-24
**Related:** [Task Model](../specs/task-model.md), [CLI Surface](../specs/cli.md), [DB-owned task IDs feature](../archive/26-06-24__db-owned-task-ids/proposal.md)
**Configuration identification:** `RFC-001` is the first RFC in this repository. Every nested point ID is prefixed with `RFC-001`.

## RFC-001.P1 Problem

Once task ids are database-owned, users cannot know durable ids before creation. That is fine for sequential scripts that create a task, capture the returned id, and then link later work. It is awkward for batch creation of a small DAG where tasks need to reference each other before any database-owned ids exist.

## RFC-001.P2 Goals

- **RFC-001.G1:** Allow batch task creation to express intra-batch dependencies before durable ids exist.
- **RFC-001.G2:** Keep database-owned ids as the only durable task identifiers.
- **RFC-001.G3:** Let users write readable batch input with local aliases that are resolved during import.

## RFC-001.P3 Non-goals

- **RFC-001.NG1:** This RFC does not decide the DB-owned id generation algorithm; that belongs to the active id feature.
- **RFC-001.NG2:** This RFC does not add global user-controlled aliases or alternate durable identifiers.
- **RFC-001.NG3:** This RFC does not define a complete import/export format beyond task creation and dependency edges.

## RFC-001.P4 Options

| ID | Summary | Pros | Cons |
| -- | ------- | ---- | ---- |
| RFC-001.O1 | Require strictly sequential creation and linking | No new batch format | Verbose for DAG setup; callers must manually capture ids between steps |
| RFC-001.O2 | Batch EDN shapes with temporary `:ref` aliases | Readable DAG input; refs disappear after creation; fits Clojure tooling | Requires parser and validation for duplicate/missing refs |
| RFC-001.O3 | Let callers provide durable ids in batch mode | Simple dependency references | Reintroduces id collision and overwrite risks the id feature is fixing |

## RFC-001.P5 Recommendation

- **RFC-001.REC1:** Prefer `RFC-001.O2`: support batch EDN task shapes where an optional `:ref` field is only a batch-local alias.
- **RFC-001.REC2:** Resolve dependency declarations against either batch-local refs or already-known durable ids, then replace refs with database-owned ids in all persisted records and command output.

## RFC-001.P6 Consequences

- **RFC-001.C1:** Batch creation should fail loudly on duplicate refs, missing refs, malformed dependency declarations, or any task creation failure.
- **RFC-001.C2:** The task model should remain centered on database-owned durable ids; `:ref` should not become a stored task field unless a later feature explicitly adds alias metadata.
- **RFC-001.C3:** Follow-up feature planning should define the EDN shape, transaction boundary, output shape, and validation behavior.

## RFC-001.P7 Outcome

- **RFC-001.OUT1:** Open for decision as of 2026-06-24. If accepted, create a separate batch creation feature after DB-owned task ids ship.
