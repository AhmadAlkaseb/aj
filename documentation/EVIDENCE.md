# Verification evidence

Reviewed 5 November 2026. Results below are local and refer to the recorded source fingerprint. Historical artifact timestamps are retained in [the JSON record](evidence/local-verification.json). They are not fabricated GitHub run results.

| Check | Observed result |
| --- | --- |
| Java / SpotBugs | PASS; 28 unit and 14 integration checks; 0 failures, 0 errors |
| security | PASS |
| reliability | PASS |
| monitoring | PASS |
| restore | Verified owner database restores |
| performance | 10,000 cars / 20 clients; p95 1.139s; two-second target met: True |
| kubernetes | TLS, commerce, PDF job, database isolation and HPA recorded |
| brokerPersistence | PASS |
| cooperation | PASS |
| browserWithRealAI | PASS |
| browser | PASS |
| oidcRecovery | PASS |
| demoRestart | PASS |
| systemRecovery | PASS |
| performanceFirstRun | p95 3.782s; target met: False |
| performanceRepeatRun | p95 1.139s; target met: True |

Java source/resource fingerprint: `b898ab43ecc3f143fccdde01baf504f63e56eabe416f2ba9de59e10328a07f7e`.

Local build logs and full reports stay under ignored test-results. Reproduce Java verification with `python scripts/verify_java.py --quality`; Docker-backed checks are described in [TESTING.md](TESTING.md). Source changes require a new run; one local pass is not a guarantee of future availability.

Remote CI IDs, immutable image digests and branch protection must be recorded after an actual GitHub operation. Group meeting attendance, historical contributions and first-person reflections require author confirmation.

## Expanded pipeline verification

The updated workflows passed Actionlint and Compose configuration validation. Twelve Python deployment-boundary tests passed; the new read-only/business smoke passed through a trusted local HTTPS proxy to actual Compose APIs. [Pipeline evidence](evidence/pipeline-verification.json) records a separate source fingerprint and the precise execution boundary. No new remote execution, registry scan/publication or digest-based Kubernetes rollout is implied. [CI-CD.md](CI-CD.md) documents target configuration and use.
