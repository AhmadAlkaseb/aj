# Local operation, backup and retention

Use `scripts/start.ps1` for normal startup. It starts the root stack and idempotently
configures local registration and verification identities without deleting existing users.
With raw Docker commands, run `python scripts/configure_identity.py` after provider readiness.
Stop with `docker compose stop` or `down` without -v to retain data.

`python scripts/backup_restore.py --verify` writes owner PostgreSQL custom-format dumps
and a MongoDB archive under ignored backups/<timestamp>. Checksums are recorded in a
manifest. Restore verification creates separately named databases, checks records and
migration history, then removes only those test databases. Never restore over the live
exam stores as a test. Backups may contain personal data and must not be committed.
These are per-database backups, not an atomic distributed snapshot. Quiesce writes for a
coordinated recovery point and reconcile queued workflows after recovery.

RabbitMQ message-store backup requires a stopped broker and its original node name.
Preserve both definitions and message data, retain the original node name/cookie, and
restore into an isolated node before switching any application connection. Existing local
node identity is retained in ignored .env; fresh installations use a stable broker hostname.
Named volumes alone are neither backup nor HA. See [RabbitMQ backup](https://www.rabbitmq.com/docs/backup).

An operator can replay one valid dead letter with:

```powershell
docker compose run --rm --no-deps --entrypoint java product-synchronizer -cp /app/app.jar com.aj.common.ReplayDeadLetter EVENT_UUID "Owner connection restored"
```

The tool verifies identity/envelope, waits for publisher confirmation, acknowledges only
the selected original, requeues unrelated messages, and emits an audit record. It refuses
an invalid schema; correct a producer/contract problem before considering replay. Do not
blindly purge the queue. Review REVIEW_REQUIRED orders against the Payments owner's stored
result; record COMPLETED/FAILED only after verification. No timeout proves financial failure.

Exam retention: keep profiles/deletion markers, order/payment state audit, command keys,
inbox entries, reservation signals and projection tombstones. Outbox entries are removed
only after broker confirmation. Logs retain seven days; backup retention is an operator
choice and no automatic database-history deletion runs. This is a local demonstration
policy, not a claim of legal compliance or a production retention schedule.

The cloud plan uses separate stateful stores, tested backups and explicit RPO/RTO targets.
A single local database/broker is not HA. Production rollout requires private endpoints,
TLS trust, restricted secret access and independently managed credentials rather than the
published examination users and local development defaults.

## Release and rollback procedure

The expanded workflows separate service tests, static analysis, contracts, system cooperation, image builds/scans and environment deployment. [CI-CD.md](CI-CD.md) describes target bootstrap, runner labels, GitHub environment variables/secrets, approval configuration, registry access and the exact release/rollback controls.

`release.yaml` requires all nine service pipelines, quality, system and CodeQL checks before optional publication. Buildx emits SBOM/provenance; Trivy gates promotion on fixable HIGH/CRITICAL findings. One artifact records nine image digests for one source revision. Staging verifies those images before production promotes the same artifact.

For manual preparation use `python scripts/release.py path/to/release.json`. On an existing target, `--context CONTEXT --namespace aj --migrate-only --output-dir test-results/deploy` runs four bounded owner migration Jobs. Then `--apply --migration-proof test-results/deploy/migration-results.json --output-dir test-results/deploy` updates images while preserving the target's live configuration. Finish with `scripts/deployment_smoke.py` using that environment's HTTPS URLs/CA and confidential smoke client. Application readiness alone does not complete acceptance.

A migration failure stops before application changes. The workflow captures a previous complete digest release, retains scoped failure evidence and can restore compatible application digests if the environment's explicit compatibility policy enables that behaviour. The separate rollback workflow selects an earlier successful release artifact and requires compatibility confirmation. Database migrations remain forward-only; no pipeline restores a backup or silently reverses a schema. Stateful event workers stay one replica with Recreate, so a deployment may temporarily interrupt those services.

`python scripts/check_broker_persistence.py` creates one private durable queue/message, recreates RabbitMQ with the existing volume, verifies preserved node identity and delivery, and removes only its probe queue. The local run passed; no live application queues were purged.
