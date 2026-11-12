# CI-02 — accepted assumption and verification status

## Local CI redesign and GUI verification · 2026-11-12

The automatic entry point is now `ci.yaml`: a change plan selects service, quality,
security, documentation and system tracks, with an explicit dependency graph and
one strict `Ready to merge` result. Service-specific entry points are manual.
Release gates verify all required predecessors before publication or deployment.
All remote Actions are locked to upstream commit SHAs; added tooling is free/open source.

Local verification passed 47 policy/deployment/federation tests and Actionlint.
A fresh isolated Maven quality run passed 28 unit tests, 14 integration tests
and SpotBugs on 12 November. Live checks
passed all five PostgreSQL GUI logins, Mongo Express login, Grafana home links,
Headlamp node access and denial of Secret access. Monitoring checks passed six
dashboards, 35 panel queries, nine metrics targets and isolated RabbitMQ delivery.
The local stack check passed all 24 running Compose containers and shared DB logins.
Generated screenshots and JSON results are under ignored `test-results/gui-hub`.

These are local results. The redesigned workflows have not been pushed or observed
running on GitHub; branch protection and remote publication/deployment are still
subject to the configuration and verification boundaries below.

## Earlier remote acceptance boundary

The user has chosen to assume the configured CI/release flow works and defer further CI-02 execution. This is a scope decision, not a successful remote verification result.

Implemented locally: staged per-service workflows, independent quality/system gates, CodeQL, scanned Buildx images with SBOM/provenance, revision-bound release manifests, staging/production promotion, bounded migration Jobs, authenticated HTTPS/business smoke and compatible application rollback. [CI-CD.md](CI-CD.md) lists the required environment/runner/cluster configuration. Local Java, contract and workflow-syntax results are recorded in [EVIDENCE.md](EVIDENCE.md). The repository is public.

Not observed in this environment: successful GitHub run IDs for the final revision, required-check/PR-review settings, publication of nine registry digests, or an executed digest-based rollout and compatible rollback. No noninteractive GitHub credential was available when access was checked. These outcomes have not been invented or inferred from local passes.

If remote acceptance is required later, commit/push with real authorship, run the workflows for that revision, retain the actual reports/run IDs, configure branch rules, publish through the gated release and execute the documented compatible rollout/rollback. Procedures: [OPERATIONS.md](OPERATIONS.md), [release workflow](../.github/workflows/release.yaml), [release planner](../scripts/release.py).

The expanded pipeline logic passed 12 local deployment-boundary tests and Actionlint. The new smoke implementation also passed read-only and business modes through a temporary trusted local TLS proxy to the real Compose services. This was not a registry publication, Kubernetes rollout or GitHub-hosted run; those boundaries remain unverified.
