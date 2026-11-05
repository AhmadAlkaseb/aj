# Performance workload and interpretation

`python scripts/performance.py` creates an isolated 10,000-car Mongo database and a
Product View container on a separate local port. Twenty concurrent clients make 200
real GraphQL requests, each returning a bounded page of 24 cars. Warmup runs first.
The container and only the named performance database are removed in finally.
No demonstration records are overwritten.

The two-second target is evaluated using p95, not an average. Results are stored in
test-results/performance.json and included in the report whether or not the target is met.
A first shared-workload run measured p95 approximately 3.78 seconds and did not meet the
target. The context included other local verification work; it is not a production SLA.
Further runs must retain their workload/hardware context rather than replacing an unfavourable
result with an unsupported speed claim.

Public GraphQL pagination is limited to 1..100 and bounded offsets. Filters are executed
by the owner, search input is bounded/escaped, and query depth/complexity are capped.
The REST administrative collection interfaces remain deliberately simple; they are not
presented as a proven large-data query API. Availability must be measured over a defined
observation window, not inferred from one successful restart or this latency benchmark.

A second run on 5 November, after stopping the separate Kubernetes/monitoring demos and restoring the normal 16-container Compose stack, measured **median 0.616s, p95 1.139s, maximum 1.263s** for the same 10,000-car / 20-client / 200-request workload. This direct-backend run met the two-second p95 target. The first failing run is retained in [performance-first-run.json](evidence/performance-first-run.json); the repeat is [performance-repeat-run.json](evidence/performance-repeat-run.json). This demonstrates sensitivity to shared workstation load, not a guaranteed end-to-end latency or production SLA.
