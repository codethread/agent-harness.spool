# Owner-scoped live refresh release exception

This record prepares `v10`. It is not a tag or a publication instruction.

- Previous marker: annotated `v9`; immutable peeled commit `befad44de36509cf3636242d14fc39bab35d85c2`.
- Proposed marker: annotated `v10`.
- Affected roots and names: `ct.spools/agent-run`, `ct.spools/delegation`, and `ct.spools/bench`; the removed public name is `ct.spools.agent-run/install!`. Consumers declare the module with `contribute` and `reconcile` instead.
- Required Skein range: the owner-scoped live-refresh candidate from `b8be0c8` through `91bec8ac0caf1cb21bf1119d4b253d4601159ecb` (the latter is the release-preparation baseline).
- Known consumer: this Skein repository only. Its current immutable old pin remains `v9` at the peeled commit above until human approval changes it.
- Compatibility alarm: `bin/compat-alarm v9` is expected to fail at archived `ct.spools.agent-run-test` because it resolves the removed `shuttle/install!`. This is the approved lifecycle break. No unrelated failure is accepted.
- Decision: no compatibility shim. Keeping the old root entry point would preserve the retired activation path and hide a required consumer migration.

Rollback is a consumer action: retain or restore the old `v9` pin and peeled SHA. Do not move or replace the old tag.
