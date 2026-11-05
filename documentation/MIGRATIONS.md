# Schema ownership and migration

Each SQL service owns its `src/main/resources/db/migration/<service>` directory.
Flyway runs before Hibernate **validate**; Hibernate update is no longer a release strategy.
`clean` is disabled. Kubernetes has a migration init container followed by application
startup/validation; a failed migration prevents readiness and rollout completion.

| Version | Meaning |
| --- | --- |
| V1 | Captured, reviewed existing service schema; also creates an empty installation |
| V2 | Transactional command keys and payment customer ownership |
| V3 | Append-only state audit; Orders-owned PDF artifacts |
| V4 | Products reservation signals; Orders expiry deadlines |
| V5 | Orders retains early payment outcomes |
| V6 | Existing live Orders receive deadlines; historical demo identities remain timeless |

Only Orders has V5/V6; versions belong to each owner, not a global database.
For an existing reviewed local schema, Compose explicitly opts into baseline version 1.
The Java default `DB_BASELINE_EXISTING=false` refuses automatic baselining elsewhere.
Take a backup, confirm the target and compare V1 before enabling that flag. Existing
volumes and records are retained. Applied migration files must never be edited.

The self-starting `python scripts/cooperation.py --exclusive` fixture verifies all
four empty PostgreSQL schemas, then drives reservation and payment compensation
without a frontend/gateway. The existing stack verifies baseline upgrades and preserved
20-record fixtures. `scripts/backup_restore.py --verify` restores schema/data/history
into separately named databases and removes only those test databases.

MongoDB documents use a product-state version independent of event schemaVersion.
Version-conditional writes and a unique GUID index protect concurrent projection updates.
Restore the owner backup including hidden deletion tombstones, then resume delivery.
For a complete rebuild, pause Synchronizer, export active snapshots **and deleted GUIDs
with their last versions** through the Products owner's operational boundary, initialise
a new Mongo database owned by Product View, replay those snapshots/tombstones through
its authenticated writer, compare counts/versions, switch MONGO_DB and resume the queue.
Keep the previous database until verification succeeds. Rebuilding only public active
cars would lose deletion fences and permit stale queued snapshots to resurrect records.
No ordinary service may query another owner's SQL/Mongo store to perform this operation.

Migration rollback is an application rollback, not automatic reversal of data changes.
The current migrations are additive. Future destructive changes require an expand/contract
release and a tested recovery path before old binaries can be used again.

See [Flyway baselines](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).
